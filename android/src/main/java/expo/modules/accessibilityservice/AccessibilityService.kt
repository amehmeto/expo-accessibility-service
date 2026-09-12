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
import androidx.annotation.VisibleForTesting
import io.sentry.Breadcrumb
import io.sentry.Sentry
import java.util.Collections

class AccessibilityService : android.accessibilityservice.AccessibilityService() {

    // Callback interface for event listeners
    interface EventListener {
        fun onAppChanged(packageName: String, className: String, timestamp: Long)

        fun onUrlBarChanged(packageName: String, rawText: String, timestamp: Long) {}

        /**
         * As [onUrlBarChanged], plus whether the URL bar held INPUT FOCUS when the
         * event fired — that is, whether the user was editing the address rather than
         * looking at the one the page is on.
         *
         * The distinction is not cosmetic for a consumer that acts on a match. The URL
         * bar emits an event per keystroke and completes "fa" into "facebook.com"
         * inline, so a consumer reading text alone cannot tell a destination from a
         * suggestion under the user's fingers, and can only guess from the shape and
         * timing of the sequence. [isEditing] answers it outright, and the transition
         * from true to false on the same text is the navigation itself.
         *
         * Read from the URL-bar node's `isFocused`. False is NOT proof the user is not
         * typing: a browser whose address bar is not a focusable text node, or whose
         * node is unreadable at that instant, reports false throughout. Treat true as
         * authoritative ("do not act") and keep a fallback for browsers that never
         * report true.
         *
         * The service calls THIS overload; the default forwards to the three-argument
         * one, so listeners written against that keep working unchanged.
         */
        fun onUrlBarChanged(
            packageName: String,
            rawText: String,
            timestamp: Long,
            isEditing: Boolean,
        ) {
            onUrlBarChanged(packageName, rawText, timestamp)
        }
    }

    companion object {
        private const val TAG = "AccessibilityService"

        /**
         * Address-bar field ids per browser package, WITHOUT the `package:id/` prefix.
         *
         * A list, not one id, because a browser renames or replaces its address bar
         * between versions and both spellings then live on real devices at the same
         * time. Firefox Focus is the case in point: it carries `display_url` from its
         * own toolbar and `mozac_browser_toolbar_url_view` since the Mozilla components
         * migration — pick one and the other half of the installed base goes unblocked,
         * silently, with every unit test still green. Candidates are tried in order.
         *
         * Corroborated against Bitwarden's browser map, which is maintained precisely
         * because their autofill breaks when an id is wrong:
         * https://github.com/bitwarden/mobile/blob/master/src/Android/Accessibility/AccessibilityHelpers.cs
         *
         * This table cannot be verified from a test — see [UrlBarBlindSpotDetector] for
         * how a wrong entry reports itself from the field instead.
         */
        val BROWSER_URL_BAR_FIELD_IDS: Map<String, List<String>> = mapOf(
            // Chromium family — all of them use url_bar.
            "com.android.chrome" to listOf("url_bar"),
            "com.chrome.beta" to listOf("url_bar"),
            "com.chrome.dev" to listOf("url_bar"),
            "com.chrome.canary" to listOf("url_bar"),
            "com.google.android.apps.chrome" to listOf("url_bar"),
            "com.brave.browser" to listOf("url_bar"),
            "com.brave.browser_beta" to listOf("url_bar"),
            "com.brave.browser_nightly" to listOf("url_bar"),
            "com.microsoft.emmx" to listOf("url_bar"),
            "com.vivaldi.browser" to listOf("url_bar"),
            "com.vivaldi.browser.snapshot" to listOf("url_bar"),
            "com.kiwibrowser.browser" to listOf("url_bar"),
            "com.ecosia.android" to listOf("url_bar"),
            "com.naver.whale" to listOf("url_bar"),
            "org.bromite.bromite" to listOf("url_bar"),
            "org.chromium.chrome" to listOf("url_bar"),
            "org.ungoogled.chromium.stable" to listOf("url_bar"),

            // Samsung Internet — its own id, and the reason tsbo#43 exists.
            "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
            "com.sec.android.app.sbrowser.beta" to listOf("location_bar_edit_text"),

            // Opera family.
            "com.opera.browser" to listOf("url_field"),
            "com.opera.browser.beta" to listOf("url_field"),
            "com.opera.mini.native" to listOf("url_field"),
            "com.opera.mini.native.beta" to listOf("url_field"),
            "com.opera.gx" to listOf("url_field"),
            "com.opera.touch" to listOf("addressbarEdit"),

            // Gecko family. url_bar_title is the older spelling and still shipping.
            "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            "org.mozilla.fenix" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            "org.mozilla.fennec_fdroid" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            "org.torproject.torbrowser" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            // Focus/Klar keep their own toolbar id on older builds.
            "org.mozilla.focus" to listOf("display_url", "mozac_browser_toolbar_url_view"),
            "org.mozilla.klar" to listOf("display_url", "mozac_browser_toolbar_url_view"),

            // Everything else.
            "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
            "com.UCMobile.intl" to listOf("address_bar"),
            "com.mi.globalbrowser" to listOf("url"),
            "com.android.browser" to listOf("url"),
            "com.amazon.cloud9" to listOf("url"),
        )

        /**
         * The fully-qualified candidates per package, built once.
         *
         * Once, because [handleBrowserContentChanged] runs on the accessibility thread
         * for every content event a browser fires — several a second — and rebuilding
         * a list of strings there was pure allocation on the hottest path we have.
         *
         * A field id containing ':' is taken as already qualified. A rebranded fork can
         * keep the upstream resource package (a Fenix fork still answering to
         * `org.mozilla.fenix:id/...`), and deriving the prefix from the running package
         * would make such an entry dead on arrival.
         */
        private val QUALIFIED_URL_BAR_IDS: Map<String, List<String>> =
            BROWSER_URL_BAR_FIELD_IDS.mapValues { (packageName, fieldIds) ->
                fieldIds.map { if (it.contains(':')) it else "$packageName:id/$it" }
            }

        /** The fully-qualified `package:id/field` candidates for [packageName]. */
        fun urlBarViewIds(packageName: String?): List<String> =
            QUALIFIED_URL_BAR_IDS[packageName] ?: emptyList()

        fun isSupportedBrowser(packageName: String?): Boolean =
            packageName != null && BROWSER_URL_BAR_FIELD_IDS.containsKey(packageName)

        fun resolveUrlBarText(packageName: String?, sourceViewId: String?, text: String?): String? {
            if (!isSupportedBrowser(packageName)) return null
            if (sourceViewId !in urlBarViewIds(packageName)) return null
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
         * Whether the Sentry breadcrumbs may carry the foreground app's package name.
         * Off, and the host app must turn it on deliberately.
         *
         * The list of apps a person opens is the most sensitive thing this service
         * sees. A breadcrumb rides along with the next captured event, so leaving this
         * on by default shipped that list to a third party on a decision the library
         * took alone — not the integrator, and not the user. Diagnosing "are events
         * arriving at all?" needs the count and the timing, which the breadcrumb still
         * carries either way.
         */
        @Volatile
        var includePackageNamesInTelemetry: Boolean = false

        /**
         * Reference to the current service instance, nulled on unbind/destroy.
         * Used by emitCurrentForegroundApp() to access rootInActiveWindow.
         */
        @Volatile
        private var instance: AccessibilityService? = null

        /**
         * Companion-scoped on purpose: Android unbinds and rebinds this service without
         * killing the process (the same reason [isConnected] and [instance] live here).
         * Per-instance state would reset the counters and the backoff on every rebind,
         * so "once, then quieter" would become "once per rebind".
         */
        private val blindSpotDetector = UrlBarBlindSpotDetector()

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
                isAnyServiceEnabled(context, listOf(defaultServiceId(context)))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check system accessibility state: ${e.message}", e)
                false
            }
        }

        /** The id of THIS library's service, `package/fully.qualified.ClassName`. */
        internal fun defaultServiceId(context: Context): String =
            "${context.packageName}/${AccessibilityService::class.java.canonicalName}"

        /**
         * Pure matcher: is [expectedId] present in the system's bound-services id list?
         * Extracted so the id-matching contract is unit-testable without the framework.
         */
        internal fun matchesBoundService(boundServiceIds: List<String>, expectedId: String): Boolean =
            expectedId in boundServiceIds

        /** As [matchesBoundService], for the several ids a configured service resolves to. */
        internal fun matchesAnyBoundService(
            boundServiceIds: List<String>,
            expectedIds: List<String>,
        ): Boolean = expectedIds.any { matchesBoundService(boundServiceIds, it) }

        /**
         * Whether the in-process signal is entitled to answer for [serviceIds].
         *
         * [isConnected] and [instance] are set by THIS library's service. They say
         * nothing about a custom service class the host app configured, so they may
         * only short-circuit when this library's service is among the ids asked about.
         * Pure so the entitlement rule is testable without a bound service.
         */
        internal fun inProcessSignalAnswersFor(
            isConnected: Boolean,
            hasInstance: Boolean,
            defaultServiceId: String,
            serviceIds: List<String>,
        ): Boolean = isConnected && hasInstance && defaultServiceId in serviceIds

        /** The class name to report when the event carries none. */
        internal const val FALLBACK_CLASS_NAME = "android.view.View"

        /**
         * The class name to report for an event, falling back when it carries none.
         *
         * A missing class name must not cost the consumer the app change itself: the
         * package is what it acts on. Pure because both call sites owe the same answer,
         * and they used to disagree — one substituted this fallback, the other dropped
         * the event.
         */
        internal fun resolveClassName(rawClassName: String?): String =
            rawClassName?.takeUnless { it.isEmpty() } ?: FALLBACK_CLASS_NAME

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
        fun isServiceRunning(context: Context): Boolean =
            isServiceRunning(context, listOf(defaultServiceId(context)))

        /**
         * As [isServiceRunning], asked about [serviceIds] rather than only this
         * library's own service.
         *
         * The caller resolves the ids, so a host app that configured a custom service
         * class through `setServiceClassName()` gets an answer about THAT service.
         * Asking the two questions with different ids is how `isEnabled()` and
         * `isServiceRunning()` came to contradict each other.
         *
         * The in-process shortcut is guarded for the same reason: [isConnected] and
         * [instance] speak for this library's service and nothing else, so they may
         * only answer when that service is among the ids asked about.
         */
        fun isServiceRunning(context: Context, serviceIds: List<String>): Boolean {
            val answeredInProcess = inProcessSignalAnswersFor(
                isConnected = isConnected,
                hasInstance = instance != null,
                defaultServiceId = defaultServiceId(context),
                serviceIds = serviceIds,
            )
            if (answeredInProcess) return true

            return try {
                val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                val boundIds = manager
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .map { it.id }
                matchesAnyBoundService(boundIds, serviceIds)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check if accessibility service is running: ${e.message}", e)
                false
            }
        }

        /**
         * Presses the system Back button on the user's behalf.
         *
         * Returns false when the service is not bound, so a caller can tell "refused"
         * from "not running".
         */
        fun goBack(): Boolean =
            instance?.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            ) ?: false

        /**
         * Reset all state for testing purposes.
         *
         * Public because the tests live in another source set, NOT because it is part
         * of the API. Calling it from an app drops every registered listener.
         */
        @VisibleForTesting
        fun resetForTesting() {
            eventListeners.clear()
            isConnected = false
            instance = null
        }

        @VisibleForTesting
        fun setInstanceForTesting(service: AccessibilityService?) {
            instance = service
        }

        /**
         * Set connection state for testing purposes.
         */
        @VisibleForTesting
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

        @Deprecated(
            message = "Returns an arbitrary listener: the backing set has no order. " +
                "Use getListenerCount() or hasListener() instead.",
            replaceWith = ReplaceWith("getListenerCount()")
        )
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
                    val cls = resolveClassName(root.className?.toString())
                    recycleQuietly(root)
                    if (!pkg.isNullOrEmpty()) return pkg to cls
                }
                // Fallback: scan interactive windows for the active application window's root.
                // Each window is recycled whatever this loop does with it — including on
                // the `continue` and the `return`, which is why the body is a try/finally
                // rather than a recycle at the end.
                for (window in service.windows) {
                    try {
                        if (!window.isActive) continue
                        val wRoot = window.root ?: continue
                        val pkg = wRoot.packageName?.toString()
                        val cls = resolveClassName(wRoot.className?.toString())
                        recycleQuietly(wRoot)
                        if (!pkg.isNullOrEmpty()) return pkg to cls
                    } finally {
                        recycleQuietly(window)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveForegroundPackage failed: ${e.message}", e)
            }
            return null
        }

        /**
         * Returns a pooled node or window to the framework, absorbing the throw from
         * one the framework already reclaimed.
         *
         * DEPRECATION: `recycle()` is deprecated from API 33 and does nothing there,
         * where the pooling it belongs to is gone. It still matters below 33, so these
         * calls stay until `minSdk` reaches 33 — then every one of them can go.
         */
        private fun recycleQuietly(node: AccessibilityNodeInfo) {
            try {
                node.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "recycling a node failed: ${e.message}")
            }
        }

        /** As [recycleQuietly], for a window. Same deprecation horizon. */
        private fun recycleQuietly(window: android.view.accessibility.AccessibilityWindowInfo) {
            try {
                window.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "recycling a window failed: ${e.message}")
            }
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

        /**
         * [isEditing] defaults to false so existing three-argument call sites stay
         * valid; the service always passes the real reading.
         */
        internal fun notifyUrlBarListeners(
            packageName: String,
            rawText: String,
            timestamp: Long,
            isEditing: Boolean = false,
        ) {
            forEachListener { it.onUrlBarChanged(packageName, rawText, timestamp, isEditing) }
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
        val className = resolveClassName(event.className?.toString())

        if (!packageName.isNullOrEmpty()) {
            val timestamp = System.currentTimeMillis()

            Log.d(TAG, "App changed: package=$packageName, class=$className")

            try {
                if (Sentry.isEnabled()) {
                    Sentry.addBreadcrumb(
                        Breadcrumb().apply {
                            category = "accessibility"
                            message = "event received"
                            setData("eventType", event.eventType)
                            setData("receivedAtMillis", timestamp)
                            setData("listenerCount", eventListeners.size)
                            if (includePackageNamesInTelemetry) {
                                setData("packageName", packageName)
                            }
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
        val viewIds = urlBarViewIds(packageName)
        if (viewIds.isEmpty()) return

        val source = event.source
        val lookup = try {
            resolveUrlBarText(packageName, source?.viewIdResourceName, source?.text?.toString())
                // The node that changed IS the URL bar, so its own focus state answers
                // whether the user is typing in it. Read before the recycle below.
                ?.let { UrlBarLookup.Read(UrlBarReading(it, source?.isFocused == true)) }
                ?: queryUrlBarFromRootThrottled(viewIds)
        } finally {
            source?.recycle()
        }

        reportIfUrlBarIdLooksStale(packageName, lookup)

        val reading = (lookup as? UrlBarLookup.Read)?.reading
        if (reading != null && urlBarGate.tryClaimEmission(packageName, reading.text, reading.isEditing)) {
            Log.d(TAG, "URL bar changed in $packageName (editing=${reading.isEditing})")
            notifyUrlBarListeners(
                packageName,
                reading.text,
                System.currentTimeMillis(),
                reading.isEditing,
            )
        }
    }

    /**
     * Says so when a browser we claim to support keeps failing to yield its address
     * bar — the only way a stale view id can surface, since it throws nothing and no
     * test can see it. See [UrlBarBlindSpotDetector].
     *
     * Only a lookup that actually happened and matched no node counts against the
     * browser. Feeding it every event would count the ones where the query slot was
     * refused (one tree query per 250ms, against events arriving many times a second)
     * and the ones where a node was found holding no text — and a browser animating a
     * blank new tab would be reported as broken.
     */
    private fun reportIfUrlBarIdLooksStale(packageName: String, lookup: UrlBarLookup) {
        val verdict = when (lookup) {
            UrlBarLookup.NotAttempted -> return
            UrlBarLookup.NoNodeMatched -> false
            UrlBarLookup.Empty, is UrlBarLookup.Read -> true
        }
        if (!blindSpotDetector.onLookup(packageName, foundNode = verdict)) return
        val ids = BROWSER_URL_BAR_FIELD_IDS[packageName]?.joinToString(", ").orEmpty()
        Log.w(TAG, "No URL bar found in $packageName after many events (tried: $ids)")
        // This one keeps the package name, unlike the per-event breadcrumb behind
        // [includePackageNamesInTelemetry]. It names a BROWSER we ship support for, it
        // fires at most a handful of times in an install's life, and the report is
        // worthless without knowing which browser broke.
        try {
            if (Sentry.isEnabled()) {
                Sentry.captureMessage(
                    "URL bar never found in a supported browser: $packageName " +
                        "(website blocking is off there; tried: $ids)",
                )
            }
        } catch (_: Throwable) {
            // Sentry is compileOnly — absent at runtime unless the host app bundles it.
        }
    }

    /**
     * Hands the active window to [UrlBarTreeReader], which owns the candidate loop and
     * the per-node recycling. The root is this method's to recycle, once, whatever the
     * reader does with what is inside it.
     *
     * The root is obtained BEFORE the pacing slot is taken. Taking the slot first spent
     * it on the attempts where there is no window to read — which happen during window
     * transitions, the exact moment a navigation lands — and then refused the next real
     * attempt for the rest of the interval.
     */
    private fun queryUrlBarFromRootThrottled(viewIds: List<String>): UrlBarLookup {
        val root = rootInActiveWindow ?: return UrlBarLookup.NotAttempted
        return try {
            if (!urlBarGate.tryAcquireQuerySlot()) {
                UrlBarLookup.NotAttempted
            } else {
                UrlBarTreeReader.read(nodeSourceOf(root), viewIds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryUrlBarFromRoot failed: ${e.message}", e)
            UrlBarLookup.NotAttempted
        } finally {
            recycleQuietly(root)
        }
    }

    /** Adapts one window's tree to the little the reader needs of it. */
    private fun nodeSourceOf(root: AccessibilityNodeInfo) = UrlBarNodeSource { viewId ->
        root.findAccessibilityNodeInfosByViewId(viewId)?.map { AndroidUrlBarNode(it) }
    }

    private class AndroidUrlBarNode(private val node: AccessibilityNodeInfo) : UrlBarNode {
        override val text: String? get() = node.text?.toString()
        override val isFocused: Boolean get() = node.isFocused
        override fun recycle() = node.recycle()
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
