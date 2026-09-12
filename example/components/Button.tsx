import { Pressable, StyleSheet, Text, View } from 'react-native'
import {
  MIN_TOUCH_TARGET,
  shape,
  spacing,
  typography,
  useTheme,
} from '../theme'

export type ButtonVariant = 'filled' | 'tonal' | 'outlined' | 'text'

type ButtonProps = {
  label: string
  onPress: () => void
  variant?: ButtonVariant
  /** Announced by TalkBack after the label, to say what the press will do. */
  hint?: string
  disabled?: boolean
  fullWidth?: boolean
}

/**
 * A Material 3 button.
 *
 * It replaces the React Native stock `Button`, which paints a 2016 Material
 * rectangle and stands about 36 dp tall. Every target here is at least
 * [MIN_TOUCH_TARGET] dp, and the press feedback is the platform ripple.
 */
export function Button({
  label,
  onPress,
  variant = 'filled',
  hint,
  disabled = false,
  fullWidth = false,
}: ButtonProps) {
  const { colors } = useTheme()

  const container = {
    filled: { backgroundColor: colors.primary, borderWidth: 0 },
    tonal: { backgroundColor: colors.primaryContainer, borderWidth: 0 },
    outlined: {
      backgroundColor: 'transparent',
      borderWidth: 1,
      borderColor: colors.outline,
    },
    text: { backgroundColor: 'transparent', borderWidth: 0 },
  }[variant]

  const labelColor = {
    filled: colors.onPrimary,
    tonal: colors.onPrimaryContainer,
    outlined: colors.primary,
    text: colors.primary,
  }[variant]

  const rippleColor = {
    filled: colors.onPrimary,
    tonal: colors.onPrimaryContainer,
    outlined: colors.primary,
    text: colors.primary,
  }[variant]

  return (
    <View
      style={[
        styles.clip,
        container,
        fullWidth ? styles.fullWidth : styles.hugContent,
        disabled && styles.disabledContainer,
      ]}
    >
      <Pressable
        accessibilityRole="button"
        accessibilityHint={hint}
        accessibilityState={{ disabled }}
        android_ripple={{ color: rippleColor }}
        disabled={disabled}
        onPress={onPress}
        style={styles.pressable}
      >
        <Text
          style={[
            typography.labelLarge,
            styles.label,
            { color: disabled ? colors.onSurfaceVariant : labelColor },
          ]}
        >
          {label}
        </Text>
      </Pressable>
    </View>
  )
}

const styles = StyleSheet.create({
  // The ripple is clipped by the container, so the rounded corner holds.
  clip: {
    borderRadius: shape.full,
    overflow: 'hidden',
  },
  fullWidth: {
    alignSelf: 'stretch',
  },
  hugContent: {
    alignSelf: 'flex-start',
  },
  disabledContainer: {
    opacity: 0.5,
  },
  pressable: {
    minHeight: MIN_TOUCH_TARGET,
    justifyContent: 'center',
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.sm,
  },
  label: {
    textAlign: 'center',
  },
})
