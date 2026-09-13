package expo.modules.accessibilityservice

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BrowserContentPacingTest {
    private lateinit var service: AccessibilityService

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
        service = spy(Robolectric.buildService(AccessibilityService::class.java).create().get())
    }

    @After
    fun tearDown() {
        AccessibilityService.resetForTesting()
    }

    @Test
    fun `a burst of content events acquires the window root once`() {
        val root = mock<AccessibilityNodeInfo>()
        doReturn(root).`when`(service).rootInActiveWindow
        val event = mock<AccessibilityEvent> {
            on { eventType } doReturn AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            on { packageName } doReturn "com.android.chrome"
        }

        repeat(100) { service.onAccessibilityEvent(event) }

        verify(service, times(1)).rootInActiveWindow
        verify(root, times(1)).recycle()
    }

    @Test
    fun `a missing root does not delay the next available window`() {
        val root = mock<AccessibilityNodeInfo>()
        doReturn(null, root).`when`(service).rootInActiveWindow
        val event = browserContentEvent()

        service.onAccessibilityEvent(event)
        service.onAccessibilityEvent(event)

        verify(service, times(2)).rootInActiveWindow
        verify(root, times(1)).findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        verify(root, times(1)).recycle()
    }

    @Test
    fun `the root is read again when the interval expires`() {
        val root = mock<AccessibilityNodeInfo>()
        doReturn(root).`when`(service).rootInActiveWindow
        val event = browserContentEvent()

        service.onAccessibilityEvent(event)
        ShadowSystemClock.advanceBy(Duration.ofMillis(249))
        service.onAccessibilityEvent(event)
        verify(service, times(1)).rootInActiveWindow
        ShadowSystemClock.advanceBy(Duration.ofMillis(1))
        service.onAccessibilityEvent(event)

        verify(service, times(2)).rootInActiveWindow
        verify(root, times(2)).recycle()
    }

    private fun browserContentEvent() = mock<AccessibilityEvent> {
        on { eventType } doReturn AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        on { packageName } doReturn "com.android.chrome"
    }
}
