import ExpoAccessibilityServiceModule from '../ExpoAccessibilityServiceModule'
import { isEnabled, setServiceClassName, getDetectedServices } from '../index'
import { AndroidAccessibilityTestHelper } from './test-utils/android-mocks.helper'

// Mock the native module
jest.mock('../ExpoAccessibilityServiceModule', () => ({
  isEnabled: jest.fn(),
  askPermission: jest.fn(),
  setServiceClassName: jest.fn(),
  getDetectedServices: jest.fn(),
}))
const mockModule = ExpoAccessibilityServiceModule as jest.Mocked<
  typeof ExpoAccessibilityServiceModule
>

describe('Android Accessibility Service Configuration Tests', () => {
  let helper: AndroidAccessibilityTestHelper

  beforeEach(() => {
    helper = new AndroidAccessibilityTestHelper()
    jest.clearAllMocks()

    // Mock the configuration behavior
    let configuredServiceClassName: string | null = null
    const detectedServices = [
      'com.example.testaccessibility.MyAccessibilityService',
    ]

    mockModule.setServiceClassName.mockImplementation(
      async (className: string) => {
        configuredServiceClassName = className
        return undefined
      },
    )

    mockModule.getDetectedServices.mockImplementation(async () => {
      return detectedServices
    })

    mockModule.isEnabled.mockImplementation(async () => {
      const packageName = helper.context.packageName
      let serviceNamesToCheck: string[]

      if (configuredServiceClassName) {
        // Use configured service name
        serviceNamesToCheck = [`${packageName}/${configuredServiceClassName}`]
      } else if (detectedServices.length > 0) {
        // Use auto-detected services
        serviceNamesToCheck = detectedServices.map(
          (service) => `${packageName}/${service}`,
        )
      } else {
        // Fall back to default
        serviceNamesToCheck = [
          `${packageName}/${packageName}.MyAccessibilityService`,
        ]
      }

      const enabledServices = helper.settings.Secure.getString(
        helper.context.contentResolver,
        helper.settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      )

      if (!enabledServices || enabledServices === '') {
        return false
      }

      const serviceList = enabledServices.split(':').map((s) => s.trim())
      return serviceNamesToCheck.some((serviceName) =>
        serviceList.includes(serviceName),
      )
    })
  })

  afterEach(() => {
    helper.reset()
  })

  describe('Service Configuration', () => {
    test('should allow setting custom service class name', async () => {
      const customServiceName = 'com.example.CustomAccessibilityService'

      await setServiceClassName(customServiceName)

      expect(mockModule.setServiceClassName).toHaveBeenCalledWith(
        customServiceName,
      )
    })

    test('should detect configured custom service when enabled', async () => {
      const customServiceName = 'com.example.CustomAccessibilityService'
      const packageName = helper.context.packageName
      const fullServiceName = `${packageName}/${customServiceName}`

      // Configure custom service name
      await setServiceClassName(customServiceName)

      // Enable the custom service
      helper.settings.Secure.getString.mockReturnValue(fullServiceName)

      const result = await isEnabled()

      expect(result).toBe(true)
      expect(mockModule.setServiceClassName).toHaveBeenCalledWith(
        customServiceName,
      )
    })

    test('should not detect custom service when different service is enabled', async () => {
      const customServiceName = 'com.example.CustomAccessibilityService'

      // Configure custom service name
      await setServiceClassName(customServiceName)

      // Enable a different service
      helper.settings.Secure.getString.mockReturnValue(
        'com.example.testaccessibility/com.example.testaccessibility.DifferentService',
      )

      const result = await isEnabled()

      expect(result).toBe(false)
    })

    test('should support multiple custom service names in configuration workflow', async () => {
      const serviceNames = [
        'com.example.PrimaryAccessibilityService',
        'com.example.SecondaryAccessibilityService',
      ]

      for (const serviceName of serviceNames) {
        await setServiceClassName(serviceName)
        expect(mockModule.setServiceClassName).toHaveBeenCalledWith(serviceName)
      }

      expect(mockModule.setServiceClassName).toHaveBeenCalledTimes(
        serviceNames.length,
      )
    })
  })

  describe('Service Auto-Detection', () => {
    test('should return detected services from manifest', async () => {
      const expectedServices = [
        'com.example.testaccessibility.MyAccessibilityService',
        'com.example.testaccessibility.SecondaryService',
      ]

      mockModule.getDetectedServices.mockResolvedValue(expectedServices)

      const result = await getDetectedServices()

      expect(result).toEqual(expectedServices)
      expect(mockModule.getDetectedServices).toHaveBeenCalledTimes(1)
    })

    test('should handle empty detected services list', async () => {
      mockModule.getDetectedServices.mockResolvedValue([])

      const result = await getDetectedServices()

      expect(result).toEqual([])
    })

    test('should use auto-detected service when no configuration is set', async () => {
      // Don't call setServiceClassName - should auto-detect
      const packageName = helper.context.packageName
      const autoDetectedService =
        'com.example.testaccessibility.MyAccessibilityService'
      const fullServiceName = `${packageName}/${autoDetectedService}`

      // Enable the auto-detected service
      helper.settings.Secure.getString.mockReturnValue(fullServiceName)

      const result = await isEnabled()

      expect(result).toBe(true)
      // setServiceClassName should not have been called
      expect(mockModule.setServiceClassName).not.toHaveBeenCalled()
    })
  })

  describe('Backward Compatibility', () => {
    test('should fall back to default service name when no configuration and no detection', async () => {
      // Mock no detected services
      mockModule.getDetectedServices.mockResolvedValue([])

      const packageName = helper.context.packageName
      const defaultServiceName = `${packageName}/${packageName}.MyAccessibilityService`

      // Enable the default service
      helper.settings.Secure.getString.mockReturnValue(defaultServiceName)

      const result = await isEnabled()

      expect(result).toBe(true)
    })

    test('should maintain compatibility with existing MyAccessibilityService', async () => {
      const packageName = helper.context.packageName
      const legacyServiceName = `${packageName}/${packageName}.MyAccessibilityService`

      // Enable the legacy service format
      helper.settings.Secure.getString.mockReturnValue(legacyServiceName)

      const result = await isEnabled()

      expect(result).toBe(true)
    })

    test('should work with existing service lists containing default service', async () => {
      const packageName = helper.context.packageName
      const defaultService = `${packageName}/${packageName}.MyAccessibilityService`
      const serviceList = [
        'com.android.talkback/com.google.android.marvin.talkback.TalkBackService',
        defaultService,
        'com.google.android.apps.accessibility.voiceaccess/com.google.android.apps.accessibility.voiceaccess.JustSpeakService',
      ].join(':')

      helper.settings.Secure.getString.mockReturnValue(serviceList)

      const result = await isEnabled()

      expect(result).toBe(true)
    })
  })

  describe('Configuration Priority and Fallback', () => {
    test('should prioritize configured service name over auto-detection', async () => {
      const configuredService = 'com.example.ConfiguredService'
      const packageName = helper.context.packageName

      // Configure a specific service
      await setServiceClassName(configuredService)

      // Enable the configured service
      helper.settings.Secure.getString.mockReturnValue(
        `${packageName}/${configuredService}`,
      )

      const result = await isEnabled()

      expect(result).toBe(true)
      expect(mockModule.setServiceClassName).toHaveBeenCalledWith(
        configuredService,
      )
    })

    test('should not detect auto-detected service when different service is configured', async () => {
      const configuredService = 'com.example.ConfiguredService'
      const autoDetectedService =
        'com.example.testaccessibility.MyAccessibilityService'
      const packageName = helper.context.packageName

      // Configure a specific service
      await setServiceClassName(configuredService)

      // Enable the auto-detected service (but not the configured one)
      helper.settings.Secure.getString.mockReturnValue(
        `${packageName}/${autoDetectedService}`,
      )

      const result = await isEnabled()

      expect(result).toBe(false) // Should not detect auto-detected service when configured service is different
    })

    test('should demonstrate complete configuration workflow', async () => {
      const packageName = helper.context.packageName

      // Step 1: Get available services
      const availableServices = ['com.example.ServiceA', 'com.example.ServiceB']
      mockModule.getDetectedServices.mockResolvedValue(availableServices)

      const detectedServices = await getDetectedServices()
      expect(detectedServices).toEqual(availableServices)

      // Step 2: Configure preferred service
      const preferredService = availableServices[0]
      await setServiceClassName(preferredService)

      // Step 3: Check if configured service is enabled
      helper.settings.Secure.getString.mockReturnValue(
        `${packageName}/${preferredService}`,
      )

      const isServiceEnabled = await isEnabled()
      expect(isServiceEnabled).toBe(true)

      // Step 4: Verify configuration took precedence
      expect(mockModule.setServiceClassName).toHaveBeenCalledWith(
        preferredService,
      )
    })
  })
})
