package expo.modules.accessibilityservice

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.eq

class MyAccessibilityServiceTest {

    private lateinit var listener1: MyAccessibilityService.EventListener
    private lateinit var listener2: MyAccessibilityService.EventListener
    private lateinit var listener3: MyAccessibilityService.EventListener

    @Before
    fun setUp() {
        // Clear any existing listeners before each test
        @Suppress("DEPRECATION")
        MyAccessibilityService.setEventListener(null)

        listener1 = mock()
        listener2 = mock()
        listener3 = mock()
    }

    @After
    fun tearDown() {
        // Clean up after each test
        @Suppress("DEPRECATION")
        MyAccessibilityService.setEventListener(null)
    }

    @Test
    fun `addEventListener adds listener and returns true`() {
        val result = MyAccessibilityService.addEventListener(listener1)

        assertTrue("addEventListener should return true for new listener", result)
        assertEquals("getEventListener should return the added listener", listener1, MyAccessibilityService.getEventListener())
    }

    @Test
    fun `addEventListener returns false for duplicate`() {
        MyAccessibilityService.addEventListener(listener1)
        val result = MyAccessibilityService.addEventListener(listener1)

        assertFalse("addEventListener should return false for duplicate listener", result)
    }

    @Test
    fun `removeEventListener removes listener and returns true`() {
        MyAccessibilityService.addEventListener(listener1)
        val result = MyAccessibilityService.removeEventListener(listener1)

        assertTrue("removeEventListener should return true when listener was removed", result)
        assertNull("getEventListener should return null after removing the only listener", MyAccessibilityService.getEventListener())
    }

    @Test
    fun `removeEventListener returns false for non-existent`() {
        val result = MyAccessibilityService.removeEventListener(listener1)

        assertFalse("removeEventListener should return false for non-existent listener", result)
    }

    @Test
    fun `multiple listeners all receive events`() {
        MyAccessibilityService.addEventListener(listener1)
        MyAccessibilityService.addEventListener(listener2)
        MyAccessibilityService.addEventListener(listener3)

        val packageName = "com.example.app"
        val className = "MainActivity"
        val timestamp = 1234567890L

        // Call notifyListeners to simulate an accessibility event
        MyAccessibilityService.notifyListeners(packageName, className, timestamp)

        // Verify ALL listeners received the event
        verify(listener1).onAppChanged(eq(packageName), eq(className), eq(timestamp))
        verify(listener2).onAppChanged(eq(packageName), eq(className), eq(timestamp))
        verify(listener3).onAppChanged(eq(packageName), eq(className), eq(timestamp))
    }

    @Test
    fun `deprecated setEventListener clears and adds single listener`() {
        MyAccessibilityService.addEventListener(listener1)
        MyAccessibilityService.addEventListener(listener2)

        @Suppress("DEPRECATION")
        MyAccessibilityService.setEventListener(listener3)

        assertEquals("setEventListener should replace all listeners with the new one", listener3, MyAccessibilityService.getEventListener())

        // Verify listener1 and listener2 were removed by trying to remove them
        val removed1 = MyAccessibilityService.removeEventListener(listener1)
        val removed2 = MyAccessibilityService.removeEventListener(listener2)
        assertFalse("listener1 should have been removed by setEventListener", removed1)
        assertFalse("listener2 should have been removed by setEventListener", removed2)
    }

    @Test
    fun `deprecated setEventListener with null clears all`() {
        MyAccessibilityService.addEventListener(listener1)
        MyAccessibilityService.addEventListener(listener2)

        @Suppress("DEPRECATION")
        MyAccessibilityService.setEventListener(null)

        assertNull("getEventListener should return null after setEventListener(null)", MyAccessibilityService.getEventListener())
    }

    @Test
    fun `getEventListener returns first listener for backwards compat`() {
        MyAccessibilityService.addEventListener(listener1)
        MyAccessibilityService.addEventListener(listener2)

        val result = MyAccessibilityService.getEventListener()

        // Since it's a Set, we can't guarantee order, but we should get one of the listeners
        assertTrue("getEventListener should return one of the added listeners",
            result == listener1 || result == listener2)
    }

    @Test
    fun `exception in one listener does not affect others`() {
        // Create a listener that throws an exception
        val throwingListener = object : MyAccessibilityService.EventListener {
            override fun onAppChanged(packageName: String, className: String, timestamp: Long) {
                throw RuntimeException("Test exception")
            }
        }

        MyAccessibilityService.addEventListener(throwingListener)
        MyAccessibilityService.addEventListener(listener1)

        val packageName = "com.example.app"
        val className = "MainActivity"
        val timestamp = 1234567890L

        // This should NOT throw - exception in throwingListener should be caught
        MyAccessibilityService.notifyListeners(packageName, className, timestamp)

        // Verify listener1 still received the event despite throwingListener throwing
        verify(listener1).onAppChanged(eq(packageName), eq(className), eq(timestamp))
    }
}
