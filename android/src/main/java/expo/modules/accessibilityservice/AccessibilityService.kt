package expo.modules.accessibilityservice

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
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

        /**
         * Reference to the current service instance, nulled on unbind/destroy.
         * Used by emitCurrentForegroundApp() to access rootInActiveWindow.
         */
        @Volatile
        private var instance: AccessibilityService? = null

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
         * Check whether any of the given service IDs appear in the system's
         * enabled accessibility services list (Settings.Secure).
         *
         * Shared by [isServiceEnabledInSystem] and [ExpoAccessibilityServiceModule]
         * so that the matching logic (colon-delimited, exact match) is in one place.
         */
        internal fun isAnyServiceEnabled(context: Context, serviceIds: List<String>): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            if (enabledServices.isNullOrBlank()) return false

            val enabledSet = enabledServices.split(":").map { it.trim() }.toSet()
            return serviceIds.any { it in enabledSet }
        }

        /**
         * Check if this accessibility service is enabled in Android system settings.
         * Unlike [isConnected], this checks the actual system state and survives
         * process death — it reads from Settings.Secure which is persisted by Android.
         *
         * Use this when you need ground-truth after a process restart where
         * the in-memory [isConnected] flag was lost.
         *
         * Returns `false` if the check itself fails (e.g. SecurityException).
         */
        fun isServiceEnabledInSystem(context: Context): Boolean {
            return try {
                val serviceId = "${context.packageName}/${AccessibilityService::class.java.canonicalName}"
                isAnyServiceEnabled(context, listOf(serviceId))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check system accessibility state: ${e.message}", e)
                false
            }
        }

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
         * Emit the current foreground app as a synthetic accessibility event.
         * Uses rootInActiveWindow to determine what app is currently on screen.
         * This is useful when the service starts while an app is already in the foreground
         * (no TYPE_WINDOW_STATE_CHANGED event fires for already-visible apps).
         */
        fun emitCurrentForegroundApp() {
            val service = instance
            if (service == null) {
                Log.w(TAG, "emitCurrentForegroundApp: service instance not available")
                return
            }

            try {
                val rootNode = service.rootInActiveWindow
                if (rootNode == null) {
                    Log.w(TAG, "emitCurrentForegroundApp: rootInActiveWindow is null")
                    return
                }

                val packageName = rootNode.packageName?.toString()
                val className = rootNode.className?.toString() ?: "android.view.View"
                // recycle() is deprecated on API 34+ (no-op); safe to call on older APIs
                rootNode.recycle()

                if (packageName.isNullOrEmpty()) {
                    Log.w(TAG, "emitCurrentForegroundApp: no package name from root node")
                    return
                }

                val timestamp = System.currentTimeMillis()
                Log.d(TAG, "emitCurrentForegroundApp: emitting $packageName")
                notifyListeners(packageName, className, timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "emitCurrentForegroundApp failed: ${e.message}", e)
            }
        }

        /**
         * Notify all registered listeners of an app change event.
         * Creates a snapshot of listeners to avoid ConcurrentModificationException.
         * Each listener is called in a try/catch to ensure all listeners receive the event.
         */
        internal fun notifyListeners(packageName: String, className: String, timestamp: Long) {
            // Create snapshot to avoid ConcurrentModificationException during iteration
            val listeners = synchronized(eventListeners) { eventListeners.toList() }
            if (listeners.isEmpty()) {
                Log.w(TAG, "notifyListeners: no listeners registered, event dropped for $packageName")
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

                try {
                    val breadcrumb = Breadcrumb().apply {
                        category = "accessibility"
                        message = "event received"
                        setData("eventType", event.eventType)
                        setData("packageName", packageName)
                        setData("receivedAtMillis", timestamp)
                        setData("listenerCount", eventListeners.size)
                    }
                    Sentry.addBreadcrumb(breadcrumb)
                } catch (_: Throwable) {
                    // Sentry may not be initialized (standalone module, tests)
                }

                notifyListeners(packageName, className, timestamp)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    public override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        instance = this
        Log.d(TAG, "Accessibility service connected, listeners=${eventListeners.size}")

        // Broadcast so that ExpoAccessibilityServiceModule (or any listener) can
        // re-register after Android kills and restarts this service.
        val intent = Intent(ACTION_SERVICE_CONNECTED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Service connected broadcast sent")
    }

    public override fun onUnbind(intent: android.content.Intent?): Boolean {
        isConnected = false
        instance = null
        Log.d(TAG, "Accessibility service unbound (permission revoked or service disabled)")
        return super.onUnbind(intent)
    }

    public override fun onDestroy() {
        isConnected = false
        instance = null
        super.onDestroy()
        Log.d(TAG, "Accessibility service destroyed, listeners=${eventListeners.size}")
    }
}
