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

class ExpoAccessibilityServiceModule : Module() {
  
  // Configurable service class name - can be set by the app
  private var serviceClassName: String? = null

  override fun definition() = ModuleDefinition {
    Name("ExpoAccessibilityService")

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
