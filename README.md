# Expo Accessibility Service

An Expo module for managing Android accessibility service permissions and monitoring foreground app changes. This module provides a simple way to check if accessibility services are enabled, request permissions from users, and receive real-time events when users switch between apps.

## Features

- ✅ Check if accessibility service is enabled
- ✅ Request accessibility service permissions
- ✅ Monitor foreground app changes in real-time
- ✅ Read the address bar of supported browsers, and know when the user is typing in it
- ✅ Event-based architecture with subscription management
- ✅ Support for multiple simultaneous listeners
- ✅ Cross-platform support (Android only)
- ✅ TypeScript support
- ✅ Built and tested against Expo SDK 57 (React Native 0.86)

## Installation

```bash
npm install expo-accessibility-service
```

### Configuration

Add the module to your `app.json` or `app.config.js`:

```json
{
  "expo": {
    "plugins": ["expo-accessibility-service"]
  }
}
```

## Usage

### Basic Permission Checking

```typescript
import * as AccessibilityService from 'expo-accessibility-service'
import { useCallback, useEffect, useState } from 'react'
import { AppState, Button, Text, View } from 'react-native'

export default function App() {
  const [permissionStatus, setPermissionStatus] = useState('unknown')
  const [error, setError] = useState<string | null>(null)

  const checkAccessibilityPermission = useCallback(async () => {
    try {
      const isEnabled = await AccessibilityService.isEnabled()
      setPermissionStatus(isEnabled ? 'enabled' : 'disabled')
    } catch (cause) {
      setError('Could not read the permission status')
    }
  }, [])

  useEffect(() => {
    void checkAccessibilityPermission()
  }, [checkAccessibilityPermission])

  // The user grants the permission in the system Settings app, so the only
  // reliable moment to re-read it is the return to your app. A fixed timer
  // cannot know when that happens: the user may take a second, or a minute.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') void checkAccessibilityPermission()
    })

    return () => subscription.remove()
  }, [checkAccessibilityPermission])

  const requestPermission = async () => {
    try {
      await AccessibilityService.askPermission()
    } catch (cause) {
      setError('Could not open the accessibility settings')
    }
  }

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Accessibility Service Status: {permissionStatus}</Text>
      {/* Show failures on screen. A console.error reaches no user. */}
      {error !== null && <Text>{error}</Text>}
      <Button title="Request Permission" onPress={requestPermission} />
    </View>
  )
}
```

### Monitoring Foreground App Changes

```typescript
import * as AccessibilityService from 'expo-accessibility-service'
import type { AccessibilityEvent } from 'expo-accessibility-service'
import { useEffect, useState, useRef } from 'react'
import { Button, Text, View, ScrollView } from 'react-native'

export default function App() {
  const [events, setEvents] = useState<AccessibilityEvent[]>([])
  const subscriptionRef = useRef(null)

  useEffect(() => {
    // Start monitoring
    const subscription = AccessibilityService.addAccessibilityEventListener(
      (event: AccessibilityEvent) => {
        console.log('App changed:', event.packageName)
        setEvents((prev) => [event, ...prev].slice(0, 10)) // Keep last 10 events
      },
    )

    subscriptionRef.current = subscription

    // Cleanup on unmount
    return () => {
      subscription.remove()
    }
  }, [])

  return (
    <ScrollView>
      <Text>Recent App Changes:</Text>
      {events.map((event, index) => (
        <View key={`${event.timestamp}-${index}`}>
          <Text>App: {event.packageName}</Text>
          <Text>Class: {event.className}</Text>
          <Text>Time: {new Date(event.timestamp).toLocaleTimeString()}</Text>
        </View>
      ))}
    </ScrollView>
  )
}
```

## API Reference

### `isEnabled(): Promise<boolean>`

Checks if the accessibility service is currently enabled for your app.

**Returns:** A promise that resolves to `true` if the accessibility service is enabled, `false` otherwise.

**Example:**

```typescript
const enabled = await AccessibilityService.isEnabled()
console.log('Accessibility service enabled:', enabled)
```

### `askPermission(): Promise<void>`

Opens the device's accessibility settings page where users can enable the accessibility service for your app.

**Returns:** A promise that resolves when the settings page is opened.

**Example:**

```typescript
await AccessibilityService.askPermission()
// User will be taken to accessibility settings
```

### `addAccessibilityEventListener(listener: (event: AccessibilityEvent) => void): AccessibilityEventSubscription`

Registers a listener for foreground app change events. The listener will be called whenever a new app comes to the foreground.

**Parameters:**

- `listener`: A callback function that receives `AccessibilityEvent` objects

**Returns:** An `AccessibilityEventSubscription` object with a `remove()` method to unsubscribe

**Example:**

```typescript
const subscription = AccessibilityService.addAccessibilityEventListener(
  (event) => {
    console.log('App changed:', event.packageName)
    console.log('Activity:', event.className)
    console.log('Timestamp:', event.timestamp)
  },
)

// Later, to stop listening:
subscription.remove()
```

**Important Notes:**

- Supports multiple simultaneous listeners
- Events are delivered with sub-100ms latency
- The service continues running when the JS app is backgrounded
- Properly handles permission revocation
- Always call `subscription.remove()` when done to prevent memory leaks

### `addUrlBarChangeListener(listener: (event: UrlBarEvent) => void): AccessibilityEventSubscription`

Registers a listener for address-bar readings in supported browsers. See [`BROWSER_URL_BAR_FIELD_IDS`](android/src/main/java/expo/modules/accessibilityservice/AccessibilityService.kt) for the browsers that are covered.

**Parameters:**

- `listener`: A callback function that receives `UrlBarEvent` objects

**Returns:** An `AccessibilityEventSubscription` object with a `remove()` method to unsubscribe

**Example:**

```typescript
const subscription = AccessibilityService.addUrlBarChangeListener((event) => {
  if (event.isEditing) return // the user is still typing

  console.log('Address shown:', event.rawText)
})

// Later, to stop listening:
subscription.remove()
```

**Important Notes:**

- The address bar emits an event per keystroke, so one visit produces many events
- `isEditing` is the only reliable way to tell a destination from a keystroke. `true` means the bar held input focus. Treat `true` as authoritative ("do not act yet")
- `isEditing: false` is **not** proof the user stopped typing. A browser whose address bar is not a focusable text node reports `false` throughout, so keep a fallback for those
- `rawText` is unparsed. It may be a URL, a search term, a partial word, or a browser's own placeholder

### `goBack(): Promise<boolean>`

Presses the system Back button on the user's behalf. Requires the accessibility service to be bound.

**Returns:** A promise that resolves to `true` when the action was performed, `false` when the service is not bound — so you can tell "refused" from "not running" without a second call.

**Example:**

```typescript
const wentBack = await AccessibilityService.goBack()
```

### `isServiceRunning(): Promise<boolean>`

Whether the accessibility service is actually bound and running, rather than merely listed in the system settings. Distinguishes "enabled in settings but not bound" — Restricted Settings on a sideloaded install, or an unbind after process death — from genuinely running.

It asks about the same service ids as `isEnabled()`, so a service configured through `setServiceClassName()` is the one both functions answer about.

**Returns:** A promise that resolves to `true` when the service is bound

### `openAppDetailsSettings(): Promise<void>`

Opens the system's App info screen for your app.

**Returns:** A promise that resolves when the screen is opened. Rejects with `ERR_NO_APP_DETAILS_ACTIVITY` when no activity can handle it.

### `setServiceClassName(className: string): Promise<void>`

Configure the accessibility service class name to check for. This allows using custom service class names instead of the default "MyAccessibilityService".

**Parameters:**

- `className`: The fully qualified class name (e.g., "com.example.MyCustomAccessibilityService")

**Returns:** A promise that resolves when the configuration is set

**Example:**

```typescript
await AccessibilityService.setServiceClassName(
  'com.myapp.CustomAccessibilityService',
)
```

### `getDetectedServices(): Promise<string[]>`

Get a list of accessibility services detected in the app's manifest. This can help identify available services for configuration.

**Returns:** A promise that resolves to an array of service class names. Rejects with `ERR_MANIFEST_UNREADABLE` when the manifest cannot be read — an empty array means "none declared", never "the read failed".

**Example:**

```typescript
const services = await AccessibilityService.getDetectedServices()
console.log('Available services:', services)
```

## Types

### `AccessibilityEvent`

Event object emitted when a foreground app change is detected.

```typescript
type AccessibilityEvent = {
  packageName: string // The package name of the app (e.g., "com.android.chrome")
  className: string // The activity/window class name
  timestamp: number // Unix timestamp in milliseconds
}
```

### `UrlBarEvent`

Event object emitted when the address bar changes in a supported browser.

```typescript
type UrlBarEvent = {
  packageName: string // The browser package (e.g., "com.android.chrome")
  rawText: string // The text in the address bar. Unparsed
  timestamp: number // Unix timestamp in milliseconds
  isEditing: boolean // Whether the bar held input focus, i.e. the user was typing
}
```

### `AccessibilityEventSubscription`

Subscription object returned by `addAccessibilityEventListener()` and `addUrlBarChangeListener()`.

```typescript
type AccessibilityEventSubscription = {
  remove: () => void // Call this to unsubscribe from events
}
```

## Platform Support

### Android

- ✅ Full support
- Checks for accessibility service status
- Opens accessibility settings for permission granting

### iOS

- ❌ Not supported
- Methods will reject with "This method is not implemented on iOS" error
- iOS doesn't have the same accessibility service concept as Android
- Consider using iOS-specific accessibility APIs like VoiceOver detection instead

### Web

- ⚠️ Not applicable
- Web browsers don't have accessibility service permissions

## Important Notes

### Android Accessibility Service

This module is designed to work with Android's accessibility service framework. To use it effectively:

1. **Service Implementation**: You'll need to implement an `AccessibilityService` class in your Android app
2. **Manifest Declaration**: Declare the service in your `AndroidManifest.xml`
3. **Service Configuration**: Create an accessibility service configuration XML file

### Example Android Service Setup

The module expects a service named `{packageName}.MyAccessibilityService`. Make sure your accessibility service follows this naming convention or modify the module accordingly.

## Development

### Prerequisites

- Node.js
- Expo CLI
- Android Studio (for Android development)
- Xcode (for iOS development)

### Building the Module

```bash
npm run build
```

### Running the Example

```bash
cd example
npx expo run:android
```

### Troubleshooting

#### Android Build Fails with Dependency Resolution Errors

If you encounter build errors like "Could not resolve project :react-native-edge-to-edge" or similar dependency resolution failures, regenerate the Android native project:

```bash
cd example
npx expo prebuild --clean --platform android
npx expo run:android
```

**Why this happens:**

- The Android Gradle configuration may become out of sync with Expo's autolinking
- Dependencies introduced by Expo SDK updates may not be properly configured
- Running `prebuild --clean` regenerates all native Android files with the correct configuration

**Note:** The first build after running `prebuild --clean` may take longer as it compiles all native dependencies from scratch.

### Testing

```bash
npm test
```

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Related

- [Expo Modules API](https://docs.expo.dev/modules/overview/)
- [Android Accessibility Service](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
