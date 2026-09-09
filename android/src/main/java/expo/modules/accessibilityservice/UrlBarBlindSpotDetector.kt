package expo.modules.accessibilityservice

/**
 * Notices when a browser we claim to support never yields its URL bar, and says so
 * once.
 *
 * The address-bar id of a browser is a private resource name. It can be renamed in any
 * release, and it differs between an OEM's builds — Samsung ships one browser across
 * dozens of One UI versions. When the id in [AccessibilityService.BROWSER_URL_BAR_FIELD_IDS]
 * stops matching, nothing throws: the events keep arriving, no node is ever found, and
 * website blocking is simply off for that browser. Silently. On devices nobody owns.
 *
 * No unit test can catch that — a test asserting our id equals our id passes whatever
 * the real browser does — and buying every model to check is not a plan. So the field
 * reports it instead: after [minEventsBeforeReporting] content events in one browser
 * with **not one** URL bar resolved, that package is worth a look.
 *
 * The threshold is what keeps this quiet. A browser fires content events constantly,
 * including plenty that have nothing to do with the address bar, so a handful of misses
 * means nothing; dozens in a row with zero hits means the id is wrong. One report per
 * package per process — this is a signal, not a metric.
 *
 * Pure and state-injected so the rule is unit-testable without an accessibility service.
 */
internal class UrlBarBlindSpotDetector(
    private val minEventsBeforeReporting: Int = MIN_EVENTS_BEFORE_REPORTING,
) {
    companion object {
        const val MIN_EVENTS_BEFORE_REPORTING = 40
    }

    private val missesByPackage = mutableMapOf<String, Int>()
    private val reported = mutableSetOf<String>()

    /**
     * Records one content event from [packageName], where [resolved] says whether a URL
     * bar came out of it.
     *
     * @return true exactly once per package, on the event that makes the case: enough
     *   events seen, none of them ever resolved. A single resolution clears the count
     *   for good — the id works, whatever it did before.
     */
    @Synchronized
    fun onBrowserEvent(packageName: String, resolved: Boolean): Boolean {
        if (resolved) {
            missesByPackage.remove(packageName)
            reported.add(packageName)
            return false
        }
        if (packageName in reported) return false

        val misses = (missesByPackage[packageName] ?: 0) + 1
        missesByPackage[packageName] = misses
        if (misses < minEventsBeforeReporting) return false

        reported.add(packageName)
        return true
    }
}
