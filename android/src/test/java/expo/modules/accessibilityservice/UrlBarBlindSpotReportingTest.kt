package expo.modules.accessibilityservice

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * The detector as the service drives it: window-state changes open and judge visits,
 * content events make the lookups. Observed through the warning the service logs next
 * to the Sentry report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UrlBarBlindSpotReportingTest {
    private val chrome = "com.android.chrome"
    private lateinit var service: AccessibilityService
    private var isUrlBarShown = true

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
        ShadowLog.clear()
        val urlBar = mock<AccessibilityNodeInfo> {
            on { text } doReturn "example.com"
        }
        val root = mock<AccessibilityNodeInfo>()
        whenever(root.findAccessibilityNodeInfosByViewId(any())).thenAnswer {
            if (isUrlBarShown) listOf(urlBar) else emptyList()
        }
        service = spy(Robolectric.buildService(AccessibilityService::class.java).create().get())
        doReturn(root).`when`(service).rootInActiveWindow
    }

    @After
    fun tearDown() {
        AccessibilityService.resetForTesting()
    }

    @Test
    fun `Chrome is not reported when its toolbar hides while the user reads`() {
        repeat(20) {
            isUrlBarShown = true
            openBrowser()
            readPageContent(times = 1)
            isUrlBarShown = false
            readPageContent(times = 100)
        }
        openBrowser()

        assertEquals(0, staleUrlBarReports())
    }

    @Test
    fun `a browser whose URL bar is never found reports itself after several visits`() {
        isUrlBarShown = false

        repeat(UrlBarBlindSpotDetector.MIN_MISSED_VISITS_BEFORE_REPORTING) {
            openBrowser()
            readPageContent(times = 3)
        }
        openBrowser()

        assertEquals(1, staleUrlBarReports())
    }

    private fun openBrowser() {
        service.onAccessibilityEvent(
            mock<AccessibilityEvent> {
                on { eventType } doReturn AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                on { packageName } doReturn chrome
            },
        )
    }

    /** One tree lookup per content event: each waits out the 250ms query pacing. */
    private fun readPageContent(times: Int) {
        val contentChanged = mock<AccessibilityEvent> {
            on { eventType } doReturn AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            on { packageName } doReturn chrome
        }
        repeat(times) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(250))
            service.onAccessibilityEvent(contentChanged)
        }
    }

    private fun staleUrlBarReports(): Int =
        ShadowLog.getLogsForTag("AccessibilityService").count {
            it.msg.startsWith("No URL bar found in $chrome")
        }
}
