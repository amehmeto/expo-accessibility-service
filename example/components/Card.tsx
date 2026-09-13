import { ReactNode } from 'react'
import { StyleSheet, Text, View } from 'react-native'
import { shape, spacing, typography, useTheme } from '../theme'

type CardProps = {
  title: string
  /** Sits next to the title, for a count or a single action. */
  trailing?: ReactNode
  children: ReactNode
}

/**
 * A Material 3 surface-container card.
 *
 * Elevation is tonal — a step up the surface roles — rather than a drop shadow,
 * so the card holds its weight in both the light and the dark scheme.
 */
export function Card({ title, trailing, children }: CardProps) {
  const { colors } = useTheme()

  return (
    <View
      style={[styles.card, { backgroundColor: colors.surfaceContainer }]}
      accessible={false}
    >
      <View style={styles.header}>
        <Text
          accessibilityRole="header"
          style={[
            typography.titleMedium,
            styles.title,
            { color: colors.onSurface },
          ]}
        >
          {title}
        </Text>
        {trailing}
      </View>
      {children}
    </View>
  )
}

const styles = StyleSheet.create({
  card: {
    borderRadius: shape.large,
    padding: spacing.lg,
    gap: spacing.md,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.sm,
  },
  title: {
    flexShrink: 1,
  },
})
