package expo.modules.accessibilityservice

import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Lifecycle tests for AccessibilityService using Robolectric.
 * Tests onServiceConnected broadcast, isConnected state transitions,
 * and cleanup on unbind/destroy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AccessibilityServiceLifecycleTest {

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
    }

    @After
    fun tearDown() {
        AccessibilityService.resetForTesting()
    }

    @Test
    fun `onServiceConnected sets isConnected to true`() {
        val service = Robolectric.buildService(AccessibilityService::class.java)
            .create()
            .get()

        assertFalse("isConnected should be false before onServiceConnected",
            AccessibilityService.isConnected)

        service.onServiceConnected()

        assertTrue("isConnected should be true after onServiceConnected",
            AccessibilityService.isConnected)
    }

    @Test
    fun `onServiceConnected sends ACTION_SERVICE_CONNECTED broadcast`() {
        val service = Robolectric.buildService(AccessibilityService::class.java)
            .create()
            .get()

        service.onServiceConnected()

        val shadowApp = shadowOf(RuntimeEnvironment.getApplication())
        val broadcastIntents = shadowApp.broadcastIntents

        assertTrue(
            "ACTION_SERVICE_CONNECTED broadcast should be sent",
            broadcastIntents.any { it.action == AccessibilityService.ACTION_SERVICE_CONNECTED }
        )
    }

    @Test
    fun `onServiceConnected broadcast is scoped to app package`() {
        val service = Robolectric.buildService(AccessibilityService::class.java)
            .create()
            .get()

        service.onServiceConnected()

        val shadowApp = shadowOf(RuntimeEnvironment.getApplication())
        val broadcastIntent = shadowApp.broadcastIntents
            .first { it.action == AccessibilityService.ACTION_SERVICE_CONNECTED }

        assertEquals(
            "Broadcast should be scoped to app package",
            service.packageName,
            broadcastIntent.`package`
        )
    }

    @Test
    fun `onUnbind sets isConnected to false`() {
        val service = Robolectric.buildService(AccessibilityService::class.java)
            .create()
            .get()

        service.onServiceConnected()
        assertTrue(AccessibilityService.isConnected)

        service.onUnbind(Intent())

        assertFalse("isConnected should be false after onUnbind",
            AccessibilityService.isConnected)
    }

    @Test
    fun `onDestroy sets isConnected to false`() {
        val service = Robolectric.buildService(AccessibilityService::class.java)
            .create()
            .get()

        service.onServiceConnected()
        assertTrue(AccessibilityService.isConnected)

        service.onDestroy()

        assertFalse("isConnected should be false after onDestroy",
            AccessibilityService.isConnected)
    }

    @Test
    fun `onServiceConnected preserves existing listeners`() {
        val listener = object : AccessibilityService.EventListener {
            override fun onAppChanged(packageName: String, className: String, timestamp: Long) {}
        }
        AccessibilityService.addEventListener(listener)

        val service = Robolectric.buildService(AccessibilityService::class.java)
            .create()
            .get()

        service.onServiceConnected()

        assertTrue("Existing listener should still be registered",
            AccessibilityService.hasListener(listener))
        assertEquals("Listener count should be preserved",
            1, AccessibilityService.getListenerCount())
    }
}
