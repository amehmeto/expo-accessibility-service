import * as AccessibilityService from 'expo-accessibility-service'
import type {
  AccessibilityEvent,
  AccessibilityEventSubscription,
  UrlBarEvent,
} from 'expo-accessibility-service'
import { ReactNode, useCallback, useEffect, useRef, useState } from 'react'
import {
  AppState,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native'
import {
  SafeAreaProvider,
  useSafeAreaInsets,
} from 'react-native-safe-area-context'
import { Badge } from './components/Badge'
import { Button } from './components/Button'
import { Card } from './components/Card'
import { Snackbar } from './components/Snackbar'
import {
  MAX_CONTENT_WIDTH,
  shape,
  spacing,
  typography,
  useTheme,
} from './theme'

/** How many events each list keeps. The lists are bounded on purpose. */
const MAX_KEPT_EVENTS = 10

type Check = 'checking' | 'yes' | 'no'

type Recorded<T> = { id: number; event: T }

export default function App() {
  return (
    <SafeAreaProvider>
      <DemoScreen />
    </SafeAreaProvider>
  )
}

function DemoScreen() {
  const { colors, isDark } = useTheme()
  const insets = useSafeAreaInsets()

  const [permission, setPermission] = useState<Check>('checking')
  const [serviceRunning, setServiceRunning] = useState<Check>('checking')
  const [detectedServices, setDetectedServices] = useState<string[]>([])
  const [configuredService, setConfiguredService] = useState<string | null>(
    null,
  )
  const [isMonitoring, setIsMonitoring] = useState(false)
  const [appEvents, setAppEvents] = useState<Recorded<AccessibilityEvent>[]>([])
  const [urlBarEvents, setUrlBarEvents] = useState<Recorded<UrlBarEvent>[]>([])
  const [error, setError] = useState<string | null>(null)

  const appSubscriptionRef = useRef<AccessibilityEventSubscription | null>(null)
  const urlBarSubscriptionRef = useRef<AccessibilityEventSubscription | null>(
    null,
  )
  // A monotonic id per received event. React keys must not come from the array
  // index: these lists prepend, so every index moves on every event.
  const nextEventIdRef = useRef(0)
  const takeEventId = () => nextEventIdRef.current++

  const reportError = useCallback((action: string, cause: unknown) => {
    const detail = cause instanceof Error ? cause.message : String(cause)
    setError(`${action} failed. ${detail}`)
  }, [])

  const dismissError = useCallback(() => setError(null), [])

  const refreshStatus = useCallback(async () => {
    try {
      const [enabled, running] = await Promise.all([
        AccessibilityService.isEnabled(),
        AccessibilityService.isServiceRunning(),
      ])
      setPermission(enabled ? 'yes' : 'no')
      setServiceRunning(running ? 'yes' : 'no')
    } catch (cause) {
      reportError('Reading the service status', cause)
    }
  }, [reportError])

  const loadDetectedServices = useCallback(async () => {
    try {
      const services = await AccessibilityService.getDetectedServices()
      setDetectedServices(services)

      if (services.length > 0) {
        await AccessibilityService.setServiceClassName(services[0])
        setConfiguredService(services[0])
      }
    } catch (cause) {
      reportError('Reading the services in the manifest', cause)
    }
  }, [reportError])

  useEffect(() => {
    const initialise = async () => {
      await loadDetectedServices()
      await refreshStatus()
    }

    void initialise()
  }, [loadDetectedServices, refreshStatus])

  // The user grants the permission in the system Settings app, so the only
  // reliable moment to re-read it is the return to this app. A fixed timer
  // cannot know when that happens.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') void refreshStatus()
    })

    return () => subscription.remove()
  }, [refreshStatus])

  const stopMonitoring = useCallback(() => {
    appSubscriptionRef.current?.remove()
    appSubscriptionRef.current = null
    urlBarSubscriptionRef.current?.remove()
    urlBarSubscriptionRef.current = null
    setIsMonitoring(false)
  }, [])

  const startMonitoring = useCallback(() => {
    if (appSubscriptionRef.current !== null) return

    try {
      appSubscriptionRef.current =
        AccessibilityService.addAccessibilityEventListener((event) => {
          setAppEvents((previous) =>
            [{ id: takeEventId(), event }, ...previous].slice(
              0,
              MAX_KEPT_EVENTS,
            ),
          )
        })

      urlBarSubscriptionRef.current =
        AccessibilityService.addUrlBarChangeListener((event) => {
          setUrlBarEvents((previous) =>
            [{ id: takeEventId(), event }, ...previous].slice(
              0,
              MAX_KEPT_EVENTS,
            ),
          )
        })

      setIsMonitoring(true)
    } catch (cause) {
      stopMonitoring()
      reportError('Starting the listeners', cause)
    }
  }, [reportError, stopMonitoring])

  useEffect(() => stopMonitoring, [stopMonitoring])

  const askPermission = async () => {
    try {
      await AccessibilityService.askPermission()
    } catch (cause) {
      reportError('Opening the accessibility settings', cause)
    }
  }

  const openAppDetails = async () => {
    try {
      await AccessibilityService.openAppDetailsSettings()
    } catch (cause) {
      reportError('Opening the app details', cause)
    }
  }

  const emitCurrentApp = async () => {
    try {
      await AccessibilityService.emitCurrentForegroundApp()
    } catch (cause) {
      reportError('Emitting the current foreground app', cause)
    }
  }

  const useService = async (className: string) => {
    try {
      await AccessibilityService.setServiceClassName(className)
      setConfiguredService(className)
      await refreshStatus()
    } catch (cause) {
      reportError('Configuring the service', cause)
    }
  }

  const isGranted = permission === 'yes'

  return (
    <View style={[styles.root, { backgroundColor: colors.background }]}>
      <StatusBar
        barStyle={isDark ? 'light-content' : 'dark-content'}
        translucent
      />

      <ScrollView
        contentContainerStyle={[
          styles.scrollContent,
          {
            // Edge-to-edge is on, so the app owes the system bars their space.
            paddingTop: insets.top + spacing.lg,
            paddingBottom: insets.bottom + spacing.xxl,
            paddingLeft: insets.left + spacing.lg,
            paddingRight: insets.right + spacing.lg,
          },
        ]}
      >
        <View style={styles.content}>
          <Text
            accessibilityRole="header"
            style={[typography.headlineSmall, { color: colors.onSurface }]}
          >
            Accessibility Service
          </Text>

          <Card title="Status">
            <View accessibilityLiveRegion="polite" style={styles.badgeRow}>
              <Badge
                label={
                  {
                    checking: 'Checking permission',
                    yes: 'Permission granted',
                    no: 'Permission not granted',
                  }[permission]
                }
                tone={
                  permission === 'yes'
                    ? 'active'
                    : permission === 'no'
                    ? 'alert'
                    : 'neutral'
                }
              />
              <Badge
                label={
                  {
                    checking: 'Checking service',
                    yes: 'Service running',
                    no: 'Service stopped',
                  }[serviceRunning]
                }
                tone={serviceRunning === 'yes' ? 'active' : 'neutral'}
              />
            </View>

            {isGranted ? (
              <View style={styles.actionRow}>
                <Button
                  label="Check again"
                  onPress={refreshStatus}
                  variant="outlined"
                />
                <Button
                  label="App info"
                  onPress={openAppDetails}
                  variant="text"
                />
              </View>
            ) : (
              <>
                <Text
                  style={[
                    typography.bodyMedium,
                    { color: colors.onSurfaceVariant },
                  ]}
                >
                  Android grants this permission in its own Settings app. Open
                  the settings, find this app in the list, then turn the service
                  on. This screen updates when you come back.
                </Text>
                <Button
                  label="Open accessibility settings"
                  onPress={askPermission}
                  hint="Opens the Android settings app"
                  fullWidth
                />
                <Button
                  label="Check again"
                  onPress={refreshStatus}
                  variant="text"
                />
              </>
            )}
          </Card>

          {isGranted && (
            <Card
              title="Monitoring"
              trailing={
                <Badge
                  label={isMonitoring ? 'Listening' : 'Paused'}
                  tone={isMonitoring ? 'active' : 'neutral'}
                />
              }
            >
              <Text
                style={[
                  typography.bodyMedium,
                  { color: colors.onSurfaceVariant },
                ]}
              >
                One switch starts both listeners: foreground apps, and the
                address bar in supported browsers.
              </Text>
              <Button
                label={isMonitoring ? 'Stop monitoring' : 'Start monitoring'}
                onPress={isMonitoring ? stopMonitoring : startMonitoring}
                variant={isMonitoring ? 'tonal' : 'filled'}
                fullWidth
              />
              {isMonitoring && (
                <Button
                  label="Emit current app"
                  onPress={emitCurrentApp}
                  hint="Reports the app on screen without waiting for a change"
                  variant="text"
                />
              )}
            </Card>
          )}

          <Card
            title="Foreground apps"
            trailing={
              appEvents.length > 0 ? (
                <Button
                  label="Clear"
                  onPress={() => setAppEvents([])}
                  variant="text"
                />
              ) : undefined
            }
          >
            {appEvents.length === 0 ? (
              <EmptyState
                text={
                  isMonitoring
                    ? 'Waiting. Switch to another app to see an event here.'
                    : 'Start monitoring to record app changes.'
                }
              />
            ) : (
              appEvents.map(({ id, event }) => (
                <Row key={id}>
                  <Text
                    numberOfLines={2}
                    style={[typography.bodyLarge, { color: colors.onSurface }]}
                  >
                    {event.packageName}
                  </Text>
                  <Text
                    numberOfLines={2}
                    style={[
                      typography.bodySmall,
                      { color: colors.onSurfaceVariant },
                    ]}
                  >
                    {event.className}
                  </Text>
                  <Text
                    style={[
                      typography.labelMedium,
                      { color: colors.onSurfaceVariant },
                    ]}
                  >
                    {formatTime(event.timestamp)}
                  </Text>
                </Row>
              ))
            )}
          </Card>

          <Card
            title="Address bar"
            trailing={
              urlBarEvents.length > 0 ? (
                <Button
                  label="Clear"
                  onPress={() => setUrlBarEvents([])}
                  variant="text"
                />
              ) : undefined
            }
          >
            {urlBarEvents.length === 0 ? (
              <EmptyState
                text={
                  isMonitoring
                    ? 'Waiting. Open a supported browser and type in its address bar.'
                    : 'Start monitoring to read the address bar of supported browsers.'
                }
              />
            ) : (
              urlBarEvents.map(({ id, event }) => (
                <Row key={id}>
                  <Text
                    numberOfLines={2}
                    style={[typography.bodyLarge, { color: colors.onSurface }]}
                  >
                    {event.rawText}
                  </Text>
                  <View style={styles.rowFooter}>
                    <Badge
                      label={event.isEditing ? 'Typing' : 'On screen'}
                      tone={event.isEditing ? 'neutral' : 'active'}
                    />
                    <Text
                      numberOfLines={1}
                      style={[
                        typography.labelMedium,
                        styles.rowMeta,
                        { color: colors.onSurfaceVariant },
                      ]}
                    >
                      {event.packageName} · {formatTime(event.timestamp)}
                    </Text>
                  </View>
                </Row>
              ))
            )}
          </Card>

          <Card title="Configuration">
            <Text
              style={[
                typography.bodyMedium,
                { color: colors.onSurfaceVariant },
              ]}
            >
              In use: {configuredService ?? 'the default service'}
            </Text>

            <Text
              accessibilityRole="header"
              style={[typography.titleMedium, { color: colors.onSurface }]}
            >
              Services in the manifest ({detectedServices.length})
            </Text>

            {detectedServices.length === 0 ? (
              <EmptyState text="No accessibility service was found in the manifest. The module falls back to MyAccessibilityService." />
            ) : (
              detectedServices.map((service) => {
                const isActive = service === configuredService

                return (
                  <Row key={service}>
                    <Text
                      numberOfLines={2}
                      style={[
                        typography.bodyMedium,
                        { color: colors.onSurface },
                      ]}
                    >
                      {service}
                    </Text>
                    {isActive ? (
                      <Badge label="In use" tone="active" />
                    ) : (
                      <Button
                        label="Use this service"
                        onPress={() => useService(service)}
                        hint={`Checks the permission against ${service}`}
                        variant="outlined"
                      />
                    )}
                  </Row>
                )
              })
            )}
          </Card>
        </View>
      </ScrollView>

      <Snackbar
        message={error}
        onDismiss={dismissError}
        bottomInset={insets.bottom}
      />
    </View>
  )
}

function Row({ children }: { children: ReactNode }) {
  const { colors } = useTheme()

  return (
    <View
      style={[styles.row, { backgroundColor: colors.surfaceContainerHigh }]}
    >
      {children}
    </View>
  )
}

function EmptyState({ text }: { text: string }) {
  const { colors } = useTheme()

  return (
    <Text style={[typography.bodyMedium, { color: colors.onSurfaceVariant }]}>
      {text}
    </Text>
  )
}

function formatTime(timestamp: number) {
  return new Date(timestamp).toLocaleTimeString()
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
  },
  // Caps the line length, so a tablet gets a readable column rather than a
  // stretched phone layout.
  content: {
    width: '100%',
    maxWidth: MAX_CONTENT_WIDTH,
    alignSelf: 'center',
    gap: spacing.lg,
  },
  badgeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  actionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: spacing.sm,
  },
  row: {
    borderRadius: shape.medium,
    padding: spacing.md,
    gap: spacing.xs,
  },
  rowFooter: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: spacing.sm,
  },
  rowMeta: {
    flexShrink: 1,
  },
})
