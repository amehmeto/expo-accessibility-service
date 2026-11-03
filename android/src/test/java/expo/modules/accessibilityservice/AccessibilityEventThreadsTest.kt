package expo.modules.accessibilityservice

import android.view.accessibility.AccessibilityEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AccessibilityEventThreadsTest {
    private lateinit var service: AccessibilityService
    private val threads = QueuedThreads()

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
        AccessibilityService.eventThreads = threads
        service = spy(Robolectric.buildService(AccessibilityService::class.java).create().get())
        doReturn(null).`when`(service).rootInActiveWindow
    }

    @After
    fun tearDown() {
        AccessibilityService.resetForTesting()
    }

    @Test
    fun `a browser content event reads no node on the thread that delivered it`() {
        val event = contentEvent("com.android.chrome")

        service.onAccessibilityEvent(event)

        verify(event, never()).source
        verify(service, never()).rootInActiveWindow

        threads.runOffMainWork()

        verify(event, times(1)).source
        verify(service, times(1)).rootInActiveWindow
    }

    @Test
    fun `a content event from another app costs no copy and no read`() {
        val event = contentEvent("com.example.notes")

        service.onAccessibilityEvent(event)

        assertEquals(0, threads.kept)
        assertEquals(0, threads.queued)
    }

    @Test
    fun `a stuck worker drops the oldest reads and keeps the newest`() {
        val worker = createAccessibilityWorker(maxQueued = 2)
        val release = CountDownLatch(1)
        val done = CountDownLatch(2)
        val ran = Collections.synchronizedList(mutableListOf<Int>())

        worker.execute { release.await() }
        for (read in 1..4) {
            worker.execute {
                ran.add(read)
                done.countDown()
            }
        }
        release.countDown()

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(3, 4), ran)
        worker.shutdownNow()
    }

    private fun contentEvent(name: String) = mock<AccessibilityEvent> {
        on { eventType } doReturn AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        on { packageName } doReturn name
    }

    private class QueuedThreads : AccessibilityEventThreads {
        private val offMainWork = mutableListOf<() -> Unit>()
        var kept = 0
        val queued get() = offMainWork.size

        override fun keep(event: AccessibilityEvent): AccessibilityEvent {
            kept++
            return event
        }

        override fun offMain(work: () -> Unit) {
            offMainWork += work
        }

        override fun onMain(work: () -> Unit) = work()

        fun runOffMainWork() {
            offMainWork.toList().forEach { it() }
            offMainWork.clear()
        }
    }
}
