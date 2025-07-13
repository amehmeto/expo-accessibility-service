import ExpoModulesCore

public class ExpoAccessibilityServiceModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoAccessibilityService")

    AsyncFunction("isEnabled") { (promise: Promise) in
      promise.reject("ERROR", "This method is not implemented on iOS.")
    }

    AsyncFunction("askPermission") { (promise: Promise) in
      promise.reject("ERROR", "This method is not implemented on iOS.")
    }
  }
}
