package expo.modules.accessibilityservice

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ForegroundWindowTextTest {
    private val settings = "com.android.settings"
    private val settingsPackages = setOf(settings, "com.samsung.android.settings")

    private val service: AccessibilityService =
        spy(Robolectric.buildService(AccessibilityService::class.java).create().get())

    @Before
    fun bindService() {
        AccessibilityService.resetForTesting()
        service.onServiceConnected()
    }

    @After
    fun unbindService() = AccessibilityService.resetForTesting()

    @Test
    fun `sees a text the window in front shows`() {
        val label = node()
        doReturn(windowOf(settings, mapOf("TiedSiren" to listOf(label)))).`when`(service).rootInActiveWindow

        assertTrue(AccessibilityService.foregroundWindowShows("TiedSiren", settingsPackages))
    }

    @Test
    fun `does not see a text the window in front does not show`() {
        doReturn(windowOf(settings, emptyMap())).`when`(service).rootInActiveWindow

        assertFalse(AccessibilityService.foregroundWindowShows("TiedSiren", settingsPackages))
    }

    @Test
    fun `does not read a window of a package it was not asked about`() {
        val root = windowOf("com.whatsapp", mapOf("TiedSiren" to listOf(node())))
        doReturn(root).`when`(service).rootInActiveWindow

        assertFalse(AccessibilityService.foregroundWindowShows("TiedSiren", settingsPackages))
        verify(root, never()).findAccessibilityNodeInfosByText(any())
    }

    @Test
    fun `gives back the window and every node it matched`() {
        val title = node()
        val entry = node()
        val root = windowOf(settings, mapOf("TiedSiren" to listOf(title, entry)))
        doReturn(root).`when`(service).rootInActiveWindow

        AccessibilityService.foregroundWindowShows("TiedSiren", settingsPackages)

        verify(title).recycle()
        verify(entry).recycle()
        verify(root).recycle()
    }

    @Test
    fun `still sees the text when a node was already reclaimed`() {
        val reclaimed = mock<AccessibilityNodeInfo> { on { recycle() } doThrow IllegalStateException() }
        doReturn(windowOf(settings, mapOf("TiedSiren" to listOf(reclaimed)))).`when`(service).rootInActiveWindow

        assertTrue(AccessibilityService.foregroundWindowShows("TiedSiren", settingsPackages))
    }

    @Test
    fun `sees nothing when the tree cannot be read`() {
        val root = mock<AccessibilityNodeInfo> {
            on { packageName } doReturn settings
            on { findAccessibilityNodeInfosByText(any()) } doThrow IllegalStateException()
        }
        doReturn(root).`when`(service).rootInActiveWindow

        assertFalse(AccessibilityService.foregroundWindowShows("TiedSiren", settingsPackages))
        verify(root).recycle()
    }

    @Test
    fun `does not look for a blank text`() {
        val root = windowOf(settings, mapOf("" to listOf(node())))
        doReturn(root).`when`(service).rootInActiveWindow

        assertFalse(AccessibilityService.foregroundWindowShows(" ", settingsPackages))
        verify(root, never()).findAccessibilityNodeInfosByText(any())
    }

    @Test
    fun `sees nothing when no window is in front`() {
        doReturn(null).`when`(service).rootInActiveWindow

        assertFalse(AccessibilityService.foregroundWindowShows("TiedSiren", settingsPackages))
    }

    @Test
    fun `sees nothing when the service is not bound`() {
        AccessibilityService.resetForTesting()

        assertFalse(AccessibilityService.foregroundWindowShows("TiedSiren", settingsPackages))
    }

    private fun node() = mock<AccessibilityNodeInfo>()

    private fun windowOf(packageName: String, nodesByText: Map<String, List<AccessibilityNodeInfo>>) =
        mock<AccessibilityNodeInfo> {
            on { this.packageName } doReturn packageName
            on { findAccessibilityNodeInfosByText(any()) } doReturn emptyList()
            nodesByText.forEach { (text, nodes) ->
                on { findAccessibilityNodeInfosByText(text) } doReturn nodes
            }
        }
}
