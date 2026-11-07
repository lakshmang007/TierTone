// What the Expo Go demo simulates. The real decisions are made by the Kotlin RuleEngine on the
// phone; this is a simplified TypeScript copy of its main rules so the demo behaves the same way.
import { CHAT_APPS, isPaused, type Config, type Lane, type Tier, type Vip } from '../../src/model'

export interface DemoApp {
  pkg: string
  label: string
  color: string
}

/** Stand-ins for the phone's installed apps (Expo Go can't list them). */
export const DEMO_APPS: DemoApp[] = [
  { pkg: 'com.whatsapp', label: 'WhatsApp', color: '#25D366' },
  { pkg: 'org.telegram.messenger', label: 'Telegram', color: '#229ED9' },
  { pkg: 'com.Slack', label: 'Slack', color: '#4A154B' },
  { pkg: 'com.google.android.gm', label: 'Gmail', color: '#EA4335' },
  { pkg: 'com.instagram.android', label: 'Instagram', color: '#C13584' },
  { pkg: 'com.google.android.youtube', label: 'YouTube', color: '#FF0000' },
  { pkg: 'in.swiggy.android', label: 'Swiggy', color: '#FC8019' },
  { pkg: 'com.phonepe.app', label: 'PhonePe', color: '#5F259F' },
  { pkg: 'com.linkedin.android', label: 'LinkedIn', color: '#0A66C2' },
  { pkg: 'com.google.android.calendar', label: 'Calendar', color: '#4285F4' },
  { pkg: 'com.spotify.music', label: 'Spotify', color: '#1DB954' },
  { pkg: 'com.google.android.dialer', label: 'Phone', color: '#34A853' },
]

export const appByPkg = (pkg: string) => DEMO_APPS.find((a) => a.pkg === pkg)

export interface Scenario {
  id: string
  pkg: string
  /** Who wrote it (chat apps) or the notification title. */
  sender: string
  text: string
  group?: string
  /** The group is muted inside the chat app itself. */
  mutedInApp?: boolean
  /** Calls, alarms, navigation and media are never touched. */
  protectedKind?: string
}

export const SCENARIOS: Scenario[] = [
  { id: 'mom', pkg: 'com.whatsapp', sender: 'Mom', text: 'Dinner at 8? Call me when you leave.' },
  { id: 'family', pkg: 'com.whatsapp', sender: 'Ravi', group: 'Family', text: 'Who has the car keys?' },
  { id: 'muted-group', pkg: 'com.whatsapp', sender: 'Mom', group: 'College alumni', mutedInApp: true, text: 'Reunion photos are up!' },
  { id: 'slack', pkg: 'com.Slack', sender: '#deploys', text: 'Build failed on main: 3 tests failing' },
  { id: 'insta', pkg: 'com.instagram.android', sender: 'priya.k', text: 'liked your reel' },
  { id: 'gmail', pkg: 'com.google.android.gm', sender: 'Placement Cell', text: 'Interview schedule for Friday' },
  { id: 'swiggy', pkg: 'in.swiggy.android', sender: 'Order update', text: 'Your order is out for delivery' },
  { id: 'call', pkg: 'com.google.android.dialer', sender: 'Incoming call', text: 'Mom is calling', protectedKind: 'call' },
]

export interface Outcome {
  lane: Lane
  vip?: Vip
  /** Sound token that plays, or null for silence / the app's own sound. */
  sound: string | null
  /** Vibration token, or null for none / the app's own. */
  vibration: string | null
  why: string
}

/** Same idea as VipMatcher.nameKey: case-, spacing- and emoji-insensitive names. */
export function nameKey(name: string): string {
  let s = name
  try {
    s = s.normalize('NFKC')
  } catch {
    // Older JS engines: compare without normalising.
  }
  s = s
    .toLowerCase()
    .replace(/[\uD800-\uDFFF️‍⃣☀-➿]/g, '')
    .replace(/[!-/:-@[-`{-~]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return s || name.trim()
}

export function matchVip(vips: Vip[], sender: string): Vip | undefined {
  const wanted = nameKey(sender)
  return vips.find((v) => [v.name, ...v.aliases].some((n) => nameKey(n) === wanted))
}

export function decide(s: Scenario, c: Config, now: number): Outcome {
  const app = appByPkg(s.pkg)?.label ?? s.pkg
  const listed: Tier | undefined = c.apps[s.pkg]
  const quiet = (lane: Lane, why: string): Outcome => ({ lane, sound: null, vibration: null, why })
  // A sorted notification's sound and vibration. Silent mode keeps only the vibration.
  const alert = (lane: Lane, sound: string, vibration: string, why: string, vip?: Vip): Outcome => ({
    lane,
    vip,
    sound: c.silentMode ? null : sound,
    vibration,
    why: c.silentMode ? `${why} Silent mode is on, so you only feel it.` : why,
  })

  if (!c.enabled) return quiet('untouched', 'TierTone is off, so every app uses its own sound.')
  if (isPaused(c, now)) return quiet('untouched', 'TierTone is paused, so apps keep their own sounds.')
  if (s.protectedKind) return quiet('untouched', 'Calls, alarms, navigation and music are never touched.')

  const vip = CHAT_APPS.has(s.pkg) ? matchVip(c.vips, s.sender) : undefined
  const mutedGroup = Boolean(s.mutedInApp && s.group)
  if (vip && !mutedGroup) {
    const over = listed === 'muted' ? ` even though ${app} is Muted` : ''
    return alert('vip', vip.sound, vip.vibration, `${vip.name} is a VIP, so their own sound and vibration play${over}.`, vip)
  }
  if (mutedGroup) {
    // The app posted it silently; TierTone respects that, VIP or not.
    const why = vip
      ? `You muted this group inside ${app}, so even a VIP stays quiet.`
      : `You muted this group inside ${app}, so it stays quiet.`
    return quiet(!listed && !c.sortUnlisted ? 'untouched' : 'muted', why)
  }
  if (!listed) {
    if (!c.sortUnlisted) return quiet('untouched', `${app} isn't in your list, so it keeps its own sound.`)
    return alert('normal', c.normalSound, c.normalVibration, `${app} isn't listed and "Sort unlisted apps" is on: Normal.`)
  }
  if (listed === 'muted') return quiet('muted', `${app} is Muted: it shows quietly, with no sound or vibration.`)
  if (listed === 'important') {
    return alert('important', c.importantSound, c.importantVibration, `${app} is Important: its own sound, vibration and a pop-up.`)
  }
  return alert('normal', c.normalSound, c.normalVibration, `${app} is Normal: the Normal sound and vibration.`)
}

/** A starting point that shows every tier in action. */
export function exampleSetup(c: Config): Config {
  const mom: Vip = {
    id: 'demo-mom',
    name: 'Mom',
    phones: ['+91 98765 43210'],
    aliases: ['Amma'],
    sound: 'app:bell',
    vibration: 'heartbeat',
    addedAt: Date.now(),
  }
  return {
    ...c,
    apps: { 'com.whatsapp': 'muted', 'com.Slack': 'important', 'com.instagram.android': 'muted', 'com.google.android.gm': 'normal' },
    vips: c.vips.some((v) => v.id === mom.id) ? c.vips : [...c.vips, mom],
  }
}
