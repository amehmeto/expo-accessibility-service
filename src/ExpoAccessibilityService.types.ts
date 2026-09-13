export type AccessibilityEvent = {
  packageName: string
  className: string
  timestamp: number
}

/**
 * A reading of a browser's address bar.
 *
 * `rawText` keeps the name it has in the native layer because nothing parses it.
 * It may be a URL, a search term, a half-typed word, or a browser's own
 * placeholder. Treat it as text, not as an address.
 */
export type UrlBarEvent = {
  packageName: string
  rawText: string
  timestamp: number
  /**
   * Whether the address bar held input focus when the event fired — that is,
   * whether the user was typing rather than looking at the address of the page
   * they are on.
   *
   * The bar emits an event per keystroke and completes "fa" into "facebook.com"
   * inline, so text alone cannot separate a destination from a suggestion under
   * the user's fingers. The transition from `true` to `false` on the same text
   * is the navigation itself.
   *
   * `false` is NOT proof the user is not typing. A browser whose address bar is
   * not a focusable text node reports `false` throughout. Treat `true` as
   * authoritative ("do not act yet") and keep a fallback for the browsers that
   * never report `true`.
   */
  isEditing: boolean
}

export type AccessibilityEventSubscription = {
  remove: () => void
}

export type ExpoAccessibilityServiceModuleEvents = {
  onAccessibilityEvent: (event: AccessibilityEvent) => void
  onUrlBarChanged: (event: UrlBarEvent) => void
}
