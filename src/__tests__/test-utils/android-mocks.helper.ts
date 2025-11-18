/**
 * Android-specific test utilities and mocks for accessibility service testing
 */

export interface MockAndroidContext {
  packageName: string
  contentResolver: any
  startActivity: jest.MockedFunction<(intent: any) => void>
}

export interface MockAndroidSettings {
  Secure: {
    ENABLED_ACCESSIBILITY_SERVICES: string
    getString: jest.MockedFunction<
      (resolver: any, key: string) => string | null
    >
  }
}

export class AndroidAccessibilityTestHelper {
  private mockContext: MockAndroidContext
  private mockSettings: MockAndroidSettings

  constructor(packageName = 'com.example.testaccessibility') {
    this.mockContext = {
      packageName,
      contentResolver: {},
      startActivity: jest.fn(),
    }

    this.mockSettings = {
      Secure: {
        ENABLED_ACCESSIBILITY_SERVICES: 'enabled_accessibility_services',
        getString: jest.fn(),
      },
    }
  }

  get context() {
    return this.mockContext
  }

  get settings() {
    return this.mockSettings
  }

  /**
   * Generate the expected service name format for the given package
   */
  getExpectedServiceName(packageName?: string): string {
    const pkg = packageName || this.mockContext.packageName
    return `${pkg}/${pkg}.MyAccessibilityService`
  }

  /**
   * Set up enabled services string with various services including ours
   */
  setEnabledServices(
    includeOurService = true,
    additionalServices: string[] = []
  ): void {
    const services: string[] = [...additionalServices]

    if (includeOurService) {
      services.push(this.getExpectedServiceName())
    }

    this.mockSettings.Secure.getString.mockReturnValue(services.join(':'))
  }

  /**
   * Set up empty/disabled services state
   */
  setNoServicesEnabled(): void {
    this.mockSettings.Secure.getString.mockReturnValue('')
  }

  /**
   * Set up null services state (system error scenario)
   */
  setNullServicesState(): void {
    this.mockSettings.Secure.getString.mockReturnValue(null)
  }

  /**
   * Create a realistic Android accessibility services list
   */
  createRealisticServicesList(includeOurService = true): string {
    const commonServices = [
      'com.android.talkback/com.google.android.marvin.talkback.TalkBackService',
      'com.google.android.apps.accessibility.voiceaccess/com.google.android.apps.accessibility.voiceaccess.JustSpeakService',
      'com.samsung.accessibility/com.samsung.accessibility.assistant.AssistantMenuService',
    ]

    const services = [...commonServices]

    if (includeOurService) {
      services.push(this.getExpectedServiceName())
    }

    return services.join(':')
  }

  /**
   * Set up successful intent creation and activity start
   */
  setupSuccessfulPermissionRequest(): void {
    this.mockContext.startActivity.mockImplementation(() => {})
  }

  /**
   * Set up failed activity start (e.g., SecurityException)
   */
  setupFailedPermissionRequest(errorMessage = 'Permission denied'): void {
    this.mockContext.startActivity.mockImplementation(() => {
      throw new Error(errorMessage)
    })
  }

  /**
   * Reset all mocks to clean state
   */
  reset(): void {
    jest.clearAllMocks()
    this.mockSettings.Secure.getString.mockReturnValue('')
    this.mockContext.startActivity.mockClear()
  }

  /**
   * Verify that the Settings.Secure.getString was called correctly
   */
  verifyServiceDetectionCall(): void {
    expect(this.mockSettings.Secure.getString).toHaveBeenCalledWith(
      this.mockContext.contentResolver,
      this.mockSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
  }

  /**
   * Verify that startActivity was called for accessibility settings
   */
  verifyPermissionRequestCall(): void {
    expect(this.mockContext.startActivity).toHaveBeenCalledTimes(1)
  }
}

/**
 * Create a mock implementation that simulates the Android native module behavior
 */
export function createAndroidNativeMock(
  helper: AndroidAccessibilityTestHelper
) {
  return {
    isEnabled: jest.fn().mockImplementation(async () => {
      const packageName = helper.context.packageName
      const serviceName = helper.getExpectedServiceName(packageName)

      const enabledServices = helper.settings.Secure.getString(
        helper.context.contentResolver,
        helper.settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      )

      if (!enabledServices || enabledServices === '') {
        return false
      }

      return enabledServices.includes(serviceName)
    }),

    askPermission: jest.fn().mockImplementation(async () => {
      try {
        // Simulate creating accessibility settings intent
        const intent = { action: 'android.settings.ACCESSIBILITY_SETTINGS' }
        helper.context.startActivity(intent)
        return undefined
      } catch (error) {
        throw new Error(
          `Could not open accessibility settings: ${(error as Error).message}`
        )
      }
    }),
  }
}

/**
 * Test scenarios for common Android accessibility service states
 */
export const AndroidTestScenarios = {
  NO_SERVICES_ENABLED: 'no_services',
  NULL_SERVICES: 'null_services',
  OUR_SERVICE_ONLY: 'our_service_only',
  OUR_SERVICE_WITH_OTHERS: 'our_service_with_others',
  OTHERS_ONLY: 'others_only',
  MALFORMED_LIST: 'malformed_list',
  VERY_LONG_LIST: 'very_long_list',
} as const

export type AndroidTestScenario =
  (typeof AndroidTestScenarios)[keyof typeof AndroidTestScenarios]
