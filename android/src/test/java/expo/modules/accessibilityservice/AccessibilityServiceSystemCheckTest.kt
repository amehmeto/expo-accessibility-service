package expo.modules.accessibilityservice

import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [AccessibilityService.isServiceEnabledInSystem] and the shared
 * [AccessibilityService.isAnyServiceEnabled] helper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AccessibilityServiceSystemCheckTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val packageName get() = context.packageName
    private val serviceId get() = "$packageName/${AccessibilityService::class.java.canonicalName}"

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
        // Clear any previously set value
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            null
        )
    }

    @After
    fun tearDown() {
        AccessibilityService.resetForTesting()
    }

    // --- isServiceEnabledInSystem ---

    @Test
    fun `isServiceEnabledInSystem returns true when service is enabled`() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            serviceId
        )

        assertTrue(AccessibilityService.isServiceEnabledInSystem(context))
    }

    @Test
    fun `isServiceEnabledInSystem returns true when service is among others`() {
        val services = listOf(
            "com.android.talkback/com.google.android.marvin.talkback.TalkBackService",
            serviceId,
            "com.samsung.accessibility/com.samsung.accessibility.AssistantMenuService"
        ).joinToString(":")

        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            services
        )

        assertTrue(AccessibilityService.isServiceEnabledInSystem(context))
    }

    @Test
    fun `isServiceEnabledInSystem returns false when service is not enabled`() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.android.talkback/com.google.android.marvin.talkback.TalkBackService"
        )

        assertFalse(AccessibilityService.isServiceEnabledInSystem(context))
    }

    @Test
    fun `isServiceEnabledInSystem returns false when no services enabled`() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )

        assertFalse(AccessibilityService.isServiceEnabledInSystem(context))
    }

    @Test
    fun `isServiceEnabledInSystem returns false when setting is null`() {
        // null is the default when nothing has been written
        assertFalse(AccessibilityService.isServiceEnabledInSystem(context))
    }

    @Test
    fun `isServiceEnabledInSystem rejects partial matches`() {
        // A shorter string that is a prefix of the real service ID should NOT match
        val partialMatch = "$packageName/${AccessibilityService::class.java.canonicalName}Extra"
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            partialMatch
        )

        assertFalse(AccessibilityService.isServiceEnabledInSystem(context))
    }

    // --- isAnyServiceEnabled ---

    @Test
    fun `isAnyServiceEnabled matches any of the provided service IDs`() {
        val otherService = "$packageName/com.example.OtherService"
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            otherService
        )

        assertTrue(
            AccessibilityService.isAnyServiceEnabled(
                context,
                listOf("$packageName/com.example.Missing", otherService)
            )
        )
    }

    @Test
    fun `isAnyServiceEnabled returns false when none match`() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.other.pkg/com.other.pkg.Service"
        )

        assertFalse(
            AccessibilityService.isAnyServiceEnabled(
                context,
                listOf("$packageName/com.example.ServiceA", "$packageName/com.example.ServiceB")
            )
        )
    }

    @Test
    fun `isAnyServiceEnabled handles whitespace around colons`() {
        val services = " $serviceId : com.other/com.other.Service "
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            services
        )

        assertTrue(AccessibilityService.isAnyServiceEnabled(context, listOf(serviceId)))
    }
}
