package expo.modules.accessibilityservice

/**
 * Decides whether a browser content event is worth acting on, without touching
 * the accessibility tree.
 *
 * Extracted from the service and given an injectable clock so both rules are
 * unit-testable — the service itself needs a live Android runtime, these rules
 * do not.
 *
 * Two independent rules:
 *
 * - **Query pacing.** Reading the URL from the window root is the expensive
 *   fallback, and browsers fire content events per keystroke and on scroll.
 *   One full-tree query per [minQueryIntervalMs] at most. The cheap path — the
 *   changed node already being the URL bar — never goes through here.
 * - **Emission de-duplication.** The same URL in the same browser is announced
 *   once. A window change clears it, because leaving and returning to a page is
 *   a new navigation for the consumer.
 */
internal class UrlBarEmissionGate(
    private val minQueryIntervalMs: Long = DEFAULT_MIN_QUERY_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val DEFAULT_MIN_QUERY_INTERVAL_MS = 250L
    }

    private var lastQueryAtMs: Long? = null
    // A pair, not a concatenation: no separator to pick, and no way for
    // (package, url) to collide with a different (package, url).
    private var lastEmitted: Pair<String, String>? = null

    /**
     * Takes the next full-tree query slot when the pacing interval has elapsed.
     * Consumes the slot when it returns true, so callers must query only then.
     */
    fun tryAcquireQuerySlot(): Boolean {
        val now = clock()
        val last = lastQueryAtMs
        if (last != null && now - last < minQueryIntervalMs) return false
        lastQueryAtMs = now
        return true
    }

    /**
     * Claims the right to announce [text] for [packageName], unless it is the
     * value already announced. Consumes the claim when it returns true.
     */
    fun tryClaimEmission(packageName: String, text: String): Boolean {
        val claim = packageName to text
        if (claim == lastEmitted) return false
        lastEmitted = claim
        return true
    }

    /** Forgets the last announced URL, so the next navigation is announced again. */
    fun forgetLastEmission() {
        lastEmitted = null
    }
}
