import { NativeModule, requireNativeModule } from 'expo'

import { ExpoAccessibilityServiceModuleEvents } from './ExpoAccessibilityService.types'

declare class ExpoAccessibilityServiceModule extends NativeModule<ExpoAccessibilityServiceModuleEvents> {
  isEnabled: () => Promise<boolean>
  askPermission: () => Promise<void>
  setServiceClassName: (className: string) => Promise<void>
  getDetectedServices: () => Promise<string[]>
  emitCurrentForegroundApp: () => Promise<void>
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoAccessibilityServiceModule>(
  'ExpoAccessibilityService',
)
