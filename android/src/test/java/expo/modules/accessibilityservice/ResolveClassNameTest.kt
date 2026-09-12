package expo.modules.accessibilityservice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The class name an event is reported with.
 *
 * Extracted because the two call sites used to disagree: `resolveForegroundPackage`
 * substituted a fallback, while `handleWindowStateChanged` required a non-empty class
 * name and dropped the whole event without one — package included, which is the part
 * a consumer acts on.
 */
class ResolveClassNameTest {

    @Test
    fun `keeps the class name the event carries`() {
        assertEquals(
            "com.example.app.MainActivity",
            AccessibilityService.resolveClassName("com.example.app.MainActivity"),
        )
    }

    @Test
    fun `falls back when the class name is null`() {
        assertEquals(
            AccessibilityService.FALLBACK_CLASS_NAME,
            AccessibilityService.resolveClassName(null),
        )
    }

    @Test
    fun `falls back when the class name is empty`() {
        assertEquals(
            AccessibilityService.FALLBACK_CLASS_NAME,
            AccessibilityService.resolveClassName(""),
        )
    }

    @Test
    fun `the fallback is a real class name, not a marker`() {
        // Consumers put this straight into an event payload, so it has to read as a
        // class name rather than as something like "unknown".
        assertEquals("android.view.View", AccessibilityService.FALLBACK_CLASS_NAME)
    }
}
