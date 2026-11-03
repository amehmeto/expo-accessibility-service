package expo.modules.accessibilityservice

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Where the work of an accessibility event runs. Android delivers events on the main
 * thread, where a node read is a binder call that waits on the browser for seconds.
 * The service's state stays on the main thread, so only the reads have to move.
 */
internal interface AccessibilityEventThreads {
    /** Android may reuse an event once its callback returns, so a later read needs a copy. */
    fun keep(event: AccessibilityEvent): AccessibilityEvent
    fun offMain(work: () -> Unit)
    fun onMain(work: () -> Unit)
}

private const val MAX_QUEUED_READS = 16
private const val WORKER_IDLE_SECONDS = 30L

/** One worker, so reads stay in order. A stuck browser costs old events, never the newest. */
internal fun createAccessibilityWorker(maxQueued: Int = MAX_QUEUED_READS): ExecutorService =
    ThreadPoolExecutor(
        1,
        1,
        WORKER_IDLE_SECONDS,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(maxQueued),
        { work -> Thread(work, "a11y-event-reads").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }

internal object BackgroundAccessibilityEventThreads : AccessibilityEventThreads {
    private val worker by lazy { createAccessibilityWorker() }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Suppress("DEPRECATION") // The copy constructor needs API 30.
    override fun keep(event: AccessibilityEvent): AccessibilityEvent = AccessibilityEvent.obtain(event)

    override fun offMain(work: () -> Unit) {
        worker.execute(work)
    }

    override fun onMain(work: () -> Unit) {
        mainHandler.post(work)
    }
}

/** Runs everything in place, so a test reads the service as one sequence. */
internal object InlineAccessibilityEventThreads : AccessibilityEventThreads {
    override fun keep(event: AccessibilityEvent): AccessibilityEvent = event
    override fun offMain(work: () -> Unit) = work()
    override fun onMain(work: () -> Unit) = work()
}
