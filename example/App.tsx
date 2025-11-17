import * as AccessibilityService from "expo-accessibility-service";
import type { AccessibilityEvent, AccessibilityEventSubscription } from "expo-accessibility-service";
import { useEffect, useState, useRef } from "react";
import { Button, Text, View, ScrollView, StyleSheet } from "react-native";

export default function App() {
  const [permissionStatus, setPermissionStatus] = useState("unknown");
  const [detectedServices, setDetectedServices] = useState<string[]>([]);
  const [configuredService, setConfiguredService] = useState<string | null>(null);
  const [isMonitoring, setIsMonitoring] = useState(false);
  const [recentEvents, setRecentEvents] = useState<AccessibilityEvent[]>([]);
  const subscriptionRef = useRef<AccessibilityEventSubscription | null>(null);

  useEffect(() => {
    initializeAccessibilityService();

    // Cleanup subscription on unmount
    return () => {
      if (subscriptionRef.current) {
        subscriptionRef.current.remove();
        subscriptionRef.current = null;
      }
    };
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

  const startMonitoring = () => {
    if (subscriptionRef.current) {
      console.log("Already monitoring");
      return;
    }

    try {
      subscriptionRef.current = AccessibilityService.addAccessibilityEventListener(
        (event: AccessibilityEvent) => {
          console.log("App changed:", event);
          setRecentEvents((prev) => [event, ...prev].slice(0, 10)); // Keep last 10 events
        }
      );
      setIsMonitoring(true);
    } catch (error) {
      console.error("Error starting monitoring:", error);
    }
  };

  const stopMonitoring = () => {
    if (subscriptionRef.current) {
      subscriptionRef.current.remove();
      subscriptionRef.current = null;
      setIsMonitoring(false);
    }
  };

  const clearEvents = () => {
    setRecentEvents([]);
  };

  const formatTimestamp = (timestamp: number) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
  };

  return (
    <ScrollView
      contentContainerStyle={{
        flexGrow: 1,
        padding: 20,
        gap: 20,
      }}
    >
      <Text style={{ fontSize: 24, fontWeight: "bold", textAlign: "center" }}>
        Accessibility Service Demo
      </Text>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Status: {permissionStatus}</Text>
        <Button title="Ask Permission" onPress={requestPermission} />
        <Button title="Refresh Status" onPress={checkAccessibilityPermission} />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>App Launch Monitoring</Text>
        <Text style={styles.monitoringStatus}>
          {isMonitoring ? "🟢 Monitoring Active" : "🔴 Monitoring Stopped"}
        </Text>
        <View style={styles.buttonRow}>
          <Button
            title="Start Monitoring"
            onPress={startMonitoring}
            disabled={isMonitoring || permissionStatus !== "enabled"}
          />
          <Button
            title="Stop Monitoring"
            onPress={stopMonitoring}
            disabled={!isMonitoring}
          />
        </View>
        {permissionStatus !== "enabled" && (
          <Text style={styles.hint}>
            Enable accessibility permission first
          </Text>
        )}
      </View>

      <View style={styles.section}>
        <View style={styles.buttonRow}>
          <Text style={styles.sectionTitle}>
            Recent Events ({recentEvents.length})
          </Text>
          {recentEvents.length > 0 && (
            <Button title="Clear" onPress={clearEvents} />
          )}
        </View>
        {recentEvents.length === 0 ? (
          <Text style={styles.hint}>
            No events yet. Start monitoring and switch between apps!
          </Text>
        ) : (
          recentEvents.map((event, index) => (
            <View key={`${event.timestamp}-${index}`} style={styles.eventCard}>
              <Text style={styles.eventPackage} numberOfLines={1}>
                📱 {event.packageName}
              </Text>
              <Text style={styles.eventClass} numberOfLines={1}>
                {event.className}
              </Text>
              <Text style={styles.eventTime}>
                {formatTimestamp(event.timestamp)}
              </Text>
            </View>
          ))
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Configuration</Text>
        <Text style={styles.configText}>
          Configured Service: {configuredService || "None (using default)"}
        </Text>
        <Text style={styles.sectionTitle}>
          Detected Services ({detectedServices.length})
        </Text>
        {detectedServices.length === 0 ? (
          <Text style={styles.hint}>
            No accessibility services found in manifest.{"\n"}
            Will use default: MyAccessibilityService
          </Text>
        ) : (
          detectedServices.map((service, index) => (
            <View key={service} style={styles.serviceItem}>
              <Text style={styles.serviceText} numberOfLines={1}>
                {service}
              </Text>
              <Button
                title={`Use Service ${index + 1}`}
                onPress={() => configureCustomService(service)}
              />
            </View>
          ))
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  section: {
    gap: 10,
    padding: 15,
    backgroundColor: "#f5f5f5",
    borderRadius: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "bold",
    textAlign: "center",
  },
  monitoringStatus: {
    fontSize: 14,
    textAlign: "center",
    fontWeight: "600",
  },
  buttonRow: {
    flexDirection: "row",
    justifyContent: "space-around",
    alignItems: "center",
    gap: 10,
  },
  hint: {
    fontSize: 12,
    fontStyle: "italic",
    textAlign: "center",
    color: "#666",
  },
  eventCard: {
    padding: 10,
    backgroundColor: "#fff",
    borderRadius: 6,
    borderLeftWidth: 3,
    borderLeftColor: "#007AFF",
  },
  eventPackage: {
    fontSize: 14,
    fontWeight: "600",
    marginBottom: 4,
  },
  eventClass: {
    fontSize: 12,
    color: "#666",
    marginBottom: 4,
  },
  eventTime: {
    fontSize: 11,
    color: "#999",
  },
  configText: {
    textAlign: "center",
    fontSize: 12,
    color: "#666",
  },
  serviceItem: {
    flexDirection: "column",
    gap: 5,
    alignItems: "center",
  },
  serviceText: {
    fontSize: 12,
    textAlign: "center",
  },
});
