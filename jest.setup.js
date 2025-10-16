// Mock expo modules
jest.mock('expo', () => ({
  NativeModule: class {},
  requireNativeModule: jest.fn(),
}));