package expo.modules.accessibilityservice

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import java.util.Collections

class AccessibilityService : android.accessibilityservice.AccessibilityService() {

    // Callback interface for event listeners
    interface EventListener {
        fun onAppChanged(packageName: String, className: String, timestamp: Long)

        fun onUrlBarChanged(packageName: String, rawText: String, timestamp: Long) {}
    }

    companion object {
        private const val TAG = "AccessibilityService"

        val BROWSER_URL_BAR_VIEW_IDS: Map<String, String> = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.chrome.beta" to "com.chrome.beta:id/url_bar",
            "com.chrome.dev" to "com.chrome.dev:id/url_bar",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.brave.browser_beta" to "com.brave.browser_beta:id/url_bar",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "com.vivaldi.browser" to "com.vivaldi.browser:id/url_bar",
            "com.kiwibrowser.browser" to "com.kiwibrowser.browser:id/url_bar",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.opera.browser" to "com.opera.browser:id/url_field",
            "com.opera.browser.beta" to "com.opera.browser.beta:id/url_field",
            "com.opera.mini.native" to "com.opera.mini.native:id/url_field",
            "com.opera.gx" to "com.opera.gx:id/url_field",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox_beta" to "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view",
            "org.mozilla.fenix" to "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
            "org.mozilla.focus" to "org.mozilla.focus:id/mozac_browser_toolbar_url_view",
            "org.mozilla.klar" to "org.mozilla.klar:id/mozac_browser_toolbar_url_view",
            "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
            "com.UCMobile.intl" to "com.UCMobile.intl:id/address_bar",
            "com.mi.globalbrowser" to "com.mi.globalbrowser:id/url",
        )

        fun isSupportedBrowser(packageName: String?): Boolean =
            packageName != null && BROWSER_URL_BAR_VIEW_IDS.containsKey(packageName)

        fun resolveUrlBarText(packageName: String?, sourceViewId: String?, text: String?): String? {
            if (!isSupportedBrowser(packageName)) return null
            val expectedViewId = BROWSER_URL_BAR_VIEW_IDS[packageName] ?: return null
            if (sourceViewId != expectedViewId) return null
            val trimmed = text?.trim()
            if (trimmed.isNullOrEmpty()) return null
            return trimmed
        }

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
         * Pure matcher: is [expectedId] present in the system's bound-services id list?
         * Extracted so the id-matching contract is unit-testable without the framework.
         */
        internal fun matchesBoundService(boundServiceIds: List<String>, expectedId: String): Boolean =
            expectedId in boundServiceIds

        /**
         * Whether this accessibility service is actually BOUND and running — not merely
         * listed in Settings.Secure. The in-process signal ([isConnected] + [instance]) is
         * strongest (the service runs in this app's process); the system bound-services
         * view ([AccessibilityManager.getEnabledAccessibilityServiceList]) corroborates it
         * and is correct even after a process restart that cleared the in-process statics.
         *
         * Combined via OR: bound if EITHER says so. Distinguishes "enabled in settings but
         * not bound" (Restricted Settings / ECM on a sideloaded install, or an unbind after
         * process death) from genuinely running.
         */
        fun isServiceRunning(context: Context): Boolean {
            if (isConnected && instance != null) return true
            return try {
                val expectedId = "${context.packageName}/${AccessibilityService::class.java.canonicalName}"
                val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                val boundIds = manager
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .map { it.id }
                matchesBoundService(boundIds, expectedId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check if accessibility service is running: ${e.message}", e)
                false
            }
        }

        fun goBack(): Boolean =
            instance?.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            ) ?: false

        /**
         * Reset all state for testing purposes.
         */
        fun resetForTesting() {
            eventListeners.clear()
            isConnected = false
            instance = null
        }

        fun setInstanceForTesting(service: AccessibilityService?) {
            instance = service
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

        fun emitCurrentForegroundApp() {
            emitCurrentForegroundApp(attempt = 0)
        }

        private const val EMIT_MAX_ATTEMPTS = 3
        private const val EMIT_RETRY_DELAY_MS = 150L

        private fun emitCurrentForegroundApp(attempt: Int) {
            val service = instance
            if (service == null) {
                Log.w(TAG, "emitCurrentForegroundApp: service instance not available")
                return
            }

            val resolved = resolveForegroundPackage(service)
            if (resolved == null) {
                if (attempt + 1 < EMIT_MAX_ATTEMPTS) {
                    Log.w(TAG, "emitCurrentForegroundApp: no package (attempt ${attempt + 1}), retrying")
                    Handler(Looper.getMainLooper()).postDelayed(
                        { emitCurrentForegroundApp(attempt + 1) },
                        EMIT_RETRY_DELAY_MS
                    )
                } else {
                    Log.w(TAG, "emitCurrentForegroundApp: no package after $EMIT_MAX_ATTEMPTS attempts")
                }
                return
            }

            val timestamp = System.currentTimeMillis()
            Log.d(TAG, "emitCurrentForegroundApp: emitting ${resolved.first}")
            notifyListeners(resolved.first, resolved.second, timestamp)
        }

        /** Returns (packageName, className) of the active window, or null. */
        private fun resolveForegroundPackage(service: AccessibilityService): Pair<String, String>? {
            try {
                val root = service.rootInActiveWindow
                if (root != null) {
                    val pkg = root.packageName?.toString()
                    val cls = root.className?.toString() ?: "android.view.View"
                    root.recycle()
                    if (!pkg.isNullOrEmpty()) return pkg to cls
                }
                // Fallback: scan interactive windows for the active application window's root.
                for (window in service.windows) {
                    if (!window.isActive) continue
                    val wRoot = window.root ?: continue
                    val pkg = wRoot.packageName?.toString()
                    val cls = wRoot.className?.toString() ?: "android.view.View"
                    wRoot.recycle()
                    if (!pkg.isNullOrEmpty()) return pkg to cls
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveForegroundPackage failed: ${e.message}", e)
            }
            return null
        }

        /**
         * Notify all registered listeners of an app change event.
         * Creates a snapshot of listeners to avoid ConcurrentModificationException.
         * Each listener is called in a try/catch to ensure all listeners receive the event.
         */
        internal fun notifyListeners(packageName: String, className: String, timestamp: Long) {
            if (eventListeners.isEmpty()) {
                Log.w(TAG, "notifyListeners: no listeners registered, event dropped for $packageName")
            }
            forEachListener { it.onAppChanged(packageName, className, timestamp) }
        }

        internal fun notifyUrlBarListeners(packageName: String, rawText: String, timestamp: Long) {
            forEachListener { it.onUrlBarChanged(packageName, rawText, timestamp) }
        }

        private inline fun forEachListener(action: (EventListener) -> Unit) {
            val listeners = synchronized(eventListeners) { eventListeners.toList() }
            listeners.forEach { listener ->
                try {
                    action(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener: ${e.message}", e)
                }
            }
        }
    }

    private val urlBarGate = UrlBarEmissionGate()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleBrowserContentChanged(event)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        urlBarGate.forgetLastEmission()

        val packageName = event.packageName?.toString()
        val className = event.className?.toString()

        if (!packageName.isNullOrEmpty() && !className.isNullOrEmpty()) {
            val timestamp = System.currentTimeMillis()

            Log.d(TAG, "App changed: package=$packageName, class=$className")

            try {
                if (Sentry.isEnabled()) {
                    Sentry.addBreadcrumb(
                        Breadcrumb().apply {
                            category = "accessibility"
                            message = "event received"
                            setData("eventType", event.eventType)
                            setData("packageName", packageName)
                            setData("receivedAtMillis", timestamp)
                            setData("listenerCount", eventListeners.size)
                        }
                    )
                }
            } catch (_: Throwable) {
                // Sentry class may be absent at runtime (compileOnly, host app didn't bundle it)
            }

            notifyListeners(packageName, className, timestamp)
        }
    }

    private fun handleBrowserContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val expectedViewId = BROWSER_URL_BAR_VIEW_IDS[packageName] ?: return

        val source = event.source
        val resolved = try {
            resolveUrlBarText(packageName, source?.viewIdResourceName, source?.text?.toString())
                ?: queryUrlBarFromRootThrottled(expectedViewId)
        } finally {
            source?.recycle()
        }

        if (resolved != null && urlBarGate.tryClaimEmission(packageName, resolved)) {
            Log.d(TAG, "URL bar changed in $packageName")
            notifyUrlBarListeners(packageName, resolved, System.currentTimeMillis())
        }
    }

    private fun queryUrlBarFromRootThrottled(viewId: String): String? =
        if (urlBarGate.tryAcquireQuerySlot()) queryUrlBarFromRoot(viewId) else null

    private fun queryUrlBarFromRoot(viewId: String): String? {
        val root = rootInActiveWindow ?: return null
        var nodes: List<AccessibilityNodeInfo>? = null
        return try {
            nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            val text = nodes?.firstOrNull()?.text?.toString()?.trim()
            if (text.isNullOrEmpty()) null else text
        } catch (e: Exception) {
            Log.e(TAG, "queryUrlBarFromRoot failed: ${e.message}", e)
            null
        } finally {
            nodes?.forEach { it.recycle() }
            root.recycle()
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
