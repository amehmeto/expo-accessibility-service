package expo.modules.accessibilityservice

import android.os.Bundle
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class OpenInForegroundBrowserTest {
    private val chrome = "com.android.chrome"
    private val chromeUrlBar = "com.android.chrome:id/url_bar"
    private val address = "https://www.google.com/"

    private val service: AccessibilityService =
        spy(Robolectric.buildService(AccessibilityService::class.java).create().get())

    @Before
    fun bindTheService() {
        AccessibilityService.resetForTesting()
        service.onServiceConnected()
    }

    @After
    fun unbindTheService() = AccessibilityService.resetForTesting()

    @Test
    fun `taps the URL bar, writes the address in the editing field and presses Enter`() {
        val urlBar = urlBarNode()
        val field = editingField()
        doReturn(windowOf(chrome, urlBar, focusedField = field)).`when`(service).rootInActiveWindow

        assertTrue(AccessibilityService.openInForegroundBrowser(address))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        verify(urlBar).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        verify(field).performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), argThat<Bundle> { writes(address) })
        verify(field).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    @Test
    fun `waits for the editing field the browser draws after the tap`() {
        val field = editingField()
        val windowBeforeTheField = windowOf(chrome, urlBarNode(), focusedField = null)
        val windowWithTheField = windowOf(chrome, urlBarNode(), focusedField = field)
        doReturn(windowBeforeTheField, windowBeforeTheField, windowWithTheField)
            .`when`(service).rootInActiveWindow

        AccessibilityService.openInForegroundBrowser(address)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        verify(field).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    @Test
    fun `tries nothing when the window in front is not a browser`() {
        val root = mock<AccessibilityNodeInfo> { on { packageName } doReturn "com.whatsapp" }
        doReturn(root).`when`(service).rootInActiveWindow

        assertFalse(AccessibilityService.openInForegroundBrowser(address))
    }

    @Test
    fun `tries nothing when the URL bar of the browser is not found`() {
        val root = mock<AccessibilityNodeInfo> {
            on { packageName } doReturn chrome
            on { findAccessibilityNodeInfosByViewId(chromeUrlBar) } doReturn emptyList()
        }
        doReturn(root).`when`(service).rootInActiveWindow

        assertFalse(AccessibilityService.openInForegroundBrowser(address))
    }

    @Test
    fun `writes nothing when the URL bar refuses the tap`() {
        val urlBar = mock<AccessibilityNodeInfo> {
            on { performAction(AccessibilityNodeInfo.ACTION_CLICK) } doReturn false
        }
        val field = editingField()
        doReturn(windowOf(chrome, urlBar, focusedField = field)).`when`(service).rootInActiveWindow

        assertFalse(AccessibilityService.openInForegroundBrowser(address))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        verify(field, never()).performAction(any<Int>(), any())
    }

    @Test
    @Config(sdk = [29])
    fun `tries nothing before Android 11, which gives no Enter action`() {
        val urlBar = urlBarNode()
        doReturn(windowOf(chrome, urlBar, focusedField = editingField())).`when`(service).rootInActiveWindow

        assertFalse(AccessibilityService.openInForegroundBrowser(address))
        verify(urlBar, never()).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `tries nothing when the service is not bound`() {
        AccessibilityService.resetForTesting()

        assertFalse(AccessibilityService.openInForegroundBrowser(address))
    }

    private fun Bundle.writes(text: String) =
        getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE) == text

    private fun urlBarNode() = mock<AccessibilityNodeInfo> {
        on { performAction(AccessibilityNodeInfo.ACTION_CLICK) } doReturn true
    }

    private fun editingField() = mock<AccessibilityNodeInfo> {
        on { isEditable } doReturn true
    }

    private fun windowOf(
        browser: String,
        urlBar: AccessibilityNodeInfo,
        focusedField: AccessibilityNodeInfo?,
    ) = mock<AccessibilityNodeInfo> {
        on { packageName } doReturn browser
        on { findAccessibilityNodeInfosByViewId(chromeUrlBar) } doReturn listOf(urlBar)
        on { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } doReturn focusedField
    }
}
