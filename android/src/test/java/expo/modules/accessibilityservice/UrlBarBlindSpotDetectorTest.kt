package expo.modules.accessibilityservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that turns a silent failure into a signal: a browser we claim to support
 * whose address bar we stop finding. See [UrlBarBlindSpotDetector] for why no other
 * kind of test can catch a stale view id, and why the unit of evidence is a visit.
 */
class UrlBarBlindSpotDetectorTest {

    private val samsung = "com.sec.android.app.sbrowser"
    private val chrome = "com.android.chrome"

    private fun detector(threshold: Int = 5) =
        UrlBarBlindSpotDetector(minMissedVisitsBeforeReporting = threshold)

    /** One visit: the browser comes to the front, then the lookups made during it. */
    private fun visit(detector: UrlBarBlindSpotDetector, pkg: String, vararg lookups: Boolean): Boolean {
        val reported = detector.onBrowserWindowShown(pkg)
        lookups.forEach { detector.onLookup(pkg, foundNode = it) }
        return reported
    }

    /** [count] visits in which every lookup missed; how many of them ended in a report. */
    private fun missedVisits(detector: UrlBarBlindSpotDetector, pkg: String, count: Int): Int =
        (1..count).count { visit(detector, pkg, false, false, false) }

    /** Opens the next visit, which is what closes and judges the one before it. */
    private fun closeVisit(detector: UrlBarBlindSpotDetector, pkg: String): Boolean =
        detector.onBrowserWindowShown(pkg)

    @Test
    fun `stays quiet while fewer visits than the threshold found nothing`() {
        val detector = detector(threshold = 5)

        missedVisits(detector, samsung, 4)

        assertFalse(closeVisit(detector, samsung))
    }

    @Test
    fun `reports once enough whole visits found no URL bar`() {
        val detector = detector(threshold = 5)

        assertEquals(0, missedVisits(detector, samsung, 5))

        assertTrue(closeVisit(detector, samsung))
    }

    @Test
    fun `a browser that hides its URL bar while the user reads is not reported`() {
        // Chrome sets its whole toolbar INVISIBLE as soon as a scroll hides it, and
        // Android's lookup by view id skips views that are not shown. Every lookup made
        // while the user reads therefore misses, however right the id is. What the id
        // being right guarantees is a hit somewhere in the visit: the bar is on screen
        // when the browser opens and on every new page.
        val detector = detector(threshold = 5)
        val readingALongPage = BooleanArray(200) { false }

        repeat(50) {
            assertFalse(visit(detector, chrome, true, *readingALongPage))
        }
        assertFalse(closeVisit(detector, chrome))
    }

    @Test
    fun `a found node counts for the visit even holding no text`() {
        // Empty is the id being RIGHT with nothing to read yet — a blank new tab. The
        // caller passes foundNode = true for it, and this must not count against the
        // browser.
        val detector = detector(threshold = 1)

        visit(detector, samsung, false, true, false)

        assertFalse(closeVisit(detector, samsung))
    }

    @Test
    fun `a visit without a single lookup neither counts nor resets`() {
        val detector = detector(threshold = 2)

        missedVisits(detector, samsung, 1)
        repeat(20) { assertFalse(visit(detector, samsung)) }
        missedVisits(detector, samsung, 1)

        assertTrue(closeVisit(detector, samsung))
    }

    @Test
    fun `missed visits have to be consecutive`() {
        val detector = detector(threshold = 5)

        missedVisits(detector, samsung, 4)
        visit(detector, samsung, false, true)
        missedVisits(detector, samsung, 4)

        // The count restarted: four missed visits after a hit are not eight.
        assertFalse(closeVisit(detector, samsung))
    }

    @Test
    fun `a browser that worked before is not exempt for good`() {
        // The likeliest way an id goes stale is a browser updating in place, which
        // always follows a period of working. Exempting a package after one success
        // would go deaf to exactly that.
        val detector = detector(threshold = 5)

        repeat(100) { visit(detector, samsung, true) }
        missedVisits(detector, samsung, 5)

        assertTrue(closeVisit(detector, samsung))
    }

    @Test
    fun `reporting backs off instead of stopping`() {
        val detector = detector(threshold = 5)

        missedVisits(detector, samsung, 5)
        assertTrue(closeVisit(detector, samsung))

        // The bar is now 10x higher: 49 more missed visits stay quiet, the 50th reports.
        missedVisits(detector, samsung, 49)
        assertFalse(closeVisit(detector, samsung))
        visit(detector, samsung, false)
        assertTrue(closeVisit(detector, samsung))
    }

    @Test
    fun `a report never makes the detector deaf`() {
        // A report that turns out to be wrong must not spend the only one this package
        // will ever get — the true failure can come later.
        val detector = detector(threshold = 5)

        missedVisits(detector, samsung, 5)
        closeVisit(detector, samsung)

        assertEquals(1, missedVisits(detector, samsung, 51))
    }

    @Test
    fun `each browser is counted, and backed off, on its own`() {
        val detector = detector(threshold = 5)

        missedVisits(detector, samsung, 5)
        closeVisit(detector, samsung)

        // Samsung's raised bar says nothing about Chrome.
        missedVisits(detector, chrome, 5)
        assertTrue(closeVisit(detector, chrome))
    }

    @Test
    fun `the real threshold asks for more than one unlucky visit`() {
        assertTrue(UrlBarBlindSpotDetector.MIN_MISSED_VISITS_BEFORE_REPORTING >= 3)
        assertTrue(UrlBarBlindSpotDetector.BACKOFF_FACTOR > 1)
    }
}
