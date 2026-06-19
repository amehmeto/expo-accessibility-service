package expo.modules.accessibilityservice

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

/**
 * Unit tests for the browser URL-bar detection added for website blocking (#19).
 * Exercises the pure resolver (browser filtering + null-safety) and the
 * listener notification path, without needing a real AccessibilityNodeInfo.
 */
class UrlBarDetectionTest {

    private val chrome = "com.android.chrome"
    private val chromeUrlBar = "com.android.chrome:id/url_bar"
    private val samsung = "com.sec.android.app.sbrowser"
    private val samsungUrlBar = "com.sec.android.app.sbrowser:id/location_bar_edit_text"

    private lateinit var listener: AccessibilityService.EventListener

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
        listener = mock()
    }

    @After
    fun tearDown() {
        AccessibilityService.resetForTesting()
    }

    @Test
    fun `isSupportedBrowser recognizes Chrome and Samsung Internet only`() {
        assertTrue(AccessibilityService.isSupportedBrowser(chrome))
        assertTrue(AccessibilityService.isSupportedBrowser(samsung))
        assertFalse(AccessibilityService.isSupportedBrowser("org.mozilla.firefox"))
        assertFalse(AccessibilityService.isSupportedBrowser("com.brave.browser"))
        assertFalse(AccessibilityService.isSupportedBrowser(null))
    }

    @Test
    fun `resolveUrlBarText returns trimmed text for Chrome url bar`() {
        val result = AccessibilityService.resolveUrlBarText(chrome, chromeUrlBar, "  facebook.com  ")
        assertEquals("facebook.com", result)
    }

    @Test
    fun `resolveUrlBarText returns text for Samsung Internet url bar`() {
        val result = AccessibilityService.resolveUrlBarText(samsung, samsungUrlBar, "instagram.com")
        assertEquals("instagram.com", result)
    }

    @Test
    fun `resolveUrlBarText ignores non-browser packages`() {
        val result = AccessibilityService.resolveUrlBarText(
            "org.mozilla.firefox",
            "org.mozilla.firefox:id/url_bar_title",
            "facebook.com"
        )
        assertNull(result)
    }

    @Test
    fun `resolveUrlBarText ignores fields that are not the url bar`() {
        // e.g. a content text view inside the page, not the omnibox
        val result = AccessibilityService.resolveUrlBarText(chrome, "com.android.chrome:id/title_bar", "facebook.com")
        assertNull(result)
    }

    @Test
    fun `resolveUrlBarText is null-safe on empty text (new tab)`() {
        assertNull(AccessibilityService.resolveUrlBarText(chrome, chromeUrlBar, ""))
        assertNull(AccessibilityService.resolveUrlBarText(chrome, chromeUrlBar, "   "))
        assertNull(AccessibilityService.resolveUrlBarText(chrome, chromeUrlBar, null))
    }

    @Test
    fun `resolveUrlBarText is null-safe on null view id`() {
        assertNull(AccessibilityService.resolveUrlBarText(chrome, null, "facebook.com"))
    }

    @Test
    fun `notifyUrlBarListeners forwards to registered listeners`() {
        AccessibilityService.addEventListener(listener)

        AccessibilityService.notifyUrlBarListeners(chrome, "facebook.com", 123L)

        verify(listener).onUrlBarChanged(eq(chrome), eq("facebook.com"), eq(123L))
    }

    @Test
    fun `notifyUrlBarListeners does not invoke onAppChanged`() {
        AccessibilityService.addEventListener(listener)

        AccessibilityService.notifyUrlBarListeners(chrome, "facebook.com", 123L)

        verify(listener, never()).onAppChanged(eq(chrome), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
