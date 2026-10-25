import { WebPlugin } from '@capacitor/core'
import { defaultConfig, type AppInfo, type Config, type Entry, type Lane, type NativeState } from '../model'
import type { SettingsScreen, TierTonePlugin } from './tiertone'

// Browser stand-in for the native plugin, so the UI can be developed with `npm run dev`.
// Nothing here runs on the phone.

const APPS: AppInfo[] = [
  { pkg: 'com.whatsapp', label: 'WhatsApp' },
  { pkg: 'org.telegram.messenger', label: 'Telegram' },
  { pkg: 'com.Slack', label: 'Slack' },
  { pkg: 'com.google.android.gm', label: 'Gmail' },
  { pkg: 'com.instagram.android', label: 'Instagram' },
  { pkg: 'in.swiggy.android', label: 'Swiggy' },
  { pkg: 'com.phonepe.app', label: 'PhonePe' },
  { pkg: 'com.google.android.calendar', label: 'Calendar' },
  { pkg: 'com.linkedin.android', label: 'LinkedIn' },
  { pkg: 'com.spotify.music', label: 'Spotify' },
]
const COLORS = ['#25D366', '#229ED9', '#4A154B', '#EA4335', '#C13584', '#FC8019', '#5F259F', '#4285F4', '#0A66C2', '#1DB954']
const KEY = 'tiertone-web-mock'

export class TierToneWeb extends WebPlugin implements TierTonePlugin {
  private config: Config
  private access = { listener: false, post: false, dnd: true }

  constructor() {
    super()
    let saved: Config | null = null
    try {
      saved = JSON.parse(localStorage.getItem(KEY) ?? 'null')
    } catch {
      saved = null
    }
    this.config = saved ?? defaultConfig()
    if (this.config.onboarded) this.access = { listener: true, post: true, dnd: true }
  }

  async getState(): Promise<NativeState> {
    const now = Date.now()
    const recent: Entry[] = [
      { time: now - 30_000, pkg: 'com.whatsapp', label: 'WhatsApp', lane: 'vip', vip: this.config.vips[0]?.name ?? 'Mom' },
      { time: now - 9 * 60_000, pkg: 'com.Slack', label: 'Slack', lane: 'important' },
      { time: now - 42 * 60_000, pkg: 'com.instagram.android', label: 'Instagram', lane: 'muted' },
      { time: now - 3 * 3_600_000, pkg: 'com.google.android.gm', label: 'Gmail', lane: 'normal' },
      { time: now - 5 * 3_600_000, pkg: 'in.swiggy.android', label: 'Swiggy', lane: 'untouched' },
    ]
    const week: Record<Lane, number> = { vip: 6, important: 23, normal: 41, muted: 88, untouched: 17 }
    return {
      config: structuredClone(this.config),
      access: { ...this.access },
      week,
      recent,
      seen: { 'com.whatsapp': now, 'com.Slack': now - 1, 'com.instagram.android': now - 2 },
      version: 'web preview',
      sdk: 35,
      now,
    }
  }

  async setConfig({ config }: { config: Config }) {
    this.config = structuredClone(config)
    localStorage.setItem(KEY, JSON.stringify(config))
  }

  async listApps() {
    return { apps: APPS }
  }

  async appIcon({ pkg }: { pkg: string }) {
    const i = Math.max(0, APPS.findIndex((a) => a.pkg === pkg))
    const letter = (APPS[i]?.label ?? pkg)[0]
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="12" fill="${COLORS[i % COLORS.length]}"/><text x="24" y="31" font-size="22" font-family="sans-serif" fill="#fff" text-anchor="middle">${letter}</text></svg>`
    return { dataUrl: `data:image/svg+xml,${encodeURIComponent(svg)}` }
  }

  async openSettings({ screen }: { screen: SettingsScreen }) {
    // Pretend the user granted whatever the settings screen is for.
    if (screen === 'notificationAccess') this.access.listener = true
    if (screen === 'appNotifications') this.access.post = true
  }

  async requestNotifications() {
    this.access.post = true
    return { granted: true }
  }

  async pickContact() {
    const names = ['Priya Sharma', 'Dad', 'Ananya', 'Rahul K']
    const name = names[this.config.vips.length % names.length]
    return { name, number: '+91 98765 4321' + this.config.vips.length, lookupKey: `mock-${name}` }
  }

  async pickSystemSound() {
    return { token: 'content://media/internal/audio/media/42', label: 'Tweeter' }
  }

  async soundLabels({ tokens }: { tokens: string[] }) {
    return Object.fromEntries(tokens.map((t) => [t, 'Tweeter']))
  }

  async previewSound() {}
  async stopPreview() {}

  async shareText({ text }: { text: string }) {
    console.info('share', text)
  }

  async clearCrashReport() {}
}
