# Expo Accessibility Service

An Expo module for managing Android accessibility service permissions and status. This module provides a simple way to check if accessibility services are enabled and request permissions from users.

## Features

- ✅ Check if accessibility service is enabled
- ✅ Request accessibility service permissions
- ✅ Cross-platform support (Android only)
- ✅ TypeScript support
- ✅ Expo SDK 53+ compatible

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

```typescript
import * as AccessibilityService from 'expo-accessibility-service';
import { useEffect, useState } from 'react';
import { Button, Text, View } from 'react-native';

export default function App() {
  const [permissionStatus, setPermissionStatus] = useState('unknown');

  useEffect(() => {
    checkAccessibilityPermission();
  }, []);

  const checkAccessibilityPermission = async () => {
    try {
      const isEnabled = await AccessibilityService.isEnabled();
      setPermissionStatus(isEnabled ? 'enabled' : 'disabled');
    } catch (error) {
      console.error('Error checking accessibility permission:', error);
    }
  };

  const requestPermission = async () => {
    try {
      await AccessibilityService.askPermission();
      // Note: After returning from settings, you may want to recheck the status
      setTimeout(checkAccessibilityPermission, 1000);
    } catch (error) {
      console.error('Error requesting accessibility permission:', error);
    }
  };

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Accessibility Service Status: {permissionStatus}</Text>
      <Button title="Request Permission" onPress={requestPermission} />
    </View>
  );
}
```

## API Reference

### `isEnabled(): Promise<boolean>`

Checks if the accessibility service is currently enabled for your app.

**Returns:** A promise that resolves to `true` if the accessibility service is enabled, `false` otherwise.

**Example:**

```typescript
const enabled = await AccessibilityService.isEnabled();
console.log("Accessibility service enabled:", enabled);
```

### `askPermission(): Promise<void>`

Opens the device's accessibility settings page where users can enable the accessibility service for your app.

**Returns:** A promise that resolves when the settings page is opened.

**Example:**

```typescript
await AccessibilityService.askPermission();
// User will be taken to accessibility settings
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
