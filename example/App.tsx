import * as AccessibilityService from "expo-accessibility-service";
import { useEffect, useState } from "react";
import { Button, Text, View, ScrollView } from "react-native";

export default function App() {
  const [permissionStatus, setPermissionStatus] = useState("unknown");
  const [detectedServices, setDetectedServices] = useState<string[]>([]);
  const [configuredService, setConfiguredService] = useState<string | null>(null);

  useEffect(() => {
    initializeAccessibilityService();
  }, []);

  const initializeAccessibilityService = async () => {
    try {
      // Discover available accessibility services
      const services = await AccessibilityService.getDetectedServices();
      setDetectedServices(services);
      
      // Configure service if available
      if (services.length > 0) {
        const preferredService = services[0]; // Use first detected service
        await AccessibilityService.setServiceClassName(preferredService);
        setConfiguredService(preferredService);
      }
      
      // Check initial status
      await checkAccessibilityPermission();
    } catch (error) {
      console.error("Error initializing accessibility service:", error);
    }
  };

  const checkAccessibilityPermission = async () => {
    try {
      const isEnabled = await AccessibilityService.isEnabled();
      setPermissionStatus(isEnabled ? "enabled" : "disabled");
    } catch (error) {
      console.error("Error checking accessibility permission:", error);
    }
  };

  const requestPermission = async () => {
    try {
      await AccessibilityService.askPermission();
      // Note: After returning from settings, you may want to recheck the status
      setTimeout(checkAccessibilityPermission, 1000);
    } catch (error) {
      console.error("Error requesting accessibility permission:", error);
    }
  };

  const configureCustomService = async (serviceName: string) => {
    try {
      await AccessibilityService.setServiceClassName(serviceName);
      setConfiguredService(serviceName);
      await checkAccessibilityPermission();
    } catch (error) {
      console.error("Error configuring service:", error);
    }
  };

  return (
    <ScrollView
      contentContainerStyle={{
        flexGrow: 1,
        padding: 20,
        gap: 20,
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Text style={{ fontSize: 24, fontWeight: "bold" }}>
        Accessibility Service Configuration
      </Text>
      
      <View style={{ alignItems: "center", gap: 10 }}>
        <Text style={{ fontSize: 18 }}>Status: {permissionStatus}</Text>
        <Button title="Ask Permission" onPress={requestPermission} />
        <Button title="Refresh Status" onPress={checkAccessibilityPermission} />
      </View>

      <View style={{ alignItems: "center", gap: 10 }}>
        <Text style={{ fontSize: 16, fontWeight: "bold" }}>Configuration</Text>
        <Text style={{ textAlign: "center" }}>
          Configured Service: {configuredService || "None (using default)"}
        </Text>
      </View>

      <View style={{ alignItems: "center", gap: 10 }}>
        <Text style={{ fontSize: 16, fontWeight: "bold" }}>
          Detected Services ({detectedServices.length})
        </Text>
        {detectedServices.length === 0 ? (
          <Text style={{ fontStyle: "italic", textAlign: "center" }}>
            No accessibility services found in manifest.{"\n"}
            Will use default: MyAccessibilityService
          </Text>
        ) : (
          detectedServices.map((service, index) => (
            <View key={service} style={{ alignItems: "center", gap: 5 }}>
              <Text style={{ fontSize: 12, textAlign: "center" }}>{service}</Text>
              <Button
                title={`Use Service ${index + 1}`}
                onPress={() => configureCustomService(service)}
              />
            </View>
          ))
        )}
      </View>

      <View style={{ alignItems: "center", gap: 10 }}>
        <Text style={{ fontSize: 14, fontStyle: "italic", textAlign: "center" }}>
          This example demonstrates the new configurable service detection.{"\n"}
          You can now use custom accessibility service names!
        </Text>
      </View>
    </ScrollView>
  );
}
