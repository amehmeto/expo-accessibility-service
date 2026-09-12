import { useColorScheme } from 'react-native'

/**
 * Material 3 colour roles for this demo, as a light scheme and a dark scheme.
 *
 * Roles, not raw hex, because a role resolves in both schemes and a hex value
 * does not. Every text pair below was checked against WCAG AA (4.5:1 for body
 * text); the weakest pair is `onSurfaceVariant` on `surfaceContainer` at 8.0:1
 * in light and 9.6:1 in dark.
 */
export type ColorScheme = {
  background: string
  surface: string
  surfaceContainer: string
  surfaceContainerHigh: string
  onSurface: string
  onSurfaceVariant: string
  outline: string
  outlineVariant: string
  primary: string
  onPrimary: string
  primaryContainer: string
  onPrimaryContainer: string
  tertiaryContainer: string
  onTertiaryContainer: string
  error: string
  onError: string
  errorContainer: string
  onErrorContainer: string
  inverseSurface: string
  inverseOnSurface: string
  inversePrimary: string
}

const lightColors: ColorScheme = {
  background: '#FBF9F9',
  surface: '#FBF9F9',
  surfaceContainer: '#EFEDED',
  surfaceContainerHigh: '#E9E7E7',
  onSurface: '#1B1B1B',
  onSurfaceVariant: '#474747',
  outline: '#777777',
  outlineVariant: '#C9C7C7',
  primary: '#00696E',
  onPrimary: '#FFFFFF',
  primaryContainer: '#9EF0F5',
  onPrimaryContainer: '#002022',
  tertiaryContainer: '#CDE7EE',
  onTertiaryContainer: '#061F25',
  error: '#BA1A1A',
  onError: '#FFFFFF',
  errorContainer: '#FFDAD6',
  onErrorContainer: '#410002',
  inverseSurface: '#2F3131',
  inverseOnSurface: '#F0F1F1',
  inversePrimary: '#82D3D8',
}

const darkColors: ColorScheme = {
  background: '#0E1415',
  surface: '#0E1415',
  surfaceContainer: '#1B2122',
  surfaceContainerHigh: '#252B2C',
  onSurface: '#DDE4E4',
  onSurfaceVariant: '#BFC8C9',
  outline: '#899294',
  outlineVariant: '#3F4849',
  primary: '#82D3D8',
  onPrimary: '#00363A',
  primaryContainer: '#004F53',
  onPrimaryContainer: '#9EF0F5',
  tertiaryContainer: '#334B51',
  onTertiaryContainer: '#CDE7EE',
  error: '#FFB4AB',
  onError: '#690005',
  errorContainer: '#93000A',
  onErrorContainer: '#FFDAD6',
  inverseSurface: '#DDE4E4',
  inverseOnSurface: '#2B3133',
  inversePrimary: '#00696E',
}

/**
 * The Material 3 type scale roles this demo uses.
 *
 * Sizes are unitless, so React Native reads them as sp and the system font-size
 * setting scales them. Nothing here sets `allowFontScaling={false}`.
 */
export const typography = {
  headlineSmall: { fontSize: 24, lineHeight: 32, fontWeight: '400' as const },
  titleLarge: { fontSize: 22, lineHeight: 28, fontWeight: '400' as const },
  titleMedium: { fontSize: 16, lineHeight: 24, fontWeight: '600' as const },
  bodyLarge: { fontSize: 16, lineHeight: 24, fontWeight: '400' as const },
  bodyMedium: { fontSize: 14, lineHeight: 20, fontWeight: '400' as const },
  bodySmall: { fontSize: 12, lineHeight: 16, fontWeight: '400' as const },
  labelLarge: { fontSize: 14, lineHeight: 20, fontWeight: '600' as const },
  labelMedium: { fontSize: 12, lineHeight: 16, fontWeight: '600' as const },
}

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
}

export const shape = {
  small: 8,
  medium: 12,
  large: 16,
  extraLarge: 28,
  full: 999,
}

/** The minimum touch target on Android. Material 3 allows nothing smaller. */
export const MIN_TOUCH_TARGET = 48

/** The width past which a phone layout would become a stretched tablet layout. */
export const MAX_CONTENT_WIDTH = 640

export type Theme = {
  colors: ColorScheme
  isDark: boolean
}

export function useTheme(): Theme {
  const isDark = useColorScheme() === 'dark'
  return { colors: isDark ? darkColors : lightColors, isDark }
}
