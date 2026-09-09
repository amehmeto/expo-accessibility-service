package expo.modules.accessibilityservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that turns a silent failure into a signal: a browser we claim to support
 * whose address bar we stop finding. See [UrlBarBlindSpotDetector] for why no other
 * kind of test can catch a stale view id.
 */
class UrlBarBlindSpotDetectorTest {

    private val samsung = "com.sec.android.app.sbrowser"
    private val chrome = "com.android.chrome"

    private fun detector(threshold: Int = 5) =
        UrlBarBlindSpotDetector(minMissesBeforeReporting = threshold)

    private fun feedMisses(detector: UrlBarBlindSpotDetector, pkg: String, count: Int): Int =
        (1..count).count { detector.onLookup(pkg, foundNode = false) }

    @Test
    fun `stays quiet below the threshold`() {
        val detector = detector(threshold = 5)

        assertEquals(0, feedMisses(detector, samsung, 4))
    }

    @Test
    fun `reports once the browser has produced nothing for long enough`() {
        val detector = detector(threshold = 5)

        assertEquals(1, feedMisses(detector, samsung, 5))
    }

    @Test
    fun `a found node never reports, even holding no text`() {
        // Empty is the id being RIGHT with nothing to read yet — a blank new tab. The
        // caller passes foundNode = true for it, and this must not count against the
        // browser.
        val detector = detector(threshold = 5)

        assertFalse(detector.onLookup(samsung, foundNode = true))
    }

    @Test
    fun `misses have to be consecutive`() {
        val detector = detector(threshold = 5)

        feedMisses(detector, samsung, 4)
        detector.onLookup(samsung, foundNode = true)

        // The count restarted: four more misses are not nine.
        assertEquals(0, feedMisses(detector, samsung, 4))
        assertEquals(1, feedMisses(detector, samsung, 1))
    }

    @Test
    fun `a browser that worked before is not exempt for good`() {
        // The likeliest way an id goes stale is a browser updating in place, which
        // always follows a period of working. Exempting a package after one success
        // would go deaf to exactly that.
        val detector = detector(threshold = 5)

        repeat(100) { detector.onLookup(samsung, foundNode = true) }

        assertEquals(1, feedMisses(detector, samsung, 5))
    }

    @Test
    fun `reporting backs off instead of stopping`() {
        val detector = detector(threshold = 5)

        assertEquals(1, feedMisses(detector, samsung, 5))
        // The bar is now 10x higher: the next 49 misses stay quiet, the 50th reports.
        assertEquals(0, feedMisses(detector, samsung, 49))
        assertEquals(1, feedMisses(detector, samsung, 1))
    }

    @Test
    fun `a report never makes the detector deaf`() {
        // A report that turns out to be wrong must not spend the only one this package
        // will ever get — the true failure can come later.
        val detector = detector(threshold = 5)

        feedMisses(detector, samsung, 5)

        assertEquals(1, feedMisses(detector, samsung, 50))
    }

    @Test
    fun `each browser is counted, and backed off, on its own`() {
        val detector = detector(threshold = 5)

        feedMisses(detector, samsung, 5)
        // Samsung's raised bar says nothing about Chrome.
        assertEquals(1, feedMisses(detector, chrome, 5))
    }

    @Test
    fun `a working browser is never reported`() {
        val detector = detector(threshold = 5)

        repeat(500) {
            // Three misses then a hit, over and over: never five in a row.
            val found = it % 4 == 3
            assertFalse(detector.onLookup(chrome, foundNode = found))
        }
    }

    @Test
    fun `the real threshold sits clear of ordinary churn`() {
        // Browsers fire content events constantly and plenty resolve nothing useful.
        assertTrue(UrlBarBlindSpotDetector.MIN_MISSES_BEFORE_REPORTING >= 20)
        assertTrue(UrlBarBlindSpotDetector.BACKOFF_FACTOR > 1)
    }
}
