package expo.modules.accessibilityservice

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.content.Intent

class ExpoAccessibilityServiceModule : Module() {

  override fun definition() = ModuleDefinition {
    Name("ExpoAccessibilityService")

    AsyncFunction("isEnabled") { promise: Promise ->
      val isEnabled = isAccessibilityServiceEnabled()
      promise.resolve(isEnabled)
    }

    AsyncFunction("askPermission") { promise: Promise ->
      openAccessibilitySettings(promise)
    }
  }

  private val context
  get() = requireNotNull(appContext.reactContext)

  private fun isAccessibilityServiceEnabled(): Boolean {
    val packageName = context.packageName
    val serviceName = "$packageName/${packageName}.MyAccessibilityService"
    
    val enabledServices = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    
    return if (TextUtils.isEmpty(enabledServices)) {
      false
    } else {
      enabledServices.contains(serviceName)
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
