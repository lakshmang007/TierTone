import { registerPlugin } from '@capacitor/core'
import type { AppInfo, Config, NativeState } from '../model'

export type SettingsScreen = 'notificationAccess' | 'appDetails' | 'appNotifications' | 'dndAccess' | 'battery'

/** Mirrors android/app/src/main/java/app/tiertone/TierTonePlugin.kt. */
export interface TierTonePlugin {
  getState(): Promise<NativeState>
  setConfig(options: { config: Config }): Promise<void>
  listApps(options: { extra: string[] }): Promise<{ apps: AppInfo[] }>
  appIcon(options: { pkg: string; size?: number }): Promise<{ dataUrl?: string }>
  openSettings(options: { screen: SettingsScreen }): Promise<void>
  requestNotifications(): Promise<{ granted: boolean }>
  pickContact(): Promise<{ cancelled?: boolean; name?: string; number?: string; lookupKey?: string }>
  pickSystemSound(options: { current: string }): Promise<{ cancelled?: boolean; token?: string; label?: string }>
  soundLabels(options: { tokens: string[] }): Promise<Record<string, string>>
  previewSound(options: { token: string }): Promise<void>
  stopPreview(): Promise<void>
  shareText(options: { text: string; title?: string }): Promise<void>
  clearCrashReport(): Promise<void>
}

/** On a phone this is the native plugin; in a desktop browser (npm run dev) it is a mock. */
export const TierTone = registerPlugin<TierTonePlugin>('TierTone', {
  web: () => import('./web').then((m) => new m.TierToneWeb()),
})
