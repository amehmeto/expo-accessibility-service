// Reexport the native module. On web, it will be resolved to ExpoAccessibilityServiceModule.web.ts
// and on native platforms to ExpoAccessibilityServiceModule.ts
import ExpoAccessibilityServiceModule from "./ExpoAccessibilityServiceModule";

export function isEnabled(): Promise<boolean> {
  return ExpoAccessibilityServiceModule.isEnabled();
}

export function askPermission(): Promise<void> {
  return ExpoAccessibilityServiceModule.askPermission();
}
