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
 * **The unit of evidence is a visit, not a lookup.** A browser hides its toolbar while
 * the user reads, and a hidden toolbar is invisible to a lookup by view id: Chromium
 * sets its whole control container to `View.INVISIBLE` as soon as a scroll hides any of
 * it (`BrowserControlsManager.updateVisibility`), and Android's
 * `findAccessibilityNodeInfosByViewId` keeps only views that are shown. So a long read
 * produces hundreds of misses in a row with an id that is perfectly right, which is how
 * an earlier version, counting 40 consecutive lookups, reported Chrome as broken on
 * devices where it worked. What a right id does guarantee is a hit somewhere in each
 * visit: the bar is on screen when the browser comes to the front and on every new
 * page. A stale id never hits, in any visit. So a visit counts against a browser only
 * when it made lookups and none of them found a node.
 *
 * A visit runs from one window-state change of the browser to the next. A visit that
 * made no lookup says nothing and changes nothing.
 *
 * **Only real lookups.** The caller must feed this ONLY lookups that actually happened,
 * and count as found any lookup where a node carried one of our candidate ids — even
 * holding no text. Counting refused query slots or a blank new tab would report a
 * browser that works perfectly.
 *
 * **A success resets the count; it does not settle the browser for good.** A browser
 * updating in place and renaming its id is the single most likely way this goes wrong,
 * and it always follows a period of working.
 *
 * **Reporting backs off rather than stopping.** A package that reports keeps its
 * counter running with the bar raised by [BACKOFF_FACTOR] each time. So a browser that
 * is genuinely broken says so once and then goes quiet, while nothing — not even a
 * report that turns out to be wrong — can make this deaf to a later, real failure.
 *
 * Pure and state-injected so the rule is unit-testable without an accessibility service.
 */
internal class UrlBarBlindSpotDetector(
    private val minMissedVisitsBeforeReporting: Int = MIN_MISSED_VISITS_BEFORE_REPORTING,
) {
    companion object {
        const val MIN_MISSED_VISITS_BEFORE_REPORTING = 5

        /** How much harder each further report for the same package becomes. */
        const val BACKOFF_FACTOR = 10
    }

    private enum class Visit { MISSED, FOUND }

    private val currentVisitByPackage = mutableMapOf<String, Visit>()
    private val missedVisitsByPackage = mutableMapOf<String, Int>()
    private val thresholdByPackage = mutableMapOf<String, Int>()

    /**
     * Records one lookup made during [packageName]'s current visit. [foundNode] is
     * whether a node carrying one of our candidate ids was there — not whether it held
     * a usable address.
     */
    @Synchronized
    fun onLookup(packageName: String, foundNode: Boolean) {
        if (foundNode) {
            currentVisitByPackage[packageName] = Visit.FOUND
            missedVisitsByPackage.remove(packageName)
            return
        }
        currentVisitByPackage.getOrPut(packageName) { Visit.MISSED }
    }

    /**
     * Records that [packageName] came to the front: its previous visit ends here and is
     * judged, and a new one starts.
     *
     * @return true when the visit that just ended makes the case: enough consecutive
     *   visits without a single node found, for this package's current threshold.
     *   Reporting resets the count and raises that threshold, so the next report for
     *   the same package needs far more evidence.
     */
    @Synchronized
    fun onBrowserWindowShown(packageName: String): Boolean {
        val endedVisit = currentVisitByPackage.remove(packageName)
        if (endedVisit != Visit.MISSED) return false

        val threshold = thresholdByPackage[packageName] ?: minMissedVisitsBeforeReporting
        val missedVisits = (missedVisitsByPackage[packageName] ?: 0) + 1
        if (missedVisits < threshold) {
            missedVisitsByPackage[packageName] = missedVisits
            return false
        }

        missedVisitsByPackage.remove(packageName)
        thresholdByPackage[packageName] = threshold * BACKOFF_FACTOR
        return true
    }
}
