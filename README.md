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

## License

MIT © [Ram Suthar](https://github.com/reallyram)

## Support

- 📧 Email: reallyram@gmail.com
- 🐛 Issues: [GitHub Issues](https://github.com/reallyram/expo-accessibility-service/issues)
- 📖 Documentation: [GitHub Repository](https://github.com/reallyram/expo-accessibility-service)

## Related

- [Expo Modules API](https://docs.expo.dev/modules/overview/)
- [Android Accessibility Service](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
