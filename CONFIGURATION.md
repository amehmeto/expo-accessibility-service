# Accessibility Service Configuration

This document explains how to configure and customize the accessibility service detection in expo-accessibility-service.

## Overview

By default, the module looks for a service named `MyAccessibilityService` in your app's package. However, you can now configure custom service names for maximum flexibility.

## Configuration Methods

### 1. Explicit Configuration (Recommended)

Configure a specific accessibility service class name:

```typescript
import { setServiceClassName, isEnabled } from 'expo-accessibility-service'

// Configure your custom service
await setServiceClassName('com.example.myapp.CustomAccessibilityService')

// Now isEnabled() will check for your custom service
const enabled = await isEnabled()
```

### 2. Auto-Detection

The module can automatically detect accessibility services from your AndroidManifest.xml:

```typescript
import { getDetectedServices, isEnabled } from 'expo-accessibility-service'

// Get all detected accessibility services
const services = await getDetectedServices()
console.log('Detected services:', services)

// If you don't call setServiceClassName(), the module will automatically
// use any detected services for isEnabled() checks
const enabled = await isEnabled()
```

### 3. Backward Compatibility (Default)

If no configuration is provided and no services are auto-detected, the module falls back to the original behavior:

```typescript
// Will check for: {packageName}/{packageName}.MyAccessibilityService
const enabled = await isEnabled()
```

## Configuration Priority

The module follows this priority order:

1. **Explicit Configuration**: If `setServiceClassName()` was called
2. **Auto-Detection**: Services detected from AndroidManifest.xml
3. **Default Fallback**: `{packageName}.MyAccessibilityService`

## Example Usage Patterns

### Pattern 1: Single Custom Service

```typescript
import {
  setServiceClassName,
  isEnabled,
  askPermission,
} from 'expo-accessibility-service'

export class AccessibilityManager {
  async initialize() {
    // Configure your custom service name
    await setServiceClassName('com.mycompany.app.MyCustomAccessibilityService')
  }

  async checkStatus() {
    return await isEnabled()
  }

  async requestPermission() {
    if (!(await this.checkStatus())) {
      await askPermission()
    }
  }
}
```

### Pattern 2: Multiple Services with Auto-Detection

```typescript
import {
  getDetectedServices,
  setServiceClassName,
  isEnabled,
} from 'expo-accessibility-service'

async function configureAccessibilityService() {
  // Get all available services
  const availableServices = await getDetectedServices()

  if (availableServices.length === 0) {
    console.log('No accessibility services found in manifest')
    return
  }

  // Configure the first (or preferred) service
  const preferredService = availableServices[0]
  await setServiceClassName(preferredService)

  console.log(`Configured service: ${preferredService}`)

  // Check if it's enabled
  const enabled = await isEnabled()
  console.log(`Service enabled: ${enabled}`)
}
```

### Pattern 3: Dynamic Service Selection

```typescript
import {
  getDetectedServices,
  setServiceClassName,
  isEnabled,
} from 'expo-accessibility-service'

async function selectAccessibilityService(preferredServiceName?: string) {
  const availableServices = await getDetectedServices()

  let selectedService: string

  if (
    preferredServiceName &&
    availableServices.includes(preferredServiceName)
  ) {
    // Use preferred service if available
    selectedService = preferredServiceName
  } else if (availableServices.length > 0) {
    // Use first available service
    selectedService = availableServices[0]
  } else {
    // Fall back to default
    console.log('Using default service name')
    return await isEnabled() // Will use default behavior
  }

  await setServiceClassName(selectedService)
  return await isEnabled()
}
```

## AndroidManifest.xml Requirements

For auto-detection to work, your accessibility service must be properly declared in AndroidManifest.xml:

```xml
<service
    android:name=".MyCustomAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

The module detects services by looking for the `android.permission.BIND_ACCESSIBILITY_SERVICE` permission.

## What the user reads before granting

Android shows two strings on the Accessibility settings screen, where your user decides whether to grant the most sensitive permission on the platform:

- `android:label` on the `<service>` — the name in the service list
- `android:description` in the `<accessibility-service>` config — the explanation on the detail screen

This module ships defaults for both, so the screen is never blank:

```xml
<string name="expo_accessibility_service_label">Foreground app detection</string>
<string name="expo_accessibility_service_description">Reports which app is in the foreground and, in supported browsers, the text shown in the address bar. It reads no other screen content.</string>
```

**Override them.** The defaults describe the module; your user needs to read about _your_ app. Declare strings with the same names in your own `res/values/strings.xml` and the resource merge picks yours:

```xml
<!-- android/app/src/main/res/values/strings.xml -->
<resources>
    <string name="expo_accessibility_service_label">Focus guard</string>
    <string name="expo_accessibility_service_description">Lets Focus Guard see which app you open, so it can block the ones you asked it to block. It reads no other screen content and nothing leaves your phone.</string>
</resources>
```

Be specific and be true. A vague description is both the main reason users back out of the grant, and a Google Play policy problem for apps that use the accessibility API.

### Optional: a settings screen of your own

Android shows a settings button on the service's detail screen when the config declares one. Without it, the user has no route from the accessibility settings back into your app. To add one, override `res/xml/accessibility_service_config.xml` in your app and add `android:settingsActivity="com.yourapp.YourSettingsActivity"`.

## Telemetry

If your app bundles Sentry, this module attaches a breadcrumb on every foreground app change. The breadcrumb carries the event type, the timestamp and the listener count — enough to diagnose "are events arriving at all?".

It does **not** carry the package name of the app the user opened. The list of apps a person opens is the most sensitive thing this service sees, and a breadcrumb rides along with the next captured event, so that decision is yours rather than this library's:

```kotlin
// In your Application.onCreate(), if and only if you mean it.
AccessibilityService.includePackageNamesInTelemetry = true
```

One report is exempt and always names its package: when a browser this module claims to support stops yielding its address bar, the blind-spot detector reports which browser broke. It fires at most a handful of times in an install's life, it names a browser rather than a usage pattern, and the report is worthless without it.

## Migration Guide

### From Hard-Coded Service Names

**Before:**

```kotlin
// Hard-coded in Android module
val serviceName = "$packageName/${packageName}.MyAccessibilityService"
```

**After:**

```typescript
// Configurable via TypeScript
await setServiceClassName('com.myapp.CustomAccessibilityService')
```

### Updating Existing Code

1. **No changes required**: Existing code continues to work with default behavior
2. **Optional enhancement**: Add configuration for custom service names
3. **Improved flexibility**: Use auto-detection for dynamic service discovery

```typescript
// Existing code - still works
const enabled = await isEnabled()

// Enhanced code - configurable
await setServiceClassName('com.myapp.MyService')
const enabled = await isEnabled()
```

## Error Handling

```typescript
import {
  setServiceClassName,
  getDetectedServices,
  isEnabled,
} from 'expo-accessibility-service'

try {
  // Configure service
  await setServiceClassName('com.example.MyService')

  // Check status
  const enabled = await isEnabled()
  console.log(`Service enabled: ${enabled}`)
} catch (error) {
  console.error('Configuration error:', error)

  // Fall back to auto-detection
  try {
    const services = await getDetectedServices()
    if (services.length > 0) {
      await setServiceClassName(services[0])
    }
  } catch (detectionError) {
    console.error('Auto-detection failed:', detectionError)
    // Will use default behavior
  }
}
```

## Best Practices

1. **Call `setServiceClassName()` early**: Configure service name during app initialization
2. **Handle multiple services**: Use `getDetectedServices()` for dynamic environments
3. **Provide fallbacks**: Always handle cases where configuration fails
4. **Test thoroughly**: Verify behavior with different Android versions and configurations
5. **Document service names**: Keep track of your accessibility service class names

## API Reference

### `setServiceClassName(className: string): Promise<void>`

Configures the accessibility service class name to check for.

- **className**: Fully qualified class name (e.g., `'com.example.MyService'`)
- **Returns**: Promise that resolves when configuration is set

### `getDetectedServices(): Promise<string[]>`

Gets accessibility services detected in the app's manifest.

- **Returns**: Promise that resolves to array of service class names

### `isEnabled(): Promise<boolean>`

Checks if the configured accessibility service is enabled.

- **Returns**: Promise that resolves to boolean indicating service status
- **Behavior**: Uses configured service name, auto-detected services, or default fallback

### `askPermission(): Promise<void>`

Opens Android accessibility settings for the user to enable services.

- **Returns**: Promise that resolves when settings are opened
