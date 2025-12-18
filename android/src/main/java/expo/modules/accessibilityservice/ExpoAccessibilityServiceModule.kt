package expo.modules.accessibilityservice

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.accessibilityservice.AccessibilityService
import android.util.Log

class ExpoAccessibilityServiceModule : Module(), MyAccessibilityService.EventListener {

  // Configurable service class name - can be set by the app
  private var serviceClassName: String? = null

  companion object {
    private const val TAG = "ExpoAccessibilityModule"
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoAccessibilityService")

    // Define events that can be emitted to JavaScript
    Events("onAccessibilityEvent")

    // Register this module as an event listener when module is created
    OnCreate {
      Log.d(TAG, "Module created, registering as event listener")
      MyAccessibilityService.addEventListener(this@ExpoAccessibilityServiceModule)
    }

    // Unregister when module is destroyed to avoid memory leaks
    OnDestroy {
      Log.d(TAG, "Module destroyed, unregistering event listener")
      MyAccessibilityService.removeEventListener(this@ExpoAccessibilityServiceModule)
    }

    AsyncFunction("isEnabled") { promise: Promise ->
      val isEnabled = isAccessibilityServiceEnabled()
      promise.resolve(isEnabled)
    }

    AsyncFunction("askPermission") { promise: Promise ->
      openAccessibilitySettings(promise)
    }

    AsyncFunction("setServiceClassName") { className: String, promise: Promise ->
      serviceClassName = className
      promise.resolve()
    }

    AsyncFunction("getDetectedServices") { promise: Promise ->
      val services = getAccessibilityServicesFromManifest()
      promise.resolve(services)
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

  private val context
  get() = requireNotNull(appContext.reactContext)

  private fun isAccessibilityServiceEnabled(): Boolean {
    val serviceNames = getServiceNamesToCheck()
    
    val enabledServices = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    
    return if (TextUtils.isEmpty(enabledServices)) {
      false
    } else {
      serviceNames.any { serviceName ->
        enabledServices.contains(serviceName)
      }
    }
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
        val detectedServices = getAccessibilityServicesFromManifest()
        if (detectedServices.isNotEmpty()) {
          detectedServices.map { "$packageName/$it" }
        } else {
          // 3. Fall back to default for backward compatibility
          listOf("$packageName/${packageName}.MyAccessibilityService")
        }
      }
    }
  }

  private fun getAccessibilityServicesFromManifest(): List<String> {
    return try {
      val packageManager = context.packageManager
      val packageName = context.packageName
      val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SERVICES)
      
      packageInfo.services?.filter { serviceInfo ->
        isAccessibilityService(serviceInfo)
      }?.map { serviceInfo ->
        serviceInfo.name
      } ?: emptyList()
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun isAccessibilityService(serviceInfo: ServiceInfo): Boolean {
    return try {
      // Check if service has accessibility service permission
      serviceInfo.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE"
    } catch (e: Exception) {
      false
    }
  }

  private fun openAccessibilitySettings(promise: Promise) {
    try {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
      promise.resolve()
    } catch (e: Exception) {
      promise.reject("ERROR", "Could not open accessibility settings: ${e.message}", e)
    }
  }
}
