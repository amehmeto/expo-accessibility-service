import { isEnabled, askPermission, setServiceClassName, getDetectedServices } from '../index';
import ExpoAccessibilityServiceModule from '../ExpoAccessibilityServiceModule';

jest.mock('../ExpoAccessibilityServiceModule', () => ({
  isEnabled: jest.fn(),
  askPermission: jest.fn(),
  setServiceClassName: jest.fn(),
  getDetectedServices: jest.fn(),
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

  describe('setServiceClassName', () => {
    it('should call native module setServiceClassName method with correct parameters', async () => {
      const className = 'com.example.MyCustomAccessibilityService';
      mockModule.setServiceClassName.mockResolvedValue(undefined);

      await setServiceClassName(className);

      expect(mockModule.setServiceClassName).toHaveBeenCalledTimes(1);
      expect(mockModule.setServiceClassName).toHaveBeenCalledWith(className);
    });

    it('should propagate errors from native module', async () => {
      const error = new Error('Invalid class name');
      mockModule.setServiceClassName.mockRejectedValue(error);

      await expect(setServiceClassName('invalid')).rejects.toThrow('Invalid class name');
      expect(mockModule.setServiceClassName).toHaveBeenCalledTimes(1);
    });

    it('should resolve without returning a value', async () => {
      mockModule.setServiceClassName.mockResolvedValue(undefined);

      const result = await setServiceClassName('com.example.Service');

      expect(result).toBeUndefined();
      expect(mockModule.setServiceClassName).toHaveBeenCalledTimes(1);
    });
  });

  describe('getDetectedServices', () => {
    it('should return array of detected services', async () => {
      const expectedServices = ['com.example.Service1', 'com.example.Service2'];
      mockModule.getDetectedServices.mockResolvedValue(expectedServices);

      const result = await getDetectedServices();

      expect(result).toEqual(expectedServices);
      expect(mockModule.getDetectedServices).toHaveBeenCalledTimes(1);
      expect(mockModule.getDetectedServices).toHaveBeenCalledWith();
    });

    it('should return empty array when no services detected', async () => {
      mockModule.getDetectedServices.mockResolvedValue([]);

      const result = await getDetectedServices();

      expect(result).toEqual([]);
      expect(mockModule.getDetectedServices).toHaveBeenCalledTimes(1);
    });

    it('should propagate errors from native module', async () => {
      const error = new Error('Manifest read error');
      mockModule.getDetectedServices.mockRejectedValue(error);

      await expect(getDetectedServices()).rejects.toThrow('Manifest read error');
      expect(mockModule.getDetectedServices).toHaveBeenCalledTimes(1);
    });
  });
});