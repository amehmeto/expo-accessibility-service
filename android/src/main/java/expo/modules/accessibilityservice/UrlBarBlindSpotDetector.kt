package expo.modules.accessibilityservice

/**
 * Notices when a browser we claim to support stops yielding its URL bar, and says so.
 *
 * The address-bar id of a browser is a private resource name. It can be renamed in any
 * release, and it differs between an OEM's builds — Samsung ships one browser across
 * dozens of One UI versions. When the id in [AccessibilityService.BROWSER_URL_BAR_FIELD_IDS]
 * stops matching, nothing throws: the events keep arriving, no node is ever found, and
 * website blocking is simply off for that browser. Silently. On devices nobody owns.
 *
 * No unit test can catch that — a test asserting our id equals our id passes whatever
 * the real browser does — and buying every model to check is not a plan. So the field
 * reports it instead.
 *
 * **Consecutive misses, and only real ones.** The caller must feed this ONLY lookups
 * that actually happened, and count as found any lookup where a node carried one of
 * our candidate ids — even holding no text. Counting refused query slots or a blank
 * new tab would report a browser that works perfectly.
 *
 * **A success resets the count; it does not settle the browser for good.** An earlier
 * version exempted a package permanently after one resolution, which got the failure
 * mode backwards: a browser updating in place and renaming its id is the single most
 * likely way this goes wrong, and it always follows a period of working.
 *
 * **Reporting backs off rather than stopping.** A package that reports keeps its
 * counter running with the bar raised by [BACKOFF_FACTOR] each time. So a browser that
 * is genuinely broken says so once and then goes quiet, while nothing — not even a
 * report that turns out to be wrong — can make this deaf to a later, real failure.
 *
 * Pure and state-injected so the rule is unit-testable without an accessibility service.
 */
internal class UrlBarBlindSpotDetector(
    private val minMissesBeforeReporting: Int = MIN_MISSES_BEFORE_REPORTING,
) {
    companion object {
        const val MIN_MISSES_BEFORE_REPORTING = 40

        /** How much harder each further report for the same package becomes. */
        const val BACKOFF_FACTOR = 10
    }

    private val missesByPackage = mutableMapOf<String, Int>()
    private val thresholdByPackage = mutableMapOf<String, Int>()

    /**
     * Records one lookup for [packageName]. [foundNode] is whether a node carrying one
     * of our candidate ids was there — not whether it held a usable address.
     *
     * @return true on the lookup that makes the case: enough consecutive misses for
     *   this package's current threshold. Reporting resets the count and raises that
     *   threshold, so the next report for the same package needs far more evidence.
     */
    @Synchronized
    fun onLookup(packageName: String, foundNode: Boolean): Boolean {
        if (foundNode) {
            missesByPackage.remove(packageName)
            return false
        }

        val threshold = thresholdByPackage[packageName] ?: minMissesBeforeReporting
        val misses = (missesByPackage[packageName] ?: 0) + 1
        if (misses < threshold) {
            missesByPackage[packageName] = misses
            return false
        }

        missesByPackage.remove(packageName)
        thresholdByPackage[packageName] = threshold * BACKOFF_FACTOR
        return true
    }
}
