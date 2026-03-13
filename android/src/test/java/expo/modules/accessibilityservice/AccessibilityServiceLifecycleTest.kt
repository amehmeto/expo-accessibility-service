package expo.modules.accessibilityservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    /**
     * Tests the broadcast-triggered re-registration pattern used by
     * ExpoAccessibilityServiceModule and BlockingCallback.
     *
     * ExpoAccessibilityServiceModule cannot be unit-tested directly because it
     * depends on the Expo module framework (Module, ModuleDefinition, appContext).
     * This test validates the same pattern: a BroadcastReceiver that listens for
     * ACTION_SERVICE_CONNECTED and re-registers a dropped listener.
     */
    @Test
    fun `broadcast receiver re-registers dropped listener on ACTION_SERVICE_CONNECTED`() {
        val listener = object : AccessibilityService.EventListener {
            override fun onAppChanged(packageName: String, className: String, timestamp: Long) {}
        }

        // Register listener, then simulate it being dropped by Android
        AccessibilityService.addEventListener(listener)
        AccessibilityService.removeEventListener(listener)
        assertFalse("Listener should be dropped",
            AccessibilityService.hasListener(listener))

        // Register a BroadcastReceiver that re-registers the listener (same pattern as the module)
        val context = RuntimeEnvironment.getApplication()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AccessibilityService.ACTION_SERVICE_CONNECTED) {
                    if (!AccessibilityService.hasListener(listener)) {
                        AccessibilityService.addEventListener(listener)
                    }
                }
            }
        }
        context.registerReceiver(
            receiver, IntentFilter(AccessibilityService.ACTION_SERVICE_CONNECTED)
        )

        // Simulate service restart — onServiceConnected sends the broadcast
        val service = Robolectric.buildService(AccessibilityService::class.java)
            .create()
            .get()
        service.onServiceConnected()

        // Process the broadcast
        shadowOf(RuntimeEnvironment.getApplication()).run {
            val broadcastIntent = broadcastIntents
                .first { it.action == AccessibilityService.ACTION_SERVICE_CONNECTED }
            receiver.onReceive(context, broadcastIntent)
        }

        assertTrue("Listener should be re-registered after broadcast",
            AccessibilityService.hasListener(listener))

        context.unregisterReceiver(receiver)
    }

    @Test
    fun `broadcast receiver does not duplicate listener if already registered`() {
        val listener = object : AccessibilityService.EventListener {
            override fun onAppChanged(packageName: String, className: String, timestamp: Long) {}
        }

        // Listener is already registered
        AccessibilityService.addEventListener(listener)

        val context = RuntimeEnvironment.getApplication()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (!AccessibilityService.hasListener(listener)) {
                    AccessibilityService.addEventListener(listener)
                }
            }
        }
        context.registerReceiver(
            receiver, IntentFilter(AccessibilityService.ACTION_SERVICE_CONNECTED)
        )

        val service = Robolectric.buildService(AccessibilityService::class.java)
            .create()
            .get()
        service.onServiceConnected()

        shadowOf(RuntimeEnvironment.getApplication()).run {
            val broadcastIntent = broadcastIntents
                .first { it.action == AccessibilityService.ACTION_SERVICE_CONNECTED }
            receiver.onReceive(context, broadcastIntent)
        }

        assertEquals("Should still have exactly 1 listener (no duplicate)",
            1, AccessibilityService.getListenerCount())

        context.unregisterReceiver(receiver)
    }
}
