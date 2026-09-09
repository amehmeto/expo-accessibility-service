package expo.modules.accessibilityservice

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

class UrlBarDetectionTest {

    private val chrome = "com.android.chrome"
    private val chromeUrlBar = "com.android.chrome:id/url_bar"
    private val samsung = "com.sec.android.app.sbrowser"
    private val samsungUrlBar = "com.sec.android.app.sbrowser:id/location_bar_edit_text"
    private val opera = "com.opera.browser"
    private val operaUrlBar = "com.opera.browser:id/url_field"
    private val firefox = "org.mozilla.firefox"
    private val firefoxUrlBar = "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"

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
    fun `isSupportedBrowser recognizes the popular Android browsers`() {
        for (pkg in listOf(
            chrome,
            samsung,
            opera,
            firefox,
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.vivaldi.browser",
            "com.opera.gx",
            "org.mozilla.focus",
            "com.duckduckgo.mobile.android",
        )) {
            assertTrue("expected $pkg to be supported", AccessibilityService.isSupportedBrowser(pkg))
        }
    }

    @Test
    fun `isSupportedBrowser rejects non-browsers and null`() {
        assertFalse(AccessibilityService.isSupportedBrowser("com.whatsapp"))
        assertFalse(AccessibilityService.isSupportedBrowser("com.google.android.youtube"))
        assertFalse(AccessibilityService.isSupportedBrowser(null))
    }

    @Test
    fun `a browser with several known ids resolves any of them`() {
        // Firefox Focus carries its own display_url on older builds and the Mozilla
        // components id since the migration; both are on real devices right now.
        // Keying on one would leave the other half unblocked, silently.
        val focus = "org.mozilla.focus"
        assertEquals(
            "facebook.com",
            AccessibilityService.resolveUrlBarText(
                focus, "org.mozilla.focus:id/display_url", "facebook.com",
            ),
        )
        assertEquals(
            "facebook.com",
            AccessibilityService.resolveUrlBarText(
                focus, "org.mozilla.focus:id/mozac_browser_toolbar_url_view", "facebook.com",
            ),
        )
    }

    @Test
    fun `an id belonging to another browser is not accepted`() {
        // The candidates are per package, not a global pool.
        assertNull(
            AccessibilityService.resolveUrlBarText(
                chrome, "com.android.chrome:id/location_bar_edit_text", "facebook.com",
            ),
        )
    }

    @Test
    fun `every browser declares at least one field id`() {
        AccessibilityService.BROWSER_URL_BAR_FIELD_IDS.forEach { (pkg, ids) ->
            assertTrue("$pkg declares no url bar id", ids.isNotEmpty())
            assertTrue("$pkg declares a blank url bar id", ids.all { it.isNotBlank() })
            assertEquals("$pkg repeats an id", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `a candidate that already carries a package keeps it`() {
        // A rebranded fork can answer to the upstream resource package. Deriving the
        // prefix from the running package would make such an entry dead on arrival, so
        // a candidate containing ':' is taken as already qualified.
        val ids = AccessibilityService.BROWSER_URL_BAR_FIELD_IDS.entries.first()
        val qualified = AccessibilityService.urlBarViewIds(ids.key)
        assertTrue(qualified.all { it.contains(":id/") })
        assertTrue(qualified.none { it.startsWith("${ids.key}:id/${ids.key}") })
    }

    @Test
    fun `view ids are qualified with the browser package`() {
        assertEquals(
            listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
            AccessibilityService.urlBarViewIds(samsung),
        )
        assertTrue(AccessibilityService.urlBarViewIds("com.whatsapp").isEmpty())
    }

    @Test
    fun `resolveUrlBarText resolves Opera and Firefox url bars`() {
        assertEquals("facebook.com", AccessibilityService.resolveUrlBarText(opera, operaUrlBar, "facebook.com"))
        assertEquals("reddit.com", AccessibilityService.resolveUrlBarText(firefox, firefoxUrlBar, "reddit.com"))
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
            "com.whatsapp",
            "com.whatsapp:id/search_src_text",
            "facebook.com"
        )
        assertNull(result)
    }

    @Test
    fun `resolveUrlBarText ignores fields that are not the url bar`() {
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

        verify(listener).onUrlBarChanged(eq(chrome), eq("facebook.com"), eq(123L), eq(false))
    }

    @Test
    fun `notifyUrlBarListeners forwards whether the user was editing the address`() {
        AccessibilityService.addEventListener(listener)

        AccessibilityService.notifyUrlBarListeners(chrome, "facebook.com", 123L, isEditing = true)

        verify(listener).onUrlBarChanged(eq(chrome), eq("facebook.com"), eq(123L), eq(true))
    }

    @Test
    fun `a listener written against the three-argument callback still receives events`() {
        // The overload exists so that adding isEditing did not break existing consumers.
        val received = mutableListOf<Triple<String, String, Long>>()
        val legacyListener = object : AccessibilityService.EventListener {
            override fun onAppChanged(packageName: String, className: String, timestamp: Long) {}
            override fun onUrlBarChanged(packageName: String, rawText: String, timestamp: Long) {
                received.add(Triple(packageName, rawText, timestamp))
            }
        }
        AccessibilityService.addEventListener(legacyListener)

        AccessibilityService.notifyUrlBarListeners(chrome, "facebook.com", 123L, isEditing = true)

        assertEquals(listOf(Triple(chrome, "facebook.com", 123L)), received)
    }

    @Test
    fun `notifyUrlBarListeners does not invoke onAppChanged`() {
        AccessibilityService.addEventListener(listener)

        AccessibilityService.notifyUrlBarListeners(chrome, "facebook.com", 123L)

        verify(listener, never()).onAppChanged(eq(chrome), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
