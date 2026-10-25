import { useState } from 'react'
import { Icon } from '../components/icons'
import { SoundPicker, useSoundLabel } from '../components/SoundPicker'
import { Card, Dialog, LaneBadge, LaneIcon, Row, ScreenTitle, SwitchRow } from '../components/ui'
import { addVip, removeVip, updateVip, type Vip } from '../model'
import { TierTone } from '../native/tiertone'
import type { AppHandle } from '../useTierTone'

export function Vips({ app }: { app: AppHandle }) {
  const { config: c, access } = app.state
  const [editing, setEditing] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const pick = async () => {
    setError(null)
    try {
      const r = await TierTone.pickContact()
      if (r.cancelled || !r.name) return
      let id = ''
      app.update((cfg) => {
        const added = addVip(cfg, { name: r.name!, number: r.number, lookupKey: r.lookupKey })
        id = added.vip.id
        return added.config
      })
      setEditing(id)
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't open contacts")
    }
  }

  const vip = c.vips.find((v) => v.id === editing)

  return (
    <main className="screen with-fab">
      <ScreenTitle title="VIP contacts" subtitle="Their chats get their own sound, even if the app is Muted." />
      <Card>
        <SwitchRow
          title="Ring through Silent & Do Not Disturb"
          subtitle="VIP messages still make a sound when your phone is silenced. They play at alarm volume."
          checked={c.vipBypassDnd}
          onChange={(on) => app.update((x) => ({ ...x, vipBypassDnd: on }))}
        />
        {c.vipBypassDnd && !access.dnd && (
          <>
            <p className="small error-text">Needs Do Not Disturb access, which Android usually grants with notification access.</p>
            <button className="btn text" onClick={() => void TierTone.openSettings({ screen: 'dndAccess' })}>
              Allow Do Not Disturb access
            </button>
          </>
        )}
      </Card>
      <p className="small muted">
        Works with WhatsApp and Telegram for now. TierTone matches the name the chat app shows, or the phone number when the
        app shares it.
      </p>
      {error && <p className="small error-text">{error}</p>}

      {c.vips.length === 0 && (
        <Card tone="lowest">
          <LaneIcon lane="vip" size={48} />
          <strong>No VIPs yet</strong>
          <p className="muted">
            Add the people you never want to miss. You choose each contact; TierTone never reads your whole contact list.
          </p>
        </Card>
      )}
      <ul className="vip-list">
        {c.vips.map((v) => (
          <VipRow key={v.id} vip={v} onClick={() => setEditing(v.id)} />
        ))}
      </ul>

      <button className="fab" onClick={() => void pick()}>
        <Icon name="add" />
        Add VIP
      </button>

      {vip && <VipEditor vip={vip} app={app} onClose={() => setEditing(null)} />}
    </main>
  )
}

function VipRow({ vip, onClick }: { vip: Vip; onClick: () => void }) {
  const sound = useSoundLabel(vip.sound)
  const details = [vip.phones[0], vip.aliases.length ? `also ${vip.aliases.join(', ')}` : ''].filter(Boolean).join(' · ')
  return (
    <li className="vip-row" onClick={onClick}>
      <span className="avatar">{vip.name.trim()[0]?.toUpperCase() ?? '?'}</span>
      <div className="row-text">
        <div className="row-title">{vip.name}</div>
        {details && <div className="row-sub">{details}</div>}
      </div>
      <LaneBadge lane="vip" text={sound} />
    </li>
  )
}

function VipEditor({ vip, app, onClose }: { vip: Vip; app: AppHandle; onClose: () => void }) {
  const [aliases, setAliases] = useState(vip.aliases.join(', '))
  const [picking, setPicking] = useState(false)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const sound = useSoundLabel(vip.sound)

  const close = () => {
    const list = [...new Set(aliases.split(',').map((a) => a.trim()).filter(Boolean))]
    if (list.join('\n') !== vip.aliases.join('\n')) app.update((c) => updateVip(c, { ...vip, aliases: list }))
    onClose()
  }

  return (
    <>
      <Dialog
        title={vip.name}
        onClose={close}
        actions={
          <button className="btn text" onClick={close}>
            Done
          </button>
        }
      >
        {vip.phones.length > 0 && <p className="muted">{vip.phones.join('\n')}</p>}
        <Row
          title="Sound"
          subtitle={sound}
          onClick={() => setPicking(true)}
          trailing={
            <button
              className="icon-btn"
              aria-label="Play sound"
              onClick={(e) => {
                e.stopPropagation()
                void TierTone.previewSound({ token: vip.sound })
              }}
            >
              <Icon name="play" />
            </button>
          }
        />
        <label className="field">
          <span>Also match these names</span>
          <input value={aliases} onChange={(e) => setAliases(e.target.value)} placeholder="e.g. Mum, Amma" />
          <small className="muted">If a chat app shows them differently, e.g. their Telegram name. Separate with commas.</small>
        </label>
        <button className="btn text danger" onClick={() => setConfirmRemove(true)}>
          Remove VIP
        </button>
      </Dialog>

      {picking && (
        <SoundPicker
          title={`Sound for ${vip.name}`}
          current={vip.sound}
          onClose={() => setPicking(false)}
          onPick={(token) => app.update((c) => updateVip(c, { ...vip, sound: token }))}
        />
      )}
      {confirmRemove && (
        <Dialog
          title={`Remove ${vip.name}?`}
          onClose={() => setConfirmRemove(false)}
          actions={
            <>
              <button className="btn text" onClick={() => setConfirmRemove(false)}>
                Cancel
              </button>
              <button
                className="btn text danger"
                onClick={() => {
                  app.update((c) => removeVip(c, vip.id))
                  onClose()
                }}
              >
                Remove
              </button>
            </>
          }
        >
          <p>Their chats will follow the app's tier again.</p>
        </Dialog>
      )}
    </>
  )
}
