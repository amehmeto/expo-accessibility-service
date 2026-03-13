package expo.modules.accessibilityservice

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import java.util.Collections

class AccessibilityService : android.accessibilityservice.AccessibilityService() {

    // Callback interface for event listeners
    interface EventListener {
        fun onAppChanged(packageName: String, className: String, timestamp: Long)
    }

    companion object {
        private const val TAG = "AccessibilityService"

        /**
         * Broadcast action sent when the accessibility service (re)connects.
         * Listeners can register a BroadcastReceiver for this action to re-register
         * themselves after Android kills and restarts the service.
         */
        const val ACTION_SERVICE_CONNECTED = "expo.modules.accessibilityservice.SERVICE_CONNECTED"

        /**
         * Tracks whether the accessibility service is currently connected.
         * Reset on process restart (static state is lost).
         */
        @Volatile
        var isConnected: Boolean = false
            private set

        // Thread-safe set of listeners (replaces single eventListener)
        private val eventListeners = Collections.synchronizedSet(mutableSetOf<EventListener>())

        fun addEventListener(listener: EventListener): Boolean {
            val added = eventListeners.add(listener)
            Log.d(TAG, "addEventListener: added=$added, total=${eventListeners.size}")
            return added
        }

        fun removeEventListener(listener: EventListener): Boolean {
            val removed = eventListeners.remove(listener)
            Log.d(TAG, "removeEventListener: removed=$removed, total=${eventListeners.size}")
            return removed
        }

        /**
         * Check if a specific listener is currently registered.
         */
        fun hasListener(listener: EventListener): Boolean = eventListeners.contains(listener)

        /**
         * Return the number of currently registered listeners.
         */
        fun getListenerCount(): Int = eventListeners.size

        /**
         * Reset all state for testing purposes.
         */
        fun resetForTesting() {
            eventListeners.clear()
            isConnected = false
        }

        /**
         * Set connection state for testing purposes.
         */
        fun setConnectedForTesting(connected: Boolean) {
            isConnected = connected
        }

        @Deprecated(
            message = "Use addEventListener/removeEventListener for multiple listener support",
            replaceWith = ReplaceWith("addEventListener(listener)")
        )
        fun setEventListener(listener: EventListener?) {
            eventListeners.clear()
            listener?.let { eventListeners.add(it) }
        }

        fun getEventListener(): EventListener? = eventListeners.firstOrNull()

        /**
         * Notify all registered listeners of an app change event.
         * Creates a snapshot of listeners to avoid ConcurrentModificationException.
         * Each listener is called in a try/catch to ensure all listeners receive the event.
         */
        internal fun notifyListeners(packageName: String, className: String, timestamp: Long) {
            // Create snapshot to avoid ConcurrentModificationException during iteration
            val listeners = synchronized(eventListeners) { eventListeners.toList() }
            if (listeners.isEmpty()) {
                Log.d(TAG, "notifyListeners: no listeners registered, event dropped for $packageName")
            }
            listeners.forEach { listener ->
                try {
                    listener.onAppChanged(packageName, className, timestamp)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener: ${e.message}", e)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only handle window state changed events (foreground app changes)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            val className = event.className?.toString()

            if (!packageName.isNullOrEmpty() && !className.isNullOrEmpty()) {
                val timestamp = System.currentTimeMillis()

                Log.d(TAG, "App changed: package=$packageName, class=$className")

                notifyListeners(packageName, className, timestamp)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        Log.d(TAG, "Accessibility service connected, listeners=${eventListeners.size}")

        // Broadcast so that ExpoAccessibilityServiceModule (or any listener) can
        // re-register after Android kills and restarts this service.
        val intent = Intent(ACTION_SERVICE_CONNECTED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Service connected broadcast sent")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isConnected = false
        Log.d(TAG, "Accessibility service unbound (permission revoked or service disabled)")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        super.onDestroy()
        Log.d(TAG, "Accessibility service destroyed, listeners=${eventListeners.size}")
    }
}
