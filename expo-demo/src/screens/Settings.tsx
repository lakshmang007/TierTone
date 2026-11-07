import { useState } from 'react'
import { Alert, ScrollView, View } from 'react-native'
import { clock, isPaused, resetRules, resume, setEnabled, soundFor, vibrationFor, vibrationLabel, type Tier } from '../../../src/model'
import { TonePicker, toneSummary } from '../SoundPicker'
import { useColors } from '../theme'
import { Button, Dialog, Icon, Row, ScreenTitle, SectionLabel, SwitchRow, T } from '../ui'
import type { DemoHandle } from '../useDemo'
import { PauseChips } from './Home'

const PRIVACY = [
  'No internet permission, so nothing can leave your phone.',
  "Reads the app, the sender's name and the notification only to sort and re-post it.",
  'Never stores message text. The activity list keeps only the app, tier and time.',
  'No contacts permission: you pick each VIP yourself.',
]

const simulated = (what: string) => () =>
  Alert.alert(what, "In the real TierTone app this opens Android's settings screen. It isn't needed in the demo.")

export function Settings({ demo }: { demo: DemoHandle }) {
  const c = useColors()
  const cfg = demo.state.config
  const [picking, setPicking] = useState<Tier | null>(null)
  const [confirmReset, setConfirmReset] = useState(false)

  return (
    <ScrollView contentContainerStyle={{ padding: 16 }}>
      <ScreenTitle title="Settings" />

      <SectionLabel>Sorting</SectionLabel>
      <SwitchRow
        title="TierTone is on"
        subtitle="Off: every app goes back to its own sound, and TierTone stops running in the background."
        value={cfg.enabled}
        onChange={(on) => demo.update((x) => setEnabled(x, on))}
      />
      {cfg.enabled ? (
        isPaused(cfg, demo.now) ? (
          <Row
            title={`Paused until ${clock(cfg.pausedUntil)}`}
            subtitle="Apps keep their own sounds until then."
            trailing={<Button kind="tonal" label="Resume" onPress={() => demo.update(resume)} />}
          />
        ) : (
          <PauseChips demo={demo} />
        )
      ) : null}
      <SwitchRow
        title="Sort apps you haven't listed"
        subtitle="On: they use the Normal sound. Off: they keep their own sound."
        value={cfg.sortUnlisted}
        onChange={(on) => demo.update((x) => ({ ...x, sortUnlisted: on }))}
      />

      <SectionLabel>Sounds & vibration</SectionLabel>
      <SilentModeRow demo={demo} />
      <Row
        title="Important"
        subtitle={toneSummary(cfg.importantSound, cfg.importantVibration, cfg.silentMode)}
        onPress={() => setPicking('important')}
      />
      <Row title="Normal" subtitle={toneSummary(cfg.normalSound, cfg.normalVibration, cfg.silentMode)} onPress={() => setPicking('normal')} />
      <Row title="Muted" subtitle="No sound, no vibration" />
      <T size={12} muted>
        VIPs each have their own sound and vibration: set them on the VIPs tab. Silent mode works with your phone on ring or
        Vibrate. If the phone itself is fully muted, Android stops all vibrations, except VIPs set to ring through Silent.
      </T>
      <Row title="Vibration, pop-ups and lock screen" subtitle="Fine-tune each tier in Android's notification settings" onPress={simulated('Notification settings')} />

      <SectionLabel>Keep sorting reliable</SectionLabel>
      <Row
        title="Notification access"
        subtitle={demo.state.access.listener ? 'On' : "Off: TierTone can't see notifications"}
        trailing={<Icon name={demo.state.access.listener ? 'check' : 'warning'} color={demo.state.access.listener ? c.primary : c.error} />}
        onPress={simulated('Notification access')}
      />
      <Row
        title="Battery optimization"
        subtitle="Samsung, Xiaomi, OnePlus and others may stop background apps. If sorting stops, exclude TierTone here."
        onPress={simulated('Battery optimization')}
      />

      <SectionLabel>Privacy</SectionLabel>
      {PRIVACY.map((line) => (
        <View key={line} style={{ flexDirection: 'row', gap: 10, paddingVertical: 4 }}>
          <Icon name="check" size={20} color={c.primary} />
          <T style={{ flex: 1 }}>{line}</T>
        </View>
      ))}

      <SectionLabel>Reset</SectionLabel>
      <Button kind="outlined" danger label="Reset all rules" onPress={() => setConfirmReset(true)} style={{ marginTop: 8 }} />

      <SectionLabel>About this demo</SectionLabel>
      <T muted style={{ marginTop: 4 }}>
        This is TierTone running in Expo Go. Expo Go can't read other apps' notifications, so the Try it panel on Home simulates
        them; the rules, tiers and sounds match the real app. For real sorting, install the APK.
      </T>
      <Button
        kind="text"
        label="Start the demo over"
        onPress={() =>
          Alert.alert('Start over?', 'Clears your demo rules and activity and shows the welcome screens again.', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Start over', style: 'destructive', onPress: demo.resetDemo },
          ])
        }
      />

      <TonePicker
        visible={picking !== null}
        title={picking === 'important' ? 'Important' : 'Normal'}
        current={picking ? { sound: soundFor(cfg, picking), vibration: vibrationFor(cfg, picking) } : { sound: '', vibration: '' }}
        silentMode={cfg.silentMode}
        onClose={() => setPicking(null)}
        onPick={({ sound, vibration }) =>
          demo.update((x) =>
            picking === 'important'
              ? { ...x, importantSound: sound, importantVibration: vibration }
              : { ...x, normalSound: sound, normalVibration: vibration },
          )
        }
      />
      <Dialog
        visible={confirmReset}
        title="Reset all rules?"
        onClose={() => setConfirmReset(false)}
        actions={
          <>
            <Button kind="text" label="Cancel" onPress={() => setConfirmReset(false)} />
            <Button
              kind="text"
              danger
              label="Reset"
              onPress={() => {
                setConfirmReset(false)
                demo.update(resetRules)
              }}
            />
          </>
        }
      >
        <T>This removes every app tier, every VIP and your sound choices. Apps go back to their own sounds.</T>
      </Dialog>
    </ScrollView>
  )
}

/** Silent mode: no sounds; each priority keeps its own vibration so you can tell them apart by feel. */
export function SilentModeRow({ demo }: { demo: DemoHandle }) {
  const c = demo.state.config
  const vipFeel = c.vips.length === 1 ? vibrationLabel(c.vips[0].vibration) : 'their own pattern'
  return (
    <SwitchRow
      title="Silent mode"
      subtitle={`No sounds. You feel who it is instead: VIPs ${vipFeel}, Important ${vibrationLabel(
        c.importantVibration,
      )}, Normal ${vibrationLabel(c.normalVibration)}. Muted stays still.`}
      value={c.silentMode}
      onChange={(on) => demo.update((x) => ({ ...x, silentMode: on }))}
    />
  )
}
