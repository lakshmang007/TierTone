import { useState } from 'react'
import { Icon } from '../components/icons'
import { TonePicker, useToneSummary } from '../components/SoundPicker'
import { Dialog, Row, ScreenTitle, SectionLabel, SwitchRow } from '../components/ui'
import { clock, isPaused, resetRules, resume, setEnabled, soundFor, vibrationFor, vibrationLabel, type Tier } from '../model'
import { TierTone } from '../native/tiertone'
import type { AppHandle } from '../useTierTone'
import { PauseChips } from './Home'

const PRIVACY = [
  'No internet permission, so nothing can leave your phone.',
  "Reads the app, the sender's name and the notification only to sort and re-post it.",
  'Never stores message text. The activity list keeps only the app, tier and time.',
  'No contacts permission: you pick each VIP yourself.',
]

export function Settings({ app }: { app: AppHandle }) {
  const { config: c, access, now, crashReport, version } = app.state
  const [picking, setPicking] = useState<Tier | null>(null)
  const [confirmReset, setConfirmReset] = useState(false)
  const importantTone = useToneSummary(c.importantSound, c.importantVibration, c.silentMode)
  const normalTone = useToneSummary(c.normalSound, c.normalVibration, c.silentMode)

  return (
    <main className="screen">
      <ScreenTitle title="Settings" />

      <SectionLabel>Sorting</SectionLabel>
      <SwitchRow
        title="TierTone is on"
        subtitle="Off: every app goes back to its own sound, and TierTone stops running in the background."
        checked={c.enabled}
        onChange={(on) => app.update((x) => setEnabled(x, on))}
      />
      {c.enabled &&
        (isPaused(c, now) ? (
          <Row
            title={`Paused until ${clock(c.pausedUntil)}`}
            subtitle="Apps keep their own sounds until then."
            trailing={
              <button className="btn tonal" onClick={() => app.update(resume)}>
                Resume
              </button>
            }
          />
        ) : (
          <PauseChips app={app} />
        ))}
      <SwitchRow
        title="Sort apps you haven't listed"
        subtitle="On: they use the Normal sound. Off: they keep their own sound."
        checked={c.sortUnlisted}
        onChange={(on) => app.update((x) => ({ ...x, sortUnlisted: on }))}
      />

      <SectionLabel>Sounds & vibration</SectionLabel>
      <SilentModeRow app={app} />
      <Row title="Important" subtitle={importantTone} onClick={() => setPicking('important')} />
      <Row title="Normal" subtitle={normalTone} onClick={() => setPicking('normal')} />
      <Row title="Muted" subtitle="No sound, no vibration" />
      <p className="small muted">
        VIPs each have their own sound and vibration: set them on the VIPs tab. Silent mode works with your phone on ring or
        Vibrate. If the phone itself is fully muted, Android stops all vibrations, except VIPs set to ring through Silent.
      </p>
      <Row
        title="Vibration, pop-ups and lock screen"
        subtitle="Fine-tune each tier in Android's notification settings"
        onClick={() => void TierTone.openSettings({ screen: 'appNotifications' })}
      />

      <SectionLabel>Keep sorting reliable</SectionLabel>
      <Row
        title="Notification access"
        subtitle={access.listener ? 'On' : "Off: TierTone can't see notifications"}
        trailing={<Status ok={access.listener} />}
        onClick={() => void TierTone.openSettings({ screen: 'notificationAccess' })}
      />
      <Row
        title="Show notifications"
        subtitle={access.post ? 'Allowed' : 'Blocked: everything is left untouched'}
        trailing={<Status ok={access.post} />}
        onClick={() => void TierTone.openSettings({ screen: 'appNotifications' })}
      />
      <Row
        title="Battery optimization"
        subtitle="Samsung, Xiaomi, OnePlus and others may stop background apps. If sorting stops, exclude TierTone here."
        onClick={() => void TierTone.openSettings({ screen: 'battery' })}
      />
      <p className="small muted">
        A short blip of an app's own sound before TierTone's is expected: the app's sound starts before TierTone can replace
        it. On Android 15 and newer, turning off “Notification cooldown” keeps every sound at full volume.
      </p>

      <SectionLabel>Privacy</SectionLabel>
      {PRIVACY.map((line) => (
        <div key={line} className="bullet yes">
          <Icon name="check" size={20} />
          <span>{line}</span>
        </div>
      ))}

      <SectionLabel>Reset</SectionLabel>
      <button className="btn outlined danger" onClick={() => setConfirmReset(true)}>
        Reset all rules
      </button>

      <SectionLabel>About</SectionLabel>
      {crashReport && (
        <>
          <Row title="Last crash report" subtitle="Saved on this phone. Share it with the developer if you like; it contains no messages." />
          <div className="button-row">
            <button className="btn tonal" onClick={() => void TierTone.shareText({ text: crashReport, title: 'Share crash report' })}>
              Share
            </button>
            <button
              className="btn text"
              onClick={() => void TierTone.clearCrashReport().then(app.refresh)}
            >
              Delete
            </button>
          </div>
        </>
      )}
      <p className="small muted">TierTone {version}</p>

      {picking && (
        <TonePicker
          title={picking === 'important' ? 'Important' : 'Normal'}
          current={{ sound: soundFor(c, picking), vibration: vibrationFor(c, picking) }}
          silentMode={c.silentMode}
          onClose={() => setPicking(null)}
          onPick={({ sound, vibration }) =>
            app.update((x) =>
              picking === 'important'
                ? { ...x, importantSound: sound, importantVibration: vibration }
                : { ...x, normalSound: sound, normalVibration: vibration },
            )
          }
        />
      )}
      {confirmReset && (
        <Dialog
          title="Reset all rules?"
          onClose={() => setConfirmReset(false)}
          actions={
            <>
              <button className="btn text" onClick={() => setConfirmReset(false)}>
                Cancel
              </button>
              <button
                className="btn text danger"
                onClick={() => {
                  setConfirmReset(false)
                  app.update(resetRules)
                }}
              >
                Reset
              </button>
            </>
          }
        >
          <p>This removes every app tier, every VIP and your sound choices. Apps go back to their own sounds.</p>
        </Dialog>
      )}
    </main>
  )
}

function Status({ ok }: { ok: boolean }) {
  return ok ? (
    <Icon name="check" className="status-ok" />
  ) : (
    <Icon name="warning" className="status-bad" />
  )
}

/** Silent mode: no sounds; each priority keeps its own vibration so you can tell them apart by feel. */
export function SilentModeRow({ app }: { app: AppHandle }) {
  const c = app.state.config
  const vipFeel = c.vips.length === 1 ? vibrationLabel(c.vips[0].vibration) : 'their own pattern'
  return (
    <SwitchRow
      title="Silent mode"
      subtitle={`No sounds. You feel who it is instead: VIPs ${vipFeel}, Important ${vibrationLabel(
        c.importantVibration,
      )}, Normal ${vibrationLabel(c.normalVibration)}. Muted stays still.`}
      checked={c.silentMode}
      onChange={(on) => app.update((x) => ({ ...x, silentMode: on }))}
    />
  )
}
