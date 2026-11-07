import { useColorScheme } from 'react-native'
import type { Lane } from '../../src/model'

const light = {
  bg: '#FBFBFE',
  surface: '#F2F2F9',
  surfaceStrong: '#E9E9F3',
  surfaceLowest: '#FFFFFF',
  text: '#1B1B21',
  muted: '#5D5E6C',
  outline: '#C9CAD6',
  outlineSoft: '#E3E3EC',
  primary: '#4F46E5',
  onPrimary: '#FFFFFF',
  primaryContainer: '#E0E7FF',
  onPrimaryContainer: '#1E1B4B',
  error: '#B3261E',
  errorContainer: '#FCE8E6',
  onErrorContainer: '#5F1410',
  good: '#16A34A',
  scrim: 'rgba(0,0,0,0.4)',
  lane: {
    vip: '#D97706',
    important: '#E11D48',
    normal: '#2563EB',
    muted: '#64748B',
    untouched: '#9CA3AF',
  } as Record<Lane, string>,
}

const dark: typeof light = {
  bg: '#121318',
  surface: '#1B1C22',
  surfaceStrong: '#24252D',
  surfaceLowest: '#0E0F13',
  text: '#E4E4EC',
  muted: '#A6A7B6',
  outline: '#44454F',
  outlineSoft: '#2C2D36',
  primary: '#A5B4FC',
  onPrimary: '#1E1B4B',
  primaryContainer: '#3730A3',
  onPrimaryContainer: '#E0E7FF',
  error: '#F2B8B5',
  errorContainer: '#601410',
  onErrorContainer: '#F9DEDC',
  good: '#4ADE80',
  scrim: 'rgba(0,0,0,0.55)',
  lane: {
    vip: '#FBBF24',
    important: '#FB7185',
    normal: '#60A5FA',
    muted: '#94A3B8',
    untouched: '#6B7280',
  },
}

export type Colors = typeof light

export function useColors(): Colors {
  return useColorScheme() === 'dark' ? dark : light
}

/** Hex colour with alpha, for tinted backgrounds (#RRGGBB + 0..1). */
export function tint(hex: string, alpha: number): string {
  const a = Math.round(alpha * 255)
    .toString(16)
    .padStart(2, '0')
  return hex + a
}
