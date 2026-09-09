package expo.modules.accessibilityservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that turns a silent failure into a signal: a browser we claim to support
 * whose address bar we never find. See [UrlBarBlindSpotDetector] for why no other kind
 * of test can catch a stale view id.
 */
class UrlBarBlindSpotDetectorTest {

    private val samsung = "com.sec.android.app.sbrowser"
    private val chrome = "com.android.chrome"

    private fun detector(threshold: Int = 5) =
        UrlBarBlindSpotDetector(minEventsBeforeReporting = threshold)

    private fun feedMisses(detector: UrlBarBlindSpotDetector, pkg: String, count: Int): Int =
        (1..count).count { detector.onBrowserEvent(pkg, resolved = false) }

    @Test
    fun `stays quiet below the threshold`() {
        val detector = detector(threshold = 5)

        assertFalse(detector.onBrowserEvent(samsung, resolved = false))
        assertFalse(detector.onBrowserEvent(samsung, resolved = false))
        assertFalse(detector.onBrowserEvent(samsung, resolved = false))
    }

    @Test
    fun `reports once the browser has produced nothing for long enough`() {
        val detector = detector(threshold = 5)

        assertEquals(1, feedMisses(detector, samsung, 5))
    }

    @Test
    fun `reports a package only once, however long it keeps failing`() {
        // A signal, not a metric: the id is wrong, saying so a hundred times adds
        // nothing and drowns the issue that matters.
        val detector = detector(threshold = 5)

        assertEquals(1, feedMisses(detector, samsung, 200))
    }

    @Test
    fun `one resolution settles the browser for good`() {
        val detector = detector(threshold = 5)

        feedMisses(detector, samsung, 4)
        detector.onBrowserEvent(samsung, resolved = true)

        // The id works. Misses after it are ordinary content events — a browser fires
        // plenty that have nothing to do with its address bar.
        assertEquals(0, feedMisses(detector, samsung, 200))
    }

    @Test
    fun `a resolution never reports anything itself`() {
        val detector = detector(threshold = 1)

        assertFalse(detector.onBrowserEvent(samsung, resolved = true))
    }

    @Test
    fun `each browser is counted on its own`() {
        val detector = detector(threshold = 5)

        feedMisses(detector, samsung, 4)
        // Chrome working says nothing about Samsung's id.
        detector.onBrowserEvent(chrome, resolved = true)

        assertEquals(1, feedMisses(detector, samsung, 1))
    }

    @Test
    fun `a browser that works is never reported`() {
        val detector = detector(threshold = 5)

        repeat(50) {
            assertFalse(detector.onBrowserEvent(chrome, resolved = it % 3 == 0))
        }
    }

    @Test
    fun `the real threshold is high enough to sit clear of ordinary churn`() {
        // Browsers fire content events constantly. A handful of misses means nothing;
        // the default has to be well above "a few".
        assertTrue(UrlBarBlindSpotDetector.MIN_EVENTS_BEFORE_REPORTING >= 20)
    }
}
