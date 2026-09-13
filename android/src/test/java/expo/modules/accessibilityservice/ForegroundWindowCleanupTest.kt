package expo.modules.accessibilityservice

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], manifest = Config.NONE)
class ForegroundWindowCleanupTest {
    private lateinit var service: AccessibilityService

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
        service = Robolectric.buildService(AccessibilityService::class.java).create().get()
        service.onServiceConnected()
    }

    @After
    fun tearDown() {
        service.onDestroy()
        AccessibilityService.resetForTesting()
    }

    @Test
    fun `releases every retrieved window after finding the foreground app`() {
        val root = mock<AccessibilityNodeInfo>()
        whenever(root.packageName).thenReturn("com.example.browser")
        whenever(root.className).thenReturn("com.example.browser.MainActivity")
        val activeWindow = mock<AccessibilityWindowInfo>()
        whenever(activeWindow.isActive).thenReturn(true)
        whenever(activeWindow.root).thenReturn(root)
        val remainingWindow = mock<AccessibilityWindowInfo>()
        shadowOf(service).setWindows(listOf(activeWindow, remainingWindow))
        val detectedPackages = mutableListOf<String>()
        AccessibilityService.addEventListener(object : AccessibilityService.EventListener {
            override fun onAppChanged(packageName: String, className: String, timestamp: Long) {
                detectedPackages.add(packageName)
            }
        })

        AccessibilityService.emitCurrentForegroundApp()

        assertEquals(listOf("com.example.browser"), detectedPackages)
        verify(root).recycle()
        verify(activeWindow).recycle()
        verify(remainingWindow).recycle()
    }
}
