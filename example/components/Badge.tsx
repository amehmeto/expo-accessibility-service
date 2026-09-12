import { StyleSheet, Text, View } from 'react-native'
import { shape, spacing, typography, useTheme } from '../theme'

export type BadgeTone = 'active' | 'neutral' | 'alert'

type BadgeProps = {
  label: string
  tone?: BadgeTone
}

/**
 * A small state badge.
 *
 * The label carries the whole meaning. The dot only repeats it, so the dot is
 * hidden from TalkBack and colour is never the only signal — which is why the
 * 🟢 / 🔴 emoji this replaces had to go.
 */
export function Badge({ label, tone = 'neutral' }: BadgeProps) {
  const { colors } = useTheme()

  const palette = {
    active: {
      background: colors.tertiaryContainer,
      foreground: colors.onTertiaryContainer,
    },
    neutral: {
      background: colors.surfaceContainerHigh,
      foreground: colors.onSurfaceVariant,
    },
    alert: {
      background: colors.errorContainer,
      foreground: colors.onErrorContainer,
    },
  }[tone]

  return (
    <View style={[styles.badge, { backgroundColor: palette.background }]}>
      <View
        accessibilityElementsHidden
        importantForAccessibility="no"
        style={[styles.dot, { backgroundColor: palette.foreground }]}
      />
      <Text style={[typography.labelMedium, { color: palette.foreground }]}>
        {label}
      </Text>
    </View>
  )
}

const styles = StyleSheet.create({
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: shape.full,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: shape.full,
  },
})
