import { isEnabled, askPermission } from '../index';

// Mock the native module
jest.mock('../ExpoAccessibilityServiceModule', () => ({
  isEnabled: jest.fn(),
  askPermission: jest.fn(),
}));

import ExpoAccessibilityServiceModule from '../ExpoAccessibilityServiceModule';
const mockModule = ExpoAccessibilityServiceModule as jest.Mocked<typeof ExpoAccessibilityServiceModule>;

// Mock Android-specific system APIs
const mockSettings = {
  Secure: {
    ENABLED_ACCESSIBILITY_SERVICES: 'enabled_accessibility_services',
    getString: jest.fn(),
  },
};

const mockIntent = jest.fn();
const mockContext = {
  packageName: 'com.example.testaccessibility',
  contentResolver: {},
  startActivity: jest.fn(),
};

describe('Android Accessibility Service Integration Tests', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // Reset mock settings
    mockSettings.Secure.getString.mockReturnValue('');
    mockContext.startActivity.mockClear();
    mockIntent.mockClear();

    // Set up Android-specific mock behavior
    mockModule.isEnabled.mockImplementation(async () => {
  // Simulate Android accessibility service detection logic
  const packageName = mockContext.packageName;
  const serviceName = `${packageName}/${packageName}.MyAccessibilityService`;
  
  const enabledServices = mockSettings.Secure.getString(
    mockContext.contentResolver,
    mockSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
  );
  
  if (!enabledServices || enabledServices === '') {
    return false;
  }
  
      return enabledServices.includes(serviceName);
    });

    mockModule.askPermission.mockImplementation(async () => {
      // Simulate Android intent to open accessibility settings
      try {
        const intent = mockIntent('android.settings.ACCESSIBILITY_SETTINGS');
        mockContext.startActivity(intent);
        return undefined;
      } catch (error) {
        throw new Error(`Could not open accessibility settings: ${(error as Error).message}`);
      }
    });
  });

  describe('Service Detection', () => {
    it('should detect when no accessibility services are enabled', async () => {
      // Simulate empty enabled services string
      mockSettings.Secure.getString.mockReturnValue('');

      const result = await isEnabled();
      
      expect(result).toBe(false);
      expect(mockSettings.Secure.getString).toHaveBeenCalledWith(
        mockContext.contentResolver,
        mockSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      );
    });

    it('should detect when null accessibility services are returned', async () => {
      // Simulate null enabled services
      mockSettings.Secure.getString.mockReturnValue(null);

      const result = await isEnabled();
      
      expect(result).toBe(false);
    });

    it('should detect when our accessibility service is enabled', async () => {
      const packageName = mockContext.packageName;
      const serviceName = `${packageName}/${packageName}.MyAccessibilityService`;
      const enabledServices = `com.other.service/.SomeService:${serviceName}:com.another/.Service`;
      
      mockSettings.Secure.getString.mockReturnValue(enabledServices);

      const result = await isEnabled();
      
      expect(result).toBe(true);
    });

    it('should detect when our service is not in enabled services list', async () => {
      const enabledServices = 'com.other.service/.SomeService:com.another/.Service';
      mockSettings.Secure.getString.mockReturnValue(enabledServices);

      const result = await isEnabled();
      
      expect(result).toBe(false);
    });

    it('should handle service name format correctly for different package names', async () => {
      // Test with different package name
      const originalPackageName = mockContext.packageName;
      mockContext.packageName = 'com.mycompany.myapp';
      
      const serviceName = `${mockContext.packageName}/${mockContext.packageName}.MyAccessibilityService`;
      mockSettings.Secure.getString.mockReturnValue(serviceName);

      const result = await isEnabled();
      
      expect(result).toBe(true);
      
      // Restore original package name
      mockContext.packageName = originalPackageName;
    });

    it('should handle partial service name matches correctly', async () => {
      // Ensure partial matches don't return false positives
      const packageName = mockContext.packageName;
      const similarServiceName = `${packageName}/${packageName}.SimilarService`;
      mockSettings.Secure.getString.mockReturnValue(similarServiceName);

      const result = await isEnabled();
      
      expect(result).toBe(false);
    });

    it('should handle service detection with colon-separated service list', async () => {
      const packageName = mockContext.packageName;
      const ourService = `${packageName}/${packageName}.MyAccessibilityService`;
      const enabledServices = [
        'com.android.talkback/.TalkBackService',
        ourService,
        'com.google.android.marvin.talkback/.TalkBackService'
      ].join(':');
      
      mockSettings.Secure.getString.mockReturnValue(enabledServices);

      const result = await isEnabled();
      
      expect(result).toBe(true);
    });
  });

  describe('Permission Request', () => {
    it('should successfully open accessibility settings', async () => {
      mockContext.startActivity.mockImplementation(() => {});

      await expect(askPermission()).resolves.toBeUndefined();
      
      expect(mockContext.startActivity).toHaveBeenCalledTimes(1);
    });

    it('should handle intent creation errors', async () => {
      mockIntent.mockImplementation(() => {
        throw new Error('Intent creation failed');
      });

      await expect(askPermission()).rejects.toThrow('Could not open accessibility settings: Intent creation failed');
    });

    it('should handle startActivity errors', async () => {
      mockIntent.mockReturnValue({ action: 'android.settings.ACCESSIBILITY_SETTINGS' });
      mockContext.startActivity.mockImplementation(() => {
        throw new Error('Activity not found');
      });

      await expect(askPermission()).rejects.toThrow('Could not open accessibility settings: Activity not found');
    });

    it('should handle SecurityException when starting accessibility settings', async () => {
      mockIntent.mockReturnValue({ action: 'android.settings.ACCESSIBILITY_SETTINGS' });
      mockContext.startActivity.mockImplementation(() => {
        throw new Error('Permission denied');
      });

      await expect(askPermission()).rejects.toThrow('Could not open accessibility settings: Permission denied');
    });
  });

  describe('Real-world Android Scenarios', () => {
    it('should handle typical Android accessibility services list format', async () => {
      // Real Android format: service_package/service_class:another_package/class
      const realWorldEnabledServices = 'com.android.talkback/com.google.android.marvin.talkback.TalkBackService:com.example.testaccessibility/com.example.testaccessibility.MyAccessibilityService:com.google.android.apps.accessibility.voiceaccess/com.google.android.apps.accessibility.voiceaccess.JustSpeakService';
      
      mockSettings.Secure.getString.mockReturnValue(realWorldEnabledServices);

      const result = await isEnabled();
      
      expect(result).toBe(true);
    });

    it('should handle empty spaces in services list', async () => {
      const servicesWithSpaces = ' com.example.testaccessibility/com.example.testaccessibility.MyAccessibilityService ';
      
      mockSettings.Secure.getString.mockReturnValue(servicesWithSpaces);

      const result = await isEnabled();
      
      expect(result).toBe(true);
    });

    it('should handle single service in list', async () => {
      const singleService = 'com.example.testaccessibility/com.example.testaccessibility.MyAccessibilityService';
      
      mockSettings.Secure.getString.mockReturnValue(singleService);

      const result = await isEnabled();
      
      expect(result).toBe(true);
    });

    it('should simulate complete enable/disable workflow', async () => {
      // Initially disabled
      mockSettings.Secure.getString.mockReturnValue('');
      expect(await isEnabled()).toBe(false);

      // User opens settings
      mockIntent.mockReturnValue({ action: 'android.settings.ACCESSIBILITY_SETTINGS' });
      mockContext.startActivity.mockImplementation(() => {}); // Reset to success
      await askPermission();
      expect(mockContext.startActivity).toHaveBeenCalledTimes(1);

      // User enables the service (simulated)
      const packageName = mockContext.packageName;
      const serviceName = `${packageName}/${packageName}.MyAccessibilityService`;
      mockSettings.Secure.getString.mockReturnValue(serviceName);
      
      // Now service is enabled
      expect(await isEnabled()).toBe(true);

      // User disables the service (simulated)
      mockSettings.Secure.getString.mockReturnValue('com.other.service/.OtherService');
      
      // Service is now disabled
      expect(await isEnabled()).toBe(false);
    });
  });

  describe('Android System Integration Edge Cases', () => {
    it('should handle undefined contentResolver', async () => {
      const originalResolver = mockContext.contentResolver;
      (mockContext as any).contentResolver = null;

      // Should still attempt to call Settings.Secure.getString
      await isEnabled();
      
      mockContext.contentResolver = originalResolver;
    });

    it('should handle very long service lists', async () => {
      // Create a very long list of services
      const longServiceList = Array.from({ length: 50 }, (_, i) => 
        `com.example.service${i}/com.example.service${i}.AccessibilityService`
      ).join(':') + ':com.example.testaccessibility/com.example.testaccessibility.MyAccessibilityService';
      
      mockSettings.Secure.getString.mockReturnValue(longServiceList);

      const result = await isEnabled();
      
      expect(result).toBe(true);
    });

    it('should handle malformed service names in system settings', async () => {
      const malformedServices = 'invalid_format:com.example.testaccessibility/com.example.testaccessibility.MyAccessibilityService:another_invalid';
      
      mockSettings.Secure.getString.mockReturnValue(malformedServices);

      const result = await isEnabled();
      
      expect(result).toBe(true);
    });
  });
});