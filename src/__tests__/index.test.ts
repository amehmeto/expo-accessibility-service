import ExpoAccessibilityServiceModule from '../ExpoAccessibilityServiceModule'
import {
  isEnabled,
  askPermission,
  setServiceClassName,
  getDetectedServices,
  addAccessibilityEventListener,
  AccessibilityEvent,
} from '../index'

// Mock subscription object
const mockSubscription = {
  remove: jest.fn(),
}

jest.mock('../ExpoAccessibilityServiceModule', () => ({
  isEnabled: jest.fn(),
  askPermission: jest.fn(),
  setServiceClassName: jest.fn(),
  getDetectedServices: jest.fn(),
  addListener: jest.fn(() => mockSubscription),
}))

const mockModule = ExpoAccessibilityServiceModule as jest.Mocked<
  typeof ExpoAccessibilityServiceModule
>

describe('ExpoAccessibilityService', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  describe('isEnabled', () => {
    it('should return true when accessibility service is enabled', async () => {
      mockModule.isEnabled.mockResolvedValue(true)

      const result = await isEnabled()

      expect(result).toBe(true)
      expect(mockModule.isEnabled).toHaveBeenCalledTimes(1)
      expect(mockModule.isEnabled).toHaveBeenCalledWith()
    })

    it('should return false when accessibility service is disabled', async () => {
      mockModule.isEnabled.mockResolvedValue(false)

      const result = await isEnabled()

      expect(result).toBe(false)
      expect(mockModule.isEnabled).toHaveBeenCalledTimes(1)
      expect(mockModule.isEnabled).toHaveBeenCalledWith()
    })

    it('should propagate errors from native module', async () => {
      const error = new Error('Native module error')
      mockModule.isEnabled.mockRejectedValue(error)

      await expect(isEnabled()).rejects.toThrow('Native module error')
      expect(mockModule.isEnabled).toHaveBeenCalledTimes(1)
    })
  })

  describe('askPermission', () => {
    it('should call native module askPermission method', async () => {
      mockModule.askPermission.mockResolvedValue(undefined)

      await askPermission()

      expect(mockModule.askPermission).toHaveBeenCalledTimes(1)
      expect(mockModule.askPermission).toHaveBeenCalledWith()
    })

    it('should propagate errors from native module', async () => {
      const error = new Error('Permission denied')
      mockModule.askPermission.mockRejectedValue(error)

      await expect(askPermission()).rejects.toThrow('Permission denied')
      expect(mockModule.askPermission).toHaveBeenCalledTimes(1)
    })

    it('should resolve without returning a value', async () => {
      mockModule.askPermission.mockResolvedValue(undefined)

      const result = await askPermission()

      expect(result).toBeUndefined()
      expect(mockModule.askPermission).toHaveBeenCalledTimes(1)
    })
  })

  describe('setServiceClassName', () => {
    it('should call native module setServiceClassName method with correct parameters', async () => {
      const className = 'com.example.MyCustomAccessibilityService'
      mockModule.setServiceClassName.mockResolvedValue(undefined)

      await setServiceClassName(className)

      expect(mockModule.setServiceClassName).toHaveBeenCalledTimes(1)
      expect(mockModule.setServiceClassName).toHaveBeenCalledWith(className)
    })

    it('should propagate errors from native module', async () => {
      const error = new Error('Invalid class name')
      mockModule.setServiceClassName.mockRejectedValue(error)

      await expect(setServiceClassName('invalid')).rejects.toThrow(
        'Invalid class name',
      )
      expect(mockModule.setServiceClassName).toHaveBeenCalledTimes(1)
    })

    it('should resolve without returning a value', async () => {
      mockModule.setServiceClassName.mockResolvedValue(undefined)

      const result = await setServiceClassName('com.example.Service')

      expect(result).toBeUndefined()
      expect(mockModule.setServiceClassName).toHaveBeenCalledTimes(1)
    })
  })

  describe('getDetectedServices', () => {
    it('should return array of detected services', async () => {
      const expectedServices = ['com.example.Service1', 'com.example.Service2']
      mockModule.getDetectedServices.mockResolvedValue(expectedServices)

      const result = await getDetectedServices()

      expect(result).toEqual(expectedServices)
      expect(mockModule.getDetectedServices).toHaveBeenCalledTimes(1)
      expect(mockModule.getDetectedServices).toHaveBeenCalledWith()
    })

    it('should return empty array when no services detected', async () => {
      mockModule.getDetectedServices.mockResolvedValue([])

      const result = await getDetectedServices()

      expect(result).toEqual([])
      expect(mockModule.getDetectedServices).toHaveBeenCalledTimes(1)
    })

    it('should propagate errors from native module', async () => {
      const error = new Error('Manifest read error')
      mockModule.getDetectedServices.mockRejectedValue(error)

      await expect(getDetectedServices()).rejects.toThrow('Manifest read error')
      expect(mockModule.getDetectedServices).toHaveBeenCalledTimes(1)
    })
  })

  describe('addAccessibilityEventListener', () => {
    it('should register listener and return subscription with remove method', () => {
      const listener = jest.fn()

      const subscription = addAccessibilityEventListener(listener)

      expect(mockModule.addListener).toHaveBeenCalledTimes(1)
      expect(mockModule.addListener).toHaveBeenCalledWith(
        'onAccessibilityEvent',
        listener,
      )
      expect(subscription).toHaveProperty('remove')
      expect(typeof subscription.remove).toBe('function')
    })

    it('should call native remove when subscription.remove is called', () => {
      const listener = jest.fn()

      const subscription = addAccessibilityEventListener(listener)
      subscription.remove()

      expect(mockSubscription.remove).toHaveBeenCalledTimes(1)
    })

    it('should support multiple simultaneous listeners', () => {
      const listener1 = jest.fn()
      const listener2 = jest.fn()
      const listener3 = jest.fn()

      const subscription1 = addAccessibilityEventListener(listener1)
      const subscription2 = addAccessibilityEventListener(listener2)
      const subscription3 = addAccessibilityEventListener(listener3)

      expect(mockModule.addListener).toHaveBeenCalledTimes(3)
      expect(subscription1).not.toBe(subscription2)
      expect(subscription2).not.toBe(subscription3)
    })

    it('should allow removing individual subscriptions without affecting others', () => {
      const listener1 = jest.fn()
      const listener2 = jest.fn()

      const subscription1 = addAccessibilityEventListener(listener1)
      addAccessibilityEventListener(listener2)

      subscription1.remove()

      expect(mockSubscription.remove).toHaveBeenCalledTimes(1)
      // listener2 should still be registered
      expect(mockModule.addListener).toHaveBeenCalledWith(
        'onAccessibilityEvent',
        listener2,
      )
    })

    it('should pass correct event data to listener', () => {
      const listener = jest.fn()
      const mockEvent: AccessibilityEvent = {
        packageName: 'com.example.app',
        className: 'com.example.app.MainActivity',
        timestamp: 1234567890,
      }

      addAccessibilityEventListener(listener)

      // Simulate event emission by calling the listener directly
      listener(mockEvent)

      expect(listener).toHaveBeenCalledWith(mockEvent)
      expect(listener).toHaveBeenCalledTimes(1)
    })

    it('should handle multiple events on same listener', () => {
      const listener = jest.fn()
      const event1: AccessibilityEvent = {
        packageName: 'com.example.app1',
        className: 'com.example.app1.MainActivity',
        timestamp: 1234567890,
      }
      const event2: AccessibilityEvent = {
        packageName: 'com.example.app2',
        className: 'com.example.app2.MainActivity',
        timestamp: 1234567891,
      }

      addAccessibilityEventListener(listener)

      // Simulate multiple event emissions
      listener(event1)
      listener(event2)

      expect(listener).toHaveBeenCalledTimes(2)
      expect(listener).toHaveBeenNthCalledWith(1, event1)
      expect(listener).toHaveBeenNthCalledWith(2, event2)
    })
  })
})
