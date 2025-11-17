import { NativeModule, requireNativeModule } from 'expo'

import { ExpoAccessibilityServiceModuleEvents } from './ExpoAccessibilityService.types'

declare class ExpoAccessibilityServiceModule extends NativeModule<ExpoAccessibilityServiceModuleEvents> {
  isEnabled: () => Promise<boolean>
  askPermission: () => Promise<void>
  setServiceClassName: (className: string) => Promise<void>
  getDetectedServices: () => Promise<string[]>
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoAccessibilityServiceModule>(
  'ExpoAccessibilityService',
)
