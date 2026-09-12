import { NativeModule, requireNativeModule } from 'expo'

import type { ExpoAccessibilityServiceModuleEvents } from './ExpoAccessibilityService.types'

declare class ExpoAccessibilityServiceModule extends NativeModule<ExpoAccessibilityServiceModuleEvents> {
  isEnabled: () => Promise<boolean>
  isServiceRunning: () => Promise<boolean>
  openAppDetailsSettings: () => Promise<void>
  askPermission: () => Promise<void>
  setServiceClassName: (className: string) => Promise<void>
  getDetectedServices: () => Promise<string[]>
  emitCurrentForegroundApp: () => Promise<void>
  goBack: () => Promise<boolean>
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoAccessibilityServiceModule>(
  'ExpoAccessibilityService',
)
