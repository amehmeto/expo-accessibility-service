package expo.modules.accessibilityservice

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class MyAccessibilityService : AccessibilityService() {

    // Callback interface for event listeners
    interface EventListener {
        fun onAppChanged(packageName: String, className: String, timestamp: Long)
    }

    companion object {
        private const val TAG = "MyAccessibilityService"

        // Static reference to the event listener (set by the module)
        @Volatile
        private var eventListener: EventListener? = null

        fun setEventListener(listener: EventListener?) {
            eventListener = listener
        }

        fun getEventListener(): EventListener? = eventListener
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only handle window state changed events (foreground app changes)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            val className = event.className?.toString()

            if (!packageName.isNullOrEmpty() && !className.isNullOrEmpty()) {
                val timestamp = System.currentTimeMillis()

                Log.d(TAG, "App changed: package=$packageName, class=$className, time=$timestamp")

                // Notify the listener (module)
                eventListener?.onAppChanged(packageName, className, timestamp)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.d(TAG, "Accessibility service unbound (permission revoked or service disabled)")
        // This is called when the accessibility service is disabled
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility service destroyed")
        // Clean up listener reference to avoid memory leaks
        eventListener = null
    }
}