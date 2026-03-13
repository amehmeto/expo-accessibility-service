package expo.modules.accessibilityservice

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.eq

class AccessibilityServiceTest {

    private lateinit var listener1: AccessibilityService.EventListener
    private lateinit var listener2: AccessibilityService.EventListener
    private lateinit var listener3: AccessibilityService.EventListener

    @Before
    fun setUp() {
        // Clear any existing listeners before each test
        AccessibilityService.resetForTesting()

        listener1 = mock()
        listener2 = mock()
        listener3 = mock()
    }

    @After
    fun tearDown() {
        // Clean up after each test
        AccessibilityService.resetForTesting()
    }

    @Test
    fun `addEventListener adds listener and returns true`() {
        val result = AccessibilityService.addEventListener(listener1)

        assertTrue("addEventListener should return true for new listener", result)
        assertEquals("getEventListener should return the added listener", listener1, AccessibilityService.getEventListener())
    }

    @Test
    fun `addEventListener returns false for duplicate`() {
        AccessibilityService.addEventListener(listener1)
        val result = AccessibilityService.addEventListener(listener1)

        assertFalse("addEventListener should return false for duplicate listener", result)
    }

    @Test
    fun `removeEventListener removes listener and returns true`() {
        AccessibilityService.addEventListener(listener1)
        val result = AccessibilityService.removeEventListener(listener1)

        assertTrue("removeEventListener should return true when listener was removed", result)
        assertNull("getEventListener should return null after removing the only listener", AccessibilityService.getEventListener())
    }

    @Test
    fun `removeEventListener returns false for non-existent`() {
        val result = AccessibilityService.removeEventListener(listener1)

        assertFalse("removeEventListener should return false for non-existent listener", result)
    }

    @Test
    fun `multiple listeners all receive events`() {
        AccessibilityService.addEventListener(listener1)
        AccessibilityService.addEventListener(listener2)
        AccessibilityService.addEventListener(listener3)

        val packageName = "com.example.app"
        val className = "MainActivity"
        val timestamp = 1234567890L

        // Call notifyListeners to simulate an accessibility event
        AccessibilityService.notifyListeners(packageName, className, timestamp)

        // Verify ALL listeners received the event
        verify(listener1).onAppChanged(eq(packageName), eq(className), eq(timestamp))
        verify(listener2).onAppChanged(eq(packageName), eq(className), eq(timestamp))
        verify(listener3).onAppChanged(eq(packageName), eq(className), eq(timestamp))
    }

    @Test
    fun `deprecated setEventListener clears and adds single listener`() {
        AccessibilityService.addEventListener(listener1)
        AccessibilityService.addEventListener(listener2)

        @Suppress("DEPRECATION")
        AccessibilityService.setEventListener(listener3)

        assertEquals("setEventListener should replace all listeners with the new one", listener3, AccessibilityService.getEventListener())

        // Verify listener1 and listener2 were removed by trying to remove them
        val removed1 = AccessibilityService.removeEventListener(listener1)
        val removed2 = AccessibilityService.removeEventListener(listener2)
        assertFalse("listener1 should have been removed by setEventListener", removed1)
        assertFalse("listener2 should have been removed by setEventListener", removed2)
    }

    @Test
    fun `deprecated setEventListener with null clears all`() {
        AccessibilityService.addEventListener(listener1)
        AccessibilityService.addEventListener(listener2)

        @Suppress("DEPRECATION")
        AccessibilityService.setEventListener(null)

        assertNull("getEventListener should return null after setEventListener(null)", AccessibilityService.getEventListener())
    }

    @Test
    fun `getEventListener returns first listener for backwards compat`() {
        AccessibilityService.addEventListener(listener1)
        AccessibilityService.addEventListener(listener2)

        val result = AccessibilityService.getEventListener()

        // Since it's a Set, we can't guarantee order, but we should get one of the listeners
        assertTrue("getEventListener should return one of the added listeners",
            result == listener1 || result == listener2)
    }

    @Test
    fun `hasListener returns true for registered listener`() {
        AccessibilityService.addEventListener(listener1)

        assertTrue("hasListener should return true for registered listener", AccessibilityService.hasListener(listener1))
        assertFalse("hasListener should return false for unregistered listener", AccessibilityService.hasListener(listener2))
    }

    @Test
    fun `hasListener returns false after removal`() {
        AccessibilityService.addEventListener(listener1)
        AccessibilityService.removeEventListener(listener1)

        assertFalse("hasListener should return false after removal", AccessibilityService.hasListener(listener1))
    }

    @Test
    fun `getListenerCount tracks registered listeners`() {
        assertEquals("getListenerCount should be 0 initially", 0, AccessibilityService.getListenerCount())

        AccessibilityService.addEventListener(listener1)
        assertEquals("getListenerCount should be 1 after adding one", 1, AccessibilityService.getListenerCount())

        AccessibilityService.addEventListener(listener2)
        assertEquals("getListenerCount should be 2 after adding two", 2, AccessibilityService.getListenerCount())

        AccessibilityService.removeEventListener(listener1)
        assertEquals("getListenerCount should be 1 after removing one", 1, AccessibilityService.getListenerCount())
    }

    @Test
    fun `isConnected is false by default`() {
        assertFalse("isConnected should be false by default", AccessibilityService.isConnected)
    }

    @Test
    fun `resetForTesting clears listeners and resets isConnected`() {
        AccessibilityService.addEventListener(listener1)
        AccessibilityService.addEventListener(listener2)
        assertEquals("should have 2 listeners", 2, AccessibilityService.getListenerCount())

        AccessibilityService.resetForTesting()

        assertEquals("listeners should be cleared", 0, AccessibilityService.getListenerCount())
        assertFalse("isConnected should be false after reset", AccessibilityService.isConnected)
    }

    @Test
    fun `setConnectedForTesting sets isConnected`() {
        assertFalse("isConnected should be false initially", AccessibilityService.isConnected)

        AccessibilityService.setConnectedForTesting(true)
        assertTrue("isConnected should be true after setConnectedForTesting(true)", AccessibilityService.isConnected)

        AccessibilityService.setConnectedForTesting(false)
        assertFalse("isConnected should be false after setConnectedForTesting(false)", AccessibilityService.isConnected)
    }

    @Test
    fun `exception in one listener does not affect others`() {
        // Create a listener that throws an exception
        val throwingListener = object : AccessibilityService.EventListener {
            override fun onAppChanged(packageName: String, className: String, timestamp: Long) {
                throw RuntimeException("Test exception")
            }
        }

        AccessibilityService.addEventListener(throwingListener)
        AccessibilityService.addEventListener(listener1)

        val packageName = "com.example.app"
        val className = "MainActivity"
        val timestamp = 1234567890L

        // This should NOT throw - exception in throwingListener should be caught
        AccessibilityService.notifyListeners(packageName, className, timestamp)

        // Verify listener1 still received the event despite throwingListener throwing
        verify(listener1).onAppChanged(eq(packageName), eq(className), eq(timestamp))
    }
}
