import { isEnabled, askPermission } from '../index';
import ExpoAccessibilityServiceModule from '../ExpoAccessibilityServiceModule';

jest.mock('../ExpoAccessibilityServiceModule', () => ({
  isEnabled: jest.fn(),
  askPermission: jest.fn(),
}));

const mockModule = ExpoAccessibilityServiceModule as jest.Mocked<typeof ExpoAccessibilityServiceModule>;

describe('ExpoAccessibilityService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('isEnabled', () => {
    it('should return true when accessibility service is enabled', async () => {
      mockModule.isEnabled.mockResolvedValue(true);

      const result = await isEnabled();

      expect(result).toBe(true);
      expect(mockModule.isEnabled).toHaveBeenCalledTimes(1);
      expect(mockModule.isEnabled).toHaveBeenCalledWith();
    });

    it('should return false when accessibility service is disabled', async () => {
      mockModule.isEnabled.mockResolvedValue(false);

      const result = await isEnabled();

      expect(result).toBe(false);
      expect(mockModule.isEnabled).toHaveBeenCalledTimes(1);
      expect(mockModule.isEnabled).toHaveBeenCalledWith();
    });

    it('should propagate errors from native module', async () => {
      const error = new Error('Native module error');
      mockModule.isEnabled.mockRejectedValue(error);

      await expect(isEnabled()).rejects.toThrow('Native module error');
      expect(mockModule.isEnabled).toHaveBeenCalledTimes(1);
    });
  });

  describe('askPermission', () => {
    it('should call native module askPermission method', async () => {
      mockModule.askPermission.mockResolvedValue(undefined);

      await askPermission();

      expect(mockModule.askPermission).toHaveBeenCalledTimes(1);
      expect(mockModule.askPermission).toHaveBeenCalledWith();
    });

    it('should propagate errors from native module', async () => {
      const error = new Error('Permission denied');
      mockModule.askPermission.mockRejectedValue(error);

      await expect(askPermission()).rejects.toThrow('Permission denied');
      expect(mockModule.askPermission).toHaveBeenCalledTimes(1);
    });

    it('should resolve without returning a value', async () => {
      mockModule.askPermission.mockResolvedValue(undefined);

      const result = await askPermission();

      expect(result).toBeUndefined();
      expect(mockModule.askPermission).toHaveBeenCalledTimes(1);
    });
  });
});