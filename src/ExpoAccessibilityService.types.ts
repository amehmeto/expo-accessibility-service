export type AccessibilityEvent = {
  packageName: string
  className: string
  timestamp: number
}

export type UrlBarEvent = {
  packageName: string
  rawText: string
  timestamp: number
}

export type AccessibilityEventSubscription = {
  remove: () => void
}

export type ExpoAccessibilityServiceModuleEvents = {
  onAccessibilityEvent: (event: AccessibilityEvent) => void
  onUrlBarChanged: (event: UrlBarEvent) => void
}
