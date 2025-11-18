import ExpoAccessibilityServiceModule from '../ExpoAccessibilityServiceModule'
import { isEnabled, askPermission } from '../index'
import { AndroidAccessibilityTestHelper } from './test-utils/android-mocks.helper'

// Mock the native module at the top level
jest.mock('../ExpoAccessibilityServiceModule', () => ({
  isEnabled: jest.fn(),
  askPermission: jest.fn(),
}))
const mockNativeModule = ExpoAccessibilityServiceModule as jest.Mocked<
  typeof ExpoAccessibilityServiceModule
>

describe('Android Accessibility Service - Real World Scenarios', () => {
  let helper: AndroidAccessibilityTestHelper

  beforeEach(() => {
    helper = new AndroidAccessibilityTestHelper()
    jest.clearAllMocks()

    // Set up the mock to use our helper's logic
    mockNativeModule.isEnabled.mockImplementation(async () => {
      const packageName = helper.context.packageName
      const serviceName = helper.getExpectedServiceName(packageName)

      const enabledServices = helper.settings.Secure.getString(
        helper.context.contentResolver,
        helper.settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      )

      if (!enabledServices || enabledServices === '') {
        return false
      }

      // Android services are colon-separated, so check for exact match
      // Also trim whitespace as the system may have formatting issues
      const serviceList = enabledServices.split(':').map((s) => s.trim())
      return serviceList.includes(serviceName)
    })

    mockNativeModule.askPermission.mockImplementation(async () => {
      try {
        const intent = { action: 'android.settings.ACCESSIBILITY_SETTINGS' }
        helper.context.startActivity(intent)
        return undefined
      } catch (error) {
        throw new Error(
          `Could not open accessibility settings: ${(error as Error).message}`
        )
      }
    })
  })

  afterEach(() => {
    helper.reset()
  })

  describe('Service Detection Scenarios', () => {
    test('Scenario: Fresh app installation - no accessibility services enabled', async () => {
      helper.setNoServicesEnabled()

      const result = await isEnabled()

      expect(result).toBe(false)
      helper.verifyServiceDetectionCall()
    })

    test('Scenario: System error - accessibility services setting returns null', async () => {
      helper.setNullServicesState()

      const result = await isEnabled()

      expect(result).toBe(false)
      helper.verifyServiceDetectionCall()
    })

    test('Scenario: Only our accessibility service is enabled', async () => {
      helper.setEnabledServices(true, [])

      const result = await isEnabled()

      expect(result).toBe(true)
      helper.verifyServiceDetectionCall()
    })

    test('Scenario: Our service enabled alongside popular accessibility apps', async () => {
      const popularServices = [
        'com.android.talkback/com.google.android.marvin.talkback.TalkBackService',
        'com.google.android.apps.accessibility.voiceaccess/com.google.android.apps.accessibility.voiceaccess.JustSpeakService',
        'com.samsung.accessibility/com.samsung.accessibility.assistant.AssistantMenuService',
      ]

      helper.setEnabledServices(true, popularServices)

      const result = await isEnabled()

      expect(result).toBe(true)
      helper.verifyServiceDetectionCall()
    })

    test('Scenario: Other services enabled, but not ours', async () => {
      const otherServices = [
        'com.android.talkback/com.google.android.marvin.talkback.TalkBackService',
        'com.third.party.app/com.third.party.app.AccessibilityService',
      ]

      helper.setEnabledServices(false, otherServices)

      const result = await isEnabled()

      expect(result).toBe(false)
      helper.verifyServiceDetectionCall()
    })

    test('Scenario: Service name collision - similar but not exact match', async () => {
      const packageName = helper.context.packageName
      const similarServices = [
        `${packageName}/${packageName}.SimilarAccessibilityService`,
        `${packageName}/${packageName}.MyAccessibilityServiceExtended`,
        `${packageName}.similar/${packageName}.MyAccessibilityService`,
      ]

      helper.setEnabledServices(false, similarServices)

      const result = await isEnabled()

      expect(result).toBe(false)
      helper.verifyServiceDetectionCall()
    })

    test('Scenario: Different package name configurations', async () => {
      // Test with various realistic package names
      const testCases = [
        'com.mycompany.myapp',
        'org.example.accessibility',
        'net.developer.tool.accessibility',
        'io.github.user.accessibilityservice',
      ]

      for (const packageName of testCases) {
        const testHelper = new AndroidAccessibilityTestHelper(packageName)
        testHelper.setEnabledServices(true, [])

        // Update our mock to use this specific helper
        mockNativeModule.isEnabled.mockImplementation(async () => {
          const serviceName = testHelper.getExpectedServiceName(packageName)

          const enabledServices = testHelper.settings.Secure.getString(
            testHelper.context.contentResolver,
            testHelper.settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
          )

          if (!enabledServices || enabledServices === '') {
            return false
          }

          // Android services are colon-separated, so check for exact match
          // Also trim whitespace as the system may have formatting issues
          const serviceList = enabledServices.split(':').map((s) => s.trim())
          return serviceList.includes(serviceName)
        })

        const result = await isEnabled()

        expect(result).toBe(true)
        testHelper.verifyServiceDetectionCall()
      }
    })
  })

  describe('Permission Request Scenarios', () => {
    test('Scenario: Successful accessibility settings launch', async () => {
      helper.setupSuccessfulPermissionRequest()

      await expect(askPermission()).resolves.toBeUndefined()

      helper.verifyPermissionRequestCall()
    })

    test('Scenario: SecurityException - app lacks permission to start activity', async () => {
      helper.setupFailedPermissionRequest(
        'SecurityException: Permission denied'
      )

      await expect(askPermission()).rejects.toThrow(
        'Could not open accessibility settings: SecurityException: Permission denied'
      )
    })

    test('Scenario: ActivityNotFoundException - accessibility settings not available', async () => {
      helper.setupFailedPermissionRequest(
        'ActivityNotFoundException: No Activity found'
      )

      await expect(askPermission()).rejects.toThrow(
        'Could not open accessibility settings: ActivityNotFoundException: No Activity found'
      )
    })

    test('Scenario: General system error during intent launch', async () => {
      helper.setupFailedPermissionRequest('System error occurred')

      await expect(askPermission()).rejects.toThrow(
        'Could not open accessibility settings: System error occurred'
      )
    })
  })

  describe('Complete User Workflow Scenarios', () => {
    test('Scenario: Complete enable workflow - user installs app and enables service', async () => {
      // Step 1: App is newly installed, no services enabled
      helper.setNoServicesEnabled()
      expect(await isEnabled()).toBe(false)

      // Step 2: User requests permission
      helper.setupSuccessfulPermissionRequest()
      await expect(askPermission()).resolves.toBeUndefined()
      helper.verifyPermissionRequestCall()

      // Step 3: User enables our service in settings (simulated)
      helper.setEnabledServices(true, [])
      expect(await isEnabled()).toBe(true)
    })

    test('Scenario: Service gets disabled externally - user turns off in system settings', async () => {
      // Step 1: Service is initially enabled
      helper.setEnabledServices(true, ['com.other.service/.Service'])
      expect(await isEnabled()).toBe(true)

      // Step 2: User disables our service in system settings (simulated)
      helper.setEnabledServices(false, ['com.other.service/.Service'])
      expect(await isEnabled()).toBe(false)

      // Step 3: App detects change and can request permission again
      helper.setupSuccessfulPermissionRequest()
      await expect(askPermission()).resolves.toBeUndefined()
    })

    test('Scenario: Multiple accessibility apps - user manages several services', async () => {
      const multipleServices = [
        'com.android.talkback/com.google.android.marvin.talkback.TalkBackService',
        'com.google.android.apps.accessibility.voiceaccess/com.google.android.apps.accessibility.voiceaccess.JustSpeakService',
        'com.thirdparty.reader/com.thirdparty.reader.ReaderService',
      ]

      // Initially only other services are enabled
      helper.setEnabledServices(false, multipleServices)
      expect(await isEnabled()).toBe(false)

      // User adds our service to the mix
      helper.setEnabledServices(true, multipleServices)
      expect(await isEnabled()).toBe(true)

      // User removes a different service (our service remains)
      const reducedServices = multipleServices.slice(0, -1) // Remove last service
      helper.setEnabledServices(true, reducedServices)
      expect(await isEnabled()).toBe(true)
    })
  })

  describe('Android System Edge Cases', () => {
    test('Scenario: Extremely long services list - performance stress test', async () => {
      // Create a very long list of services (100+ services)
      const longServicesList = Array.from(
        { length: 100 },
        (_, i) =>
          `com.example.service${i}/com.example.service${i}.AccessibilityService`
      )

      helper.setEnabledServices(true, longServicesList)

      const result = await isEnabled()

      expect(result).toBe(true)
      helper.verifyServiceDetectionCall()
    })

    test('Scenario: Malformed system settings - corrupted accessibility services list', async () => {
      // Simulate corrupted or malformed entries
      const malformedList = [
        'invalid_format_entry',
        'com.example.testaccessibility/com.example.testaccessibility.MyAccessibilityService', // Our valid service
        '/missing_package.Service',
        'com.valid.service/', // Missing class name
        ':', // Empty entry
        'com.another.valid/com.another.valid.Service',
      ].join(':')

      helper.settings.Secure.getString.mockReturnValue(malformedList)

      const result = await isEnabled()

      expect(result).toBe(true) // Should still detect our valid service
      helper.verifyServiceDetectionCall()
    })

    test('Scenario: Service list with unusual whitespace and formatting', async () => {
      const serviceWithWhitespace = ` \t${helper.getExpectedServiceName()} \n `
      helper.settings.Secure.getString.mockReturnValue(serviceWithWhitespace)

      const result = await isEnabled()

      expect(result).toBe(true) // Should handle whitespace correctly
      helper.verifyServiceDetectionCall()
    })

    test('Scenario: Case sensitivity test - Android service names are case-sensitive', async () => {
      const packageName = helper.context.packageName
      const wrongCaseService = `${packageName}/${packageName}.myaccessibilityservice` // lowercase

      helper.settings.Secure.getString.mockReturnValue(wrongCaseService)

      const result = await isEnabled()

      expect(result).toBe(false) // Should not match due to case difference
      helper.verifyServiceDetectionCall()
    })
  })

  describe('Platform Integration Tests', () => {
    test('Scenario: Android API level compatibility - Settings.Secure access', async () => {
      // Test that we properly access the Settings.Secure API
      helper.setEnabledServices(true, [])

      await isEnabled()

      expect(helper.settings.Secure.getString).toHaveBeenCalledWith(
        helper.context.contentResolver,
        'enabled_accessibility_services'
      )
    })

    test('Scenario: Intent handling - proper accessibility settings intent', async () => {
      helper.setupSuccessfulPermissionRequest()

      await askPermission()

      // Verify that startActivity was called with proper intent structure
      expect(helper.context.startActivity).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'android.settings.ACCESSIBILITY_SETTINGS',
        })
      )
    })
  })
})
