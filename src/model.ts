// Types and pure helpers shared by every screen. The Config shape is exactly the rules file
// that the native listener reads (see android/.../core/ConfigJson.kt).

export type Tier = 'important' | 'normal' | 'muted'
export type Lane = 'vip' | Tier | 'untouched'

export interface Vip {
  id: string
  name: string
  phones: string[]
  lookupKey?: string
  aliases: string[]
  sound: string
  addedAt: number
}

export interface Config {
  v?: number
  enabled: boolean
  pausedUntil: number
  onboarded: boolean
  /** Also re-post apps the user never listed, on the Normal channel. */
  sortUnlisted: boolean
  /** VIP channels ring through Silent and Do Not Disturb (alarm volume). */
  vipBypassDnd: boolean
  importantSound: string
  normalSound: string
  apps: Record<string, Tier>
  vips: Vip[]
}

export interface Access {
  listener: boolean
  post: boolean
  dnd: boolean
}

export interface Entry {
  time: number
  pkg: string
  label: string
  lane: Lane
  vip?: string
}

export interface NativeState {
  config: Config
  access: Access
  week: Record<Lane, number>
  recent: Entry[]
  seen: Record<string, number>
  crashReport?: string
  version: string
  sdk: number
  now: number
}

export interface AppInfo {
  pkg: string
  label: string
}

export interface PickedContact {
  name: string
  number?: string
  lookupKey?: string
}

export const TIERS: { key: Tier; label: string }[] = [
  { key: 'important', label: 'Important' },
  { key: 'normal', label: 'Normal' },
  { key: 'muted', label: 'Muted' },
]

export const LANE_LABEL: Record<Lane, string> = {
  vip: 'VIP',
  important: 'Important',
  normal: 'Normal',
  muted: 'Muted',
  untouched: 'Untouched',
}

/** Tones bundled in res/raw, as (token, label). */
export const BUILT_IN_SOUNDS: [string, string][] = [
  ['app:rise', 'Rise'],
  ['app:bell', 'Bell'],
  ['app:chime', 'Chime'],
  ['app:ping', 'Ping'],
  ['app:pop', 'Pop'],
  ['app:pulse', 'Pulse'],
]
export const DEFAULT_SOUND = 'default'
export const SILENT = 'silent'
const DEFAULT_VIP_SOUND = 'app:bell'

/** VIP matching starts with these chat apps (same list as RuleEngine.CHAT_APPS). */
export const CHAT_APPS = new Set([
  'com.whatsapp',
  'com.whatsapp.w4b',
  'org.telegram.messenger',
  'org.telegram.messenger.web',
  'org.thunderdog.challegram',
])

export function defaultConfig(onboarded = false): Config {
  return {
    v: 1,
    enabled: true,
    pausedUntil: 0,
    onboarded,
    sortUnlisted: false,
    vipBypassDnd: false,
    importantSound: 'app:rise',
    normalSound: DEFAULT_SOUND,
    apps: {},
    vips: [],
  }
}

export const isPaused = (c: Config, now: number) => c.enabled && now < c.pausedUntil

export function soundFor(c: Config, tier: Tier): string {
  if (tier === 'important') return c.importantSound
  if (tier === 'normal') return c.normalSound
  return SILENT
}

/** Label for tokens that don't need the phone's media store; undefined for custom sounds. */
export function knownSoundLabel(token: string): string | undefined {
  const builtIn = BUILT_IN_SOUNDS.find(([t]) => t === token)
  if (builtIn) return builtIn[1]
  if (token === DEFAULT_SOUND) return 'Phone default'
  if (token === SILENT) return 'Silent'
  return undefined
}

// ---- Config changes. Each returns a new Config; the hook saves it. ----

export function setTier(c: Config, pkg: string, tier: Tier | null): Config {
  const apps = { ...c.apps }
  if (tier) apps[pkg] = tier
  else delete apps[pkg]
  return { ...c, apps }
}

export const setEnabled = (c: Config, enabled: boolean): Config => ({ ...c, enabled, pausedUntil: 0 })
export const pauseFor = (c: Config, minutes: number): Config => ({ ...c, pausedUntil: Date.now() + minutes * 60_000 })
export const resume = (c: Config): Config => ({ ...c, pausedUntil: 0 })
export const resetRules = (c: Config): Config => defaultConfig(c.onboarded)

/** Adds a VIP, or adds the number to an existing VIP picked from the same contact. */
export function addVip(c: Config, picked: PickedContact): { config: Config; vip: Vip } {
  const existing = picked.lookupKey ? c.vips.find((v) => v.lookupKey === picked.lookupKey) : undefined
  if (existing) {
    const phones = picked.number && !existing.phones.includes(picked.number) ? [...existing.phones, picked.number] : existing.phones
    const vip = { ...existing, phones }
    return { config: updateVip(c, vip), vip }
  }
  const vip: Vip = {
    id: crypto.randomUUID().slice(0, 8),
    name: picked.name,
    phones: picked.number ? [picked.number] : [],
    aliases: [],
    sound: DEFAULT_VIP_SOUND,
    addedAt: Date.now(),
  }
  if (picked.lookupKey) vip.lookupKey = picked.lookupKey
  return { config: { ...c, vips: [...c.vips, vip] }, vip }
}

export const updateVip = (c: Config, vip: Vip): Config => ({ ...c, vips: c.vips.map((v) => (v.id === vip.id ? vip : v)) })
export const removeVip = (c: Config, id: string): Config => ({ ...c, vips: c.vips.filter((v) => v.id !== id) })

// ---- Formatting ----

export const plural = (n: number, word: string) => (n === 1 ? `1 ${word}` : `${n} ${word}s`)

export const clock = (time: number) => new Date(time).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })

export function relativeTime(time: number, now: number): string {
  const minutes = Math.floor((now - time) / 60_000)
  if (minutes < 1) return 'Just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  return new Date(time).toLocaleDateString([], { day: 'numeric', month: 'short' })
}
