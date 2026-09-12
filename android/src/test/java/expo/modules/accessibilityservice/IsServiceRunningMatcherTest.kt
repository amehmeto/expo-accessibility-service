package expo.modules.accessibilityservice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsServiceRunningMatcherTest {

    private val expected = "com.tiedsiren.tiedsiren/expo.modules.accessibilityservice.AccessibilityService"
    private val custom = "com.tiedsiren.tiedsiren/com.tiedsiren.CustomAccessibilityService"

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

    // --- matchesAnyBoundService: a configured service resolves to several candidates ---

    @Test
    fun `matches when any one of the expected ids is bound`() {
        val bound = listOf("other.app/other.Service", custom)

        assertTrue(AccessibilityService.matchesAnyBoundService(bound, listOf(expected, custom)))
    }

    @Test
    fun `does not match when none of the expected ids is bound`() {
        val bound = listOf("other.app/other.Service")

        assertFalse(AccessibilityService.matchesAnyBoundService(bound, listOf(expected, custom)))
    }

    @Test
    fun `does not match for an empty expected list`() {
        assertFalse(AccessibilityService.matchesAnyBoundService(listOf(expected), emptyList()))
    }

    /**
     * The regression this pair exists for: `isEnabled()` asked about the configured
     * service while `isServiceRunning()` asked about this library's own, so a host app
     * with a custom service class was told "granted" and "stopped" at the same time.
     */
    @Test
    fun `a custom service alone is bound, and the library service is not`() {
        val bound = listOf(custom)

        assertTrue(AccessibilityService.matchesAnyBoundService(bound, listOf(custom)))
        assertFalse(AccessibilityService.matchesAnyBoundService(bound, listOf(expected)))
    }

    // --- inProcessSignalAnswersFor: when the in-process shortcut may speak ---

    @Test
    fun `in-process signal answers for this library's own service`() {
        assertTrue(
            AccessibilityService.inProcessSignalAnswersFor(
                isConnected = true,
                hasInstance = true,
                defaultServiceId = expected,
                serviceIds = listOf(expected),
            )
        )
    }

    @Test
    fun `in-process signal stays silent about a custom service`() {
        // isConnected and instance are set by THIS library's service. They say nothing
        // about the host app's own service class, so they must not answer for it.
        assertFalse(
            AccessibilityService.inProcessSignalAnswersFor(
                isConnected = true,
                hasInstance = true,
                defaultServiceId = expected,
                serviceIds = listOf(custom),
            )
        )
    }

    @Test
    fun `in-process signal answers when the library service is among several ids`() {
        assertTrue(
            AccessibilityService.inProcessSignalAnswersFor(
                isConnected = true,
                hasInstance = true,
                defaultServiceId = expected,
                serviceIds = listOf(custom, expected),
            )
        )
    }

    @Test
    fun `in-process signal stays silent when not connected`() {
        assertFalse(
            AccessibilityService.inProcessSignalAnswersFor(
                isConnected = false,
                hasInstance = true,
                defaultServiceId = expected,
                serviceIds = listOf(expected),
            )
        )
    }

    @Test
    fun `in-process signal stays silent without a service instance`() {
        // Connected but instance-less is the window after a process restart cleared the
        // statics: the system view has to answer instead.
        assertFalse(
            AccessibilityService.inProcessSignalAnswersFor(
                isConnected = true,
                hasInstance = false,
                defaultServiceId = expected,
                serviceIds = listOf(expected),
            )
        )
    }
}
