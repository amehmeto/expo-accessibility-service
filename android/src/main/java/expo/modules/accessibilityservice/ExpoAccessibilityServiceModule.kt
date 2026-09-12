package expo.modules.accessibilityservice

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.provider.Settings
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log

class ExpoAccessibilityServiceModule : Module(), AccessibilityService.EventListener {

  // Configurable service class name - can be set by the app
  private var serviceClassName: String? = null
  private var serviceConnectedReceiver: BroadcastReceiver? = null

  companion object {
    private const val TAG = "ExpoAccessibilityModule"

    // Distinct codes, so JavaScript can branch on the cause instead of matching on a
    // message. One generic "ERROR" left every failure indistinguishable.
    internal const val ERROR_MANIFEST_UNREADABLE = "ERR_MANIFEST_UNREADABLE"
    internal const val ERROR_NO_SETTINGS_ACTIVITY = "ERR_NO_SETTINGS_ACTIVITY"
    internal const val ERROR_NO_APP_DETAILS_ACTIVITY = "ERR_NO_APP_DETAILS_ACTIVITY"
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoAccessibilityService")

    // Define events that can be emitted to JavaScript
    Events("onAccessibilityEvent", "onUrlBarChanged")

    // Register this module as an event listener when module is created
    OnCreate {
      Log.d(TAG, "Module created, registering as event listener")
      AccessibilityService.addEventListener(this@ExpoAccessibilityServiceModule)
      registerServiceConnectedReceiver()
    }

    // Unregister when module is destroyed to avoid memory leaks
    OnDestroy {
      Log.d(TAG, "Module destroyed, unregistering event listener")
      unregisterServiceConnectedReceiver()
      AccessibilityService.removeEventListener(this@ExpoAccessibilityServiceModule)
    }

    AsyncFunction("isEnabled") { promise: Promise ->
      val isEnabled = isAccessibilityServiceEnabled()
      promise.resolve(isEnabled)
    }

    // Asked about the SAME service ids as isEnabled(), so the two cannot disagree
    // about a custom service class configured through setServiceClassName().
    AsyncFunction("isServiceRunning") { promise: Promise ->
      promise.resolve(AccessibilityService.isServiceRunning(context, getServiceNamesToCheck()))
    }

    AsyncFunction("askPermission") { promise: Promise ->
      openAccessibilitySettings(promise)
    }

    AsyncFunction("openAppDetailsSettings") { promise: Promise ->
      openAppDetailsSettings(promise)
    }

    AsyncFunction("setServiceClassName") { className: String, promise: Promise ->
      serviceClassName = className
      promise.resolve()
    }

    // Rejects rather than answering "none found", so a caller can tell an app with no
    // declared service from a manifest it could not read.
    AsyncFunction("getDetectedServices") { promise: Promise ->
      try {
        promise.resolve(readAccessibilityServicesFromManifest())
      } catch (e: Exception) {
        promise.reject(ERROR_MANIFEST_UNREADABLE, "Could not read the app manifest: ${e.message}", e)
      }
    }

    AsyncFunction("emitCurrentForegroundApp") { promise: Promise ->
      Log.d(TAG, "emitCurrentForegroundApp called from JS")
      AccessibilityService.emitCurrentForegroundApp()
      promise.resolve()
    }

    AsyncFunction("goBack") { promise: Promise ->
      promise.resolve(AccessibilityService.goBack())
    }
  }

  // Implement EventListener interface
  override fun onAppChanged(packageName: String, className: String, timestamp: Long) {
    try {
      // Create event data map
      val eventData = mapOf(
        "packageName" to packageName,
        "className" to className,
        "timestamp" to timestamp
      )

      Log.d(TAG, "Emitting accessibility event: $eventData")

      // Emit event to JavaScript
      sendEvent("onAccessibilityEvent", eventData)
    } catch (e: Exception) {
      Log.e(TAG, "Error emitting accessibility event", e)
    }
  }

  /**
   * Forwards a URL-bar reading to JavaScript.
   *
   * The four-argument overload is the one the service calls, so [isEditing] crosses
   * the bridge with the text it belongs to. A consumer that acts on a match needs
   * both: the bar emits an event per keystroke and completes inline, so text alone
   * cannot separate a destination from a suggestion under the user's fingers.
   *
   * [rawText] keeps its native name across the bridge because it is not parsed. It
   * may be a URL, a search term, a partial word, or a browser's own placeholder.
   */
  override fun onUrlBarChanged(
    packageName: String,
    rawText: String,
    timestamp: Long,
    isEditing: Boolean,
  ) {
    try {
      val eventData = mapOf(
        "packageName" to packageName,
        "rawText" to rawText,
        "timestamp" to timestamp,
        "isEditing" to isEditing
      )

      Log.d(TAG, "Emitting URL bar event: package=$packageName, editing=$isEditing")

      sendEvent("onUrlBarChanged", eventData)
    } catch (e: Exception) {
      Log.e(TAG, "Error emitting URL bar event", e)
    }
  }

  private fun registerServiceConnectedReceiver() {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent?.action == AccessibilityService.ACTION_SERVICE_CONNECTED) {
          Log.d(TAG, "Service connected broadcast received")
          if (!AccessibilityService.hasListener(this@ExpoAccessibilityServiceModule)) {
            AccessibilityService.addEventListener(this@ExpoAccessibilityServiceModule)
            Log.d(TAG, "Re-registered as event listener after service restart")
          }
        }
      }
    }
    val filter = IntentFilter(AccessibilityService.ACTION_SERVICE_CONNECTED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      context.registerReceiver(receiver, filter)
    }
    serviceConnectedReceiver = receiver
    Log.d(TAG, "Registered service connected receiver")
  }

  private fun unregisterServiceConnectedReceiver() {
    serviceConnectedReceiver?.let {
      try {
        context.unregisterReceiver(it)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to unregister service connected receiver: ${e.message}")
      }
    }
    serviceConnectedReceiver = null
  }

  private val context
  get() = requireNotNull(appContext.reactContext)

  private fun isAccessibilityServiceEnabled(): Boolean {
    val serviceNames = getServiceNamesToCheck()
    return AccessibilityService.isAnyServiceEnabled(context, serviceNames)
  }

  private fun getServiceNamesToCheck(): List<String> {
    val packageName = context.packageName
    
    return when {
      // 1. If service class name is explicitly configured, use it
      serviceClassName != null -> {
        listOf("$packageName/$serviceClassName")
      }
      // 2. Try to auto-detect accessibility services from manifest
      else -> {
        val detectedServices = detectedServicesOrEmpty()
        if (detectedServices.isNotEmpty()) {
          detectedServices.map { "$packageName/$it" }
        } else {
          // 3. Fall back to default for backward compatibility
          listOf("$packageName/${packageName}.AccessibilityService")
        }
      }
    }
  }

  /** Reads the manifest. Throws when it cannot be read — the caller decides. */
  private fun readAccessibilityServicesFromManifest(): List<String> {
    val packageManager = context.packageManager
    val packageName = context.packageName
    val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SERVICES)

    return packageInfo.services
      ?.filter { isAccessibilityService(it) }
      ?.map { it.name }
      ?: emptyList()
  }

  /**
   * As [readAccessibilityServicesFromManifest], but degrading to an empty list.
   *
   * For the paths that must still answer something when the manifest is unreadable —
   * [getServiceNamesToCheck] falls back to the default service id. The failure is
   * logged rather than swallowed: an empty list used to be the only trace, which made
   * "no service declared" and "the read failed" the same answer.
   */
  private fun detectedServicesOrEmpty(): List<String> {
    return try {
      readAccessibilityServicesFromManifest()
    } catch (e: Exception) {
      Log.e(TAG, "Could not read accessibility services from the manifest: ${e.message}", e)
      emptyList()
    }
  }

  private fun isAccessibilityService(serviceInfo: ServiceInfo): Boolean =
    serviceInfo.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE"

  private fun openAccessibilitySettings(promise: Promise) {
    try {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
      promise.resolve()
    } catch (e: Exception) {
      promise.reject(
        ERROR_NO_SETTINGS_ACTIVITY,
        "Could not open accessibility settings: ${e.message}",
        e
      )
    }
  }

  private fun openAppDetailsSettings(promise: Promise) {
    try {
      val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null)
      ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
      promise.resolve()
    } catch (e: Exception) {
      promise.reject(
        ERROR_NO_APP_DETAILS_ACTIVITY,
        "Could not open app details settings: ${e.message}",
        e
      )
    }
  }
}
