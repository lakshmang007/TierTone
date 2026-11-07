import { useEffect, useState } from 'react'
import { BUILT_IN_SOUNDS, DEFAULT_SOUND, VIBRATE_DEFAULT, VIBRATE_NONE, VIBRATIONS, knownSoundLabel, vibrationLabel } from '../model'
import { TierTone } from '../native/tiertone'
import { Dialog } from './ui'

const customLabels = new Map<string, string>()

/** Human name for a sound token. Custom phone sounds are looked up natively, once. */
export function useSoundLabel(token: string): string {
  const known = knownSoundLabel(token)
  const [custom, setCustom] = useState(() => customLabels.get(token))
  useEffect(() => {
    if (known || customLabels.has(token)) return
    let alive = true
    TierTone.soundLabels({ tokens: [token] })
      .then((labels) => {
        const label = labels[token] ?? 'Custom sound'
        customLabels.set(token, label)
        if (alive) setCustom(label)
      })
      .catch(() => undefined)
    return () => {
      alive = false
    }
  }, [token, known])
  return known ?? custom ?? customLabels.get(token) ?? 'Custom sound'
}

/** "Bell · Heartbeat", or just the vibration when Silent mode is on. */
export function useToneSummary(sound: string, vibration: string, silentMode: boolean): string {
  const soundName = useSoundLabel(sound)
  return silentMode ? vibrationLabel(vibration) : `${soundName} · ${vibrationLabel(vibration)}`
}

function SoundOption({ token, selected, onSelect }: { token: string; selected: boolean; onSelect: () => void }) {
  const label = useSoundLabel(token)
  return (
    <label className="radio-row">
      <input type="radio" name="sound" checked={selected} onChange={onSelect} />
      <span>{label}</span>
    </label>
  )
}

export interface Tone {
  sound: string
  vibration: string
}

/** Pick a sound and a vibration pattern together. Each choice previews as you tap it. */
export function TonePicker(props: {
  title: string
  current: Tone
  silentMode: boolean
  onPick: (tone: Tone) => void
  onClose: () => void
}) {
  const [sound, setSound] = useState(props.current.sound)
  const [vibration, setVibration] = useState(props.current.vibration)

  useEffect(() => () => void TierTone.stopPreview(), [])

  const chooseSound = (token: string) => {
    setSound(token)
    void TierTone.previewSound({ token })
  }
  const chooseVibration = (token: string) => {
    setVibration(token)
    void TierTone.previewVibration({ token })
  }
  const soundOptions = [...BUILT_IN_SOUNDS.map(([t]) => t), DEFAULT_SOUND]
  if (!soundOptions.includes(sound)) soundOptions.push(sound)
  const vibrationOptions = [...VIBRATIONS.map((v) => v.token), VIBRATE_DEFAULT, VIBRATE_NONE]

  const pickFromPhone = async () => {
    const r = await TierTone.pickSystemSound({ current: sound })
    if (r.token) {
      if (r.label) customLabels.set(r.token, r.label)
      chooseSound(r.token)
    }
  }

  return (
    <Dialog
      title={props.title}
      onClose={props.onClose}
      actions={
        <>
          <button className="btn text" onClick={props.onClose}>
            Cancel
          </button>
          <button
            className="btn text"
            onClick={() => {
              props.onPick({ sound, vibration })
              props.onClose()
            }}
          >
            Done
          </button>
        </>
      }
    >
      <h3 className="picker-heading">Sound</h3>
      {props.silentMode && <p className="small muted">Silent mode is on, so this sound plays only after you turn it off.</p>}
      <div className={props.silentMode ? 'dim' : undefined}>
        {soundOptions.map((token) => (
          <SoundOption key={token} token={token} selected={sound === token} onSelect={() => chooseSound(token)} />
        ))}
        <button className="btn text" onClick={() => void pickFromPhone()}>
          Choose from phone sounds…
        </button>
      </div>

      <h3 className="picker-heading">Vibration</h3>
      <p className="small muted">Tap one to feel it.</p>
      {vibrationOptions.map((token) => (
        <label key={token} className="radio-row">
          <input type="radio" name="vibration" checked={vibration === token} onChange={() => chooseVibration(token)} />
          <span>{vibrationLabel(token)}</span>
        </label>
      ))}
    </Dialog>
  )
}
