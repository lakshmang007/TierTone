import { useEffect, useState } from 'react'
import { Pressable, View } from 'react-native'
import { BUILT_IN_SOUNDS, DEFAULT_SOUND, VIBRATE_DEFAULT, VIBRATE_NONE, VIBRATIONS, knownSoundLabel, vibrationLabel } from '../../src/model'
import { playSound, stopSound, vibrate } from './sound'
import { useColors } from './theme'
import { Button, Dialog, T } from './ui'

export const soundLabel = (token: string) => knownSoundLabel(token) ?? 'Custom sound'

/** "Bell · Heartbeat", or just the vibration when Silent mode is on. */
export const toneSummary = (sound: string, vibration: string, silentMode: boolean) =>
  silentMode ? vibrationLabel(vibration) : `${soundLabel(sound)} · ${vibrationLabel(vibration)}`

export interface Tone {
  sound: string
  vibration: string
}

function Radio({ label, on, onPress }: { label: string; on: boolean; onPress: () => void }) {
  const c = useColors()
  return (
    <Pressable onPress={onPress} style={{ flexDirection: 'row', alignItems: 'center', gap: 14, minHeight: 38 }}>
      <View style={{ width: 20, height: 20, borderRadius: 10, borderWidth: 2, borderColor: on ? c.primary : c.muted, alignItems: 'center', justifyContent: 'center' }}>
        {on ? <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: c.primary }} /> : null}
      </View>
      <T size={16}>{label}</T>
    </Pressable>
  )
}

/** Pick a sound and a vibration pattern together. Each choice plays (or buzzes) as you tap it. */
export function TonePicker(props: {
  visible: boolean
  title: string
  current: Tone
  silentMode: boolean
  onPick: (tone: Tone) => void
  onClose: () => void
}) {
  const c = useColors()
  const [sound, setSound] = useState(props.current.sound)
  const [vibration, setVibration] = useState(props.current.vibration)
  useEffect(() => {
    if (props.visible) {
      setSound(props.current.sound)
      setVibration(props.current.vibration)
    }
  }, [props.visible, props.current.sound, props.current.vibration])

  const close = () => {
    stopSound()
    props.onClose()
  }
  const sounds = [...BUILT_IN_SOUNDS.map(([t]) => t), DEFAULT_SOUND]
  const vibrations = [...VIBRATIONS.map((v) => v.token), VIBRATE_DEFAULT, VIBRATE_NONE]

  return (
    <Dialog
      visible={props.visible}
      title={props.title}
      onClose={close}
      actions={
        <>
          <Button kind="text" label="Cancel" onPress={close} />
          <Button
            kind="text"
            label="Done"
            onPress={() => {
              props.onPick({ sound, vibration })
              close()
            }}
          />
        </>
      }
    >
      <T weight="600" color={c.primary}>
        Sound
      </T>
      {props.silentMode ? (
        <T size={12} muted>
          Silent mode is on, so this sound plays only after you turn it off.
        </T>
      ) : null}
      <View style={{ opacity: props.silentMode ? 0.55 : 1 }}>
        {sounds.map((token) => (
          <Radio
            key={token}
            label={soundLabel(token)}
            on={sound === token}
            onPress={() => {
              setSound(token)
              playSound(token)
            }}
          />
        ))}
      </View>

      <T weight="600" color={c.primary}>
        Vibration
      </T>
      <T size={12} muted>
        Tap one to feel it.
      </T>
      <View>
        {vibrations.map((token) => (
          <Radio
            key={token}
            label={vibrationLabel(token)}
            on={vibration === token}
            onPress={() => {
              setVibration(token)
              vibrate(token)
            }}
          />
        ))}
      </View>
      <T size={12} muted>
        The real app can also use any sound on your phone. In this demo, "Phone default" plays Ping.
      </T>
    </Dialog>
  )
}
