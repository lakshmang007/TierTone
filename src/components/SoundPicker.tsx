import { useEffect, useState } from 'react'
import { BUILT_IN_SOUNDS, DEFAULT_SOUND, knownSoundLabel } from '../model'
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

function SoundOption({ token, selected, onSelect }: { token: string; selected: boolean; onSelect: () => void }) {
  const label = useSoundLabel(token)
  return (
    <label className="radio-row">
      <input type="radio" name="sound" checked={selected} onChange={onSelect} />
      <span>{label}</span>
    </label>
  )
}

export function SoundPicker(props: { title: string; current: string; onPick: (token: string) => void; onClose: () => void }) {
  const [selected, setSelected] = useState(props.current)

  useEffect(() => () => void TierTone.stopPreview(), [])

  const choose = (token: string) => {
    setSelected(token)
    void TierTone.previewSound({ token })
  }
  const options = [...BUILT_IN_SOUNDS.map(([t]) => t), DEFAULT_SOUND]
  if (!options.includes(selected)) options.push(selected)

  const pickFromPhone = async () => {
    const r = await TierTone.pickSystemSound({ current: selected })
    if (r.token) {
      if (r.label) customLabels.set(r.token, r.label)
      choose(r.token)
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
              props.onPick(selected)
              props.onClose()
            }}
          >
            Done
          </button>
        </>
      }
    >
      {options.map((token) => (
        <SoundOption key={token} token={token} selected={selected === token} onSelect={() => choose(token)} />
      ))}
      <button className="btn text" onClick={() => void pickFromPhone()}>
        Choose from phone sounds…
      </button>
    </Dialog>
  )
}
