import { useEffect, useRef } from 'react'
import { Pressable, StyleSheet, Text, View } from 'react-native'
import {
  MIN_TOUCH_TARGET,
  shape,
  spacing,
  typography,
  useTheme,
} from '../theme'

/** Material's long duration, for a message the user has to read and act on. */
const AUTO_DISMISS_MS = 10000

type SnackbarProps = {
  message: string | null
  onDismiss: () => void
  /** Distance from the bottom of the screen, so the bar clears the nav bar. */
  bottomInset: number
}

/**
 * A Material 3 snackbar.
 *
 * It exists because the demo used to write every failure to `console.error`,
 * where no user can see it. A failed call now says so on screen, and says so to
 * TalkBack through the assertive live region.
 */
export function Snackbar({ message, onDismiss, bottomInset }: SnackbarProps) {
  const { colors } = useTheme()

  // The timer is armed by the MESSAGE, and by nothing else.
  //
  // Depending on `onDismiss` restarted the countdown on every render of the parent,
  // and the parent re-renders on every accessibility event. Under a stream of them
  // the ten seconds never elapsed and the snackbar stayed on screen for good. A ref
  // keeps the latest callback reachable without making it a dependency, so a caller
  // passing an inline arrow — the normal thing to write — cannot break the timer.
  const onDismissRef = useRef(onDismiss)
  onDismissRef.current = onDismiss

  useEffect(() => {
    if (message === null) return
    const timer = setTimeout(() => onDismissRef.current(), AUTO_DISMISS_MS)
    return () => clearTimeout(timer)
  }, [message])

  if (message === null) return null

  return (
    <View
      accessibilityLiveRegion="assertive"
      accessibilityRole="alert"
      style={[
        styles.snackbar,
        {
          backgroundColor: colors.inverseSurface,
          bottom: bottomInset + spacing.lg,
        },
      ]}
    >
      <Text
        style={[
          typography.bodyMedium,
          styles.message,
          { color: colors.inverseOnSurface },
        ]}
      >
        {message}
      </Text>
      <Pressable
        accessibilityRole="button"
        android_ripple={{ color: colors.inversePrimary }}
        onPress={onDismiss}
        style={styles.action}
      >
        <Text style={[typography.labelLarge, { color: colors.inversePrimary }]}>
          Dismiss
        </Text>
      </Pressable>
    </View>
  )
}

const styles = StyleSheet.create({
  snackbar: {
    position: 'absolute',
    left: spacing.lg,
    right: spacing.lg,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    paddingLeft: spacing.lg,
    paddingRight: spacing.sm,
    borderRadius: shape.small,
    overflow: 'hidden',
  },
  message: {
    flex: 1,
    paddingVertical: spacing.lg,
  },
  action: {
    minHeight: MIN_TOUCH_TARGET,
    minWidth: MIN_TOUCH_TARGET,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.md,
  },
})
