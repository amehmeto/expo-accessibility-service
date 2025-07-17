import * as AccessibilityService from "expo-accessibility-service";
import { useEffect, useState } from "react";
import { Button, Text, View } from "react-native";

export default function App() {
  const [permissionStatus, setPermissionStatus] = useState("unknown");

  useEffect(() => {
    checkAccessibilityPermission();
  }, []);

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

  return (
    <View
      style={{
        flex: 1,
        rowGap: 20,
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Text style={{ fontSize: 24, fontWeight: "bold" }}>
        Accessibility Service
      </Text>
      <Text style={{ fontSize: 18 }}>Is Enabled?: {permissionStatus}</Text>
      <Button title="Ask Permission" onPress={requestPermission} />
    </View>
  );
}
