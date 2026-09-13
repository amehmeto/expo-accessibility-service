// Reexport the native module. On web, it will be resolved to ExpoAccessibilityServiceModule.web.ts
// and on native platforms to ExpoAccessibilityServiceModule.ts
import type {
  AccessibilityEvent,
  AccessibilityEventSubscription,
  UrlBarEvent,
} from './ExpoAccessibilityService.types'
import ExpoAccessibilityServiceModule from './ExpoAccessibilityServiceModule'

export function isEnabled(): Promise<boolean> {
  return ExpoAccessibilityServiceModule.isEnabled()
}

export function isServiceRunning(): Promise<boolean> {
  return ExpoAccessibilityServiceModule.isServiceRunning()
}

export function openAppDetailsSettings(): Promise<void> {
  return ExpoAccessibilityServiceModule.openAppDetailsSettings()
}

export function askPermission(): Promise<void> {
  return ExpoAccessibilityServiceModule.askPermission()
}

/**
 * Configure the accessibility service class name to check for.
 * This allows using custom service class names instead of the default "MyAccessibilityService".
 *
 * @param className - The fully qualified class name (e.g., "com.example.MyCustomAccessibilityService")
 * @returns Promise that resolves when the configuration is set
 */
export function setServiceClassName(className: string): Promise<void> {
  return ExpoAccessibilityServiceModule.setServiceClassName(className)
}

/**
 * Get a list of accessibility services detected in the app's manifest.
 * This can help identify available services for configuration.
 *
 * @returns Promise that resolves to an array of service class names
 */
export function getDetectedServices(): Promise<string[]> {
  return ExpoAccessibilityServiceModule.getDetectedServices()
}

/**
 * Add a listener for foreground app change events.
 * The listener will be called whenever a new app comes to the foreground.
 *
 * @param listener - Callback function that receives accessibility events with packageName, className, and timestamp
 * @returns A subscription object with a remove() method to unsubscribe
 *
 * @example
 * ```typescript
 * const subscription = addAccessibilityEventListener((event) => {
 *   console.log('App changed:', event.packageName);
 * });
 *
 * // Later, to stop listening:
 * subscription.remove();
 * ```
 */
export function addAccessibilityEventListener(
  listener: (event: AccessibilityEvent) => void,
): AccessibilityEventSubscription {
  const subscription = ExpoAccessibilityServiceModule.addListener(
    'onAccessibilityEvent',
    listener,
  )

  return {
    remove: () => subscription.remove(),
  }
}

/**
 * Emit the current foreground app as a synthetic accessibility event.
 * Uses rootInActiveWindow to determine what app is currently on screen.
 *
 * This is useful when the foreground service starts while an app is already
 * visible (no TYPE_WINDOW_STATE_CHANGED event fires for already-visible apps).
 * Calling this will trigger any registered accessibility event listeners with
 * the current foreground app's package name.
 *
 * @returns Promise that resolves when the event has been emitted
 *
 * @example
 * ```typescript
 * // After starting the foreground service from a native alarm,
 * // detect what app is currently on screen:
 * await emitCurrentForegroundApp();
 * ```
 */
export function emitCurrentForegroundApp(): Promise<void> {
  return ExpoAccessibilityServiceModule.emitCurrentForegroundApp()
}

/**
 * Press the system Back button on the user's behalf.
 *
 * Requires the accessibility service to be bound. Resolves to `false` when it is
 * not, so you can tell "refused" from "not running" without a second call.
 *
 * @returns Promise that resolves to whether the action was performed
 *
 * @example
 * ```typescript
 * // Push the user out of an app you are blocking:
 * const wentBack = await goBack()
 * ```
 */
export function goBack(): Promise<boolean> {
  return ExpoAccessibilityServiceModule.goBack()
}

/**
 * Add a listener for address-bar readings in supported browsers.
 *
 * Fires while the user types, so one visit can produce many events. Use
 * `event.isEditing` to tell a destination from a keystroke: `true` means the bar
 * held input focus, so the text is what the user is typing, not where they are.
 *
 * `event.rawText` is unparsed. It may be a URL, a search term, or a partial
 * word.
 *
 * @param listener - Callback that receives packageName, rawText, timestamp and isEditing
 * @returns A subscription object with a remove() method to unsubscribe
 *
 * @example
 * ```typescript
 * const subscription = addUrlBarChangeListener((event) => {
 *   if (event.isEditing) return // still typing, not a destination
 *   console.log('Address shown:', event.rawText)
 * })
 *
 * // Later, to stop listening:
 * subscription.remove()
 * ```
 */
export function addUrlBarChangeListener(
  listener: (event: UrlBarEvent) => void,
): AccessibilityEventSubscription {
  const subscription = ExpoAccessibilityServiceModule.addListener(
    'onUrlBarChanged',
    listener,
  )

  return {
    remove: () => subscription.remove(),
  }
}

// Re-export types for convenience
export type { AccessibilityEvent, AccessibilityEventSubscription, UrlBarEvent }
