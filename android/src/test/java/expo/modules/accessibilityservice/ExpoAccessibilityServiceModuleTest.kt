package expo.modules.accessibilityservice

import android.os.Looper
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.ModuleHolder
import expo.modules.kotlin.ModuleRegistry
import expo.modules.kotlin.events.EventEmitter
import expo.modules.kotlin.events.EventName
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ExpoAccessibilityServiceModuleTest {
    private val appContext = mock<AppContext>()
    private val eventEmitter = mock<EventEmitter>()
    private lateinit var holder: ModuleHolder<ExpoAccessibilityServiceModule>

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
        val module = ExpoAccessibilityServiceModule()
        whenever(appContext.reactContext).thenReturn(RuntimeEnvironment.getApplication())
        whenever(appContext.eventEmitter(module)).thenReturn(eventEmitter)
        val registry = ModuleRegistry(WeakReference(appContext))
        registry.register(module)
        holder = requireNotNull(registry.getModuleHolder(module))
        holder.post(EventName.MODULE_CREATE)
    }

    @After
    fun tearDown() {
        if (::holder.isInitialized) holder.post(EventName.MODULE_DESTROY)
        AccessibilityService.resetForTesting()
    }

    @Test
    fun `forwards typing and committed navigation from the service to Expo`() {
        AccessibilityService.notifyUrlBarListeners("com.android.chrome", "fa", 123L, true)
        AccessibilityService.notifyUrlBarListeners("com.android.chrome", "facebook.com", 124L, false)

        val payloads = argumentCaptor<Map<*, *>>()
        verify(eventEmitter, times(2)).emit(eq("onUrlBarChanged"), payloads.capture())
        assertEquals(
            listOf(
                mapOf(
                    "packageName" to "com.android.chrome",
                    "rawText" to "fa",
                    "timestamp" to 123L,
                    "isEditing" to true,
                ),
                mapOf(
                    "packageName" to "com.android.chrome",
                    "rawText" to "facebook.com",
                    "timestamp" to 124L,
                    "isEditing" to false,
                ),
            ),
            payloads.allValues,
        )
        assertTrue(holder.definition.eventsDefinition!!.names.contains("onUrlBarChanged"))
    }

    @Test
    fun `stops forwarding URL readings when Expo destroys the module`() {
        holder.post(EventName.MODULE_DESTROY)

        AccessibilityService.notifyUrlBarListeners("com.android.chrome", "facebook.com", 124L, false)

        verifyNoInteractions(eventEmitter)
    }

    @Test
    fun `resumes forwarding once after repeated service reconnections`() {
        AccessibilityService.removeEventListener(holder.module)
        val service = Robolectric.buildService(AccessibilityService::class.java).create().get()

        service.onServiceConnected()
        shadowOf(Looper.getMainLooper()).idle()
        service.onServiceConnected()
        shadowOf(Looper.getMainLooper()).idle()
        AccessibilityService.notifyUrlBarListeners("com.android.chrome", "facebook.com", 124L, false)

        verify(eventEmitter).emit(
            "onUrlBarChanged",
            mapOf(
                "packageName" to "com.android.chrome",
                "rawText" to "facebook.com",
                "timestamp" to 124L,
                "isEditing" to false,
            ),
        )
        service.onDestroy()
    }
}
