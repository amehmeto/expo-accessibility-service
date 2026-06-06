package expo.modules.accessibilityservice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsServiceRunningMatcherTest {

    private val expected = "com.tiedsiren.tiedsiren/expo.modules.accessibilityservice.AccessibilityService"

    @Test
    fun `returns true when expected id is in the bound list`() {
        val bound = listOf("other.app/other.Service", expected)
        assertTrue(AccessibilityService.matchesBoundService(bound, expected))
    }

    @Test
    fun `returns false when expected id is absent`() {
        val bound = listOf("other.app/other.Service")
        assertFalse(AccessibilityService.matchesBoundService(bound, expected))
    }

    @Test
    fun `returns false for an empty bound list`() {
        assertFalse(AccessibilityService.matchesBoundService(emptyList(), expected))
    }
}
