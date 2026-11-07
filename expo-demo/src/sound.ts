import { createAudioPlayer, setAudioModeAsync, type AudioPlayer } from 'expo-audio'
import { Vibration } from 'react-native'
import { DEFAULT_SOUND, vibrationPattern } from '../../src/model'

// The same six tones the real app ships (android/app/src/main/res/raw).
const SOURCES: Record<string, number> = {
  'app:rise': require('../assets/sounds/rise.wav'),
  'app:bell': require('../assets/sounds/bell.wav'),
  'app:chime': require('../assets/sounds/chime.wav'),
  'app:ping': require('../assets/sounds/ping.wav'),
  'app:pop': require('../assets/sounds/pop.wav'),
  'app:pulse': require('../assets/sounds/pulse.wav'),
}

let player: AudioPlayer | null = null
let ready = false

/** Plays a sound token. "Phone default" can't be reached from Expo Go, so Ping stands in for it. */
export function playSound(token: string | null) {
  if (!token) return
  const source = SOURCES[token === DEFAULT_SOUND ? 'app:ping' : token]
  if (!source) return
  if (!ready) {
    ready = true
    void setAudioModeAsync({ playsInSilentMode: true, interruptionMode: 'mixWithOthers' })
  }
  if (!player) {
    player = createAudioPlayer(source)
  } else {
    player.replace(source)
  }
  void player.seekTo(0)
  player.play()
}

export function stopSound() {
  player?.pause()
}

/** Plays a vibration pattern (same patterns as the real app's notification channels). */
export function vibrate(token: string | null) {
  if (!token) return
  const pattern = vibrationPattern(token)
  if (pattern) Vibration.vibrate(pattern)
}
