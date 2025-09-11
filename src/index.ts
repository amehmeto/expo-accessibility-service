// Reexport the native module. On web, it will be resolved to ExpoAccessibilityServiceModule.web.ts
// and on native platforms to ExpoAccessibilityServiceModule.ts
import ExpoAccessibilityServiceModule from "./ExpoAccessibilityServiceModule";

export function isEnabled(): Promise<boolean> {
  return ExpoAccessibilityServiceModule.isEnabled();
}

export function askPermission(): Promise<void> {
  return ExpoAccessibilityServiceModule.askPermission();
}

/**
 * Configure the accessibility service class name to check for.
 * This allows using custom service class names instead of the default "MyAccessibilityService".
 * 
 * @param className - The fully qualified class name (e.g., "com.example.MyCustomAccessibilityService")
 * @returns Promise that resolves when the configuration is set
 */
export function setServiceClassName(className: string): Promise<void> {
  return ExpoAccessibilityServiceModule.setServiceClassName(className);
}

/**
 * Get a list of accessibility services detected in the app's manifest.
 * This can help identify available services for configuration.
 * 
 * @returns Promise that resolves to an array of service class names
 */
export function getDetectedServices(): Promise<string[]> {
  return ExpoAccessibilityServiceModule.getDetectedServices();
}
