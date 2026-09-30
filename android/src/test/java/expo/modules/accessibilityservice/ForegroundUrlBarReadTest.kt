package expo.modules.accessibilityservice

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ForegroundUrlBarReadTest {
    private val chrome = "com.android.chrome"
    private val chromeUrlBar = "com.android.chrome:id/url_bar"

    private lateinit var service: AccessibilityService

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
        service = spy(Robolectric.buildService(AccessibilityService::class.java).create().get())
        service.onServiceConnected()
    }

    @After
    fun tearDown() {
        AccessibilityService.resetForTesting()
    }

    @Test
    fun `reads the address shown by the browser in front`() {
        val root = windowOf(chrome, urlBar = urlBarNode("lemonde.fr", focused = false))
        doReturn(root).`when`(service).rootInActiveWindow

        assertEquals(
            AccessibilityService.ForegroundUrlBar(chrome, "lemonde.fr", isEditing = false),
            AccessibilityService.readForegroundUrlBar(),
        )
        verify(root).recycle()
    }

    @Test
    fun `reads an address the event flow already announced`() {
        val root = windowOf(chrome, urlBar = urlBarNode("lemonde.fr", focused = false))
        doReturn(root).`when`(service).rootInActiveWindow
        service.onAccessibilityEvent(browserContentEvent())

        assertEquals("lemonde.fr", AccessibilityService.readForegroundUrlBar()?.text)
    }

    @Test
    fun `says whether the user is typing in the URL bar`() {
        val root = windowOf(chrome, urlBar = urlBarNode("lemonde", focused = true))
        doReturn(root).`when`(service).rootInActiveWindow

        assertEquals(true, AccessibilityService.readForegroundUrlBar()?.isEditing)
    }

    @Test
    fun `reads nothing when the window in front is not a browser`() {
        val root = mock<AccessibilityNodeInfo> { on { packageName } doReturn "com.whatsapp" }
        doReturn(root).`when`(service).rootInActiveWindow

        assertNull(AccessibilityService.readForegroundUrlBar())
        verify(root).recycle()
    }

    @Test
    fun `keeps the browser identity when the URL bar holds no address`() {
        val root = windowOf(chrome, urlBar = urlBarNode("", focused = false))
        doReturn(root).`when`(service).rootInActiveWindow

        assertEquals(
            AccessibilityService.ForegroundUrlBar(chrome, text = null, isEditing = false),
            AccessibilityService.readForegroundUrlBar(),
        )
    }

    @Test
    fun `keeps the browser identity when its toolbar is hidden`() {
        val root = windowOf(chrome, urlBars = emptyList())
        doReturn(root).`when`(service).rootInActiveWindow

        assertEquals(
            AccessibilityService.ForegroundUrlBar(chrome, text = null, isEditing = false),
            AccessibilityService.readForegroundUrlBar(),
        )
    }

    @Test
    fun `reads nothing when the service is not bound`() {
        AccessibilityService.resetForTesting()

        assertNull(AccessibilityService.readForegroundUrlBar())
    }

    @Test
    fun `does not take the query slot of the next browser event`() {
        val root = windowOf(chrome, urlBar = urlBarNode("lemonde.fr", focused = false))
        doReturn(root).`when`(service).rootInActiveWindow

        AccessibilityService.readForegroundUrlBar()
        service.onAccessibilityEvent(browserContentEvent())

        verify(service, times(2)).rootInActiveWindow
    }

    private fun urlBarNode(text: String, focused: Boolean) = mock<AccessibilityNodeInfo> {
        on { this.text } doReturn text
        on { isFocused } doReturn focused
    }

    private fun windowOf(browser: String, urlBar: AccessibilityNodeInfo) =
        windowOf(browser, listOf(urlBar))

    private fun windowOf(browser: String, urlBars: List<AccessibilityNodeInfo>) =
        mock<AccessibilityNodeInfo> {
            on { packageName } doReturn browser
            on { findAccessibilityNodeInfosByViewId(chromeUrlBar) } doReturn urlBars
        }

    private fun browserContentEvent() = mock<AccessibilityEvent> {
        on { eventType } doReturn AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        on { packageName } doReturn chrome
    }
}
