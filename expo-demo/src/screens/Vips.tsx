import { useState } from 'react'
import { Pressable, ScrollView, TextInput, View } from 'react-native'
import { addVip, removeVip, updateVip, type Vip } from '../../../src/model'
import { playSound, vibrate } from '../sound'
import { TonePicker, toneSummary } from '../SoundPicker'
import { tint, useColors } from '../theme'
import { Button, Card, Dialog, Icon, LaneBadge, LaneIcon, Row, ScreenTitle, SwitchRow, T } from '../ui'
import type { DemoHandle } from '../useDemo'

export function Vips({ demo }: { demo: DemoHandle }) {
  const c = useColors()
  const cfg = demo.state.config
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<string | null>(null)
  const vip = cfg.vips.find((v) => v.id === editing)

  return (
    <View style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={{ padding: 16, gap: 8, paddingBottom: 96 }}>
        <ScreenTitle title="VIP contacts" subtitle="Their chats get their own sound, even if the app is Muted." />
        <Card>
          <SwitchRow
            title="Ring through Silent & Do Not Disturb"
            subtitle="VIP messages still make a sound when your phone is silenced. They play at alarm volume."
            value={cfg.vipBypassDnd}
            onChange={(on) => demo.update((x) => ({ ...x, vipBypassDnd: on }))}
          />
        </Card>
        <T size={12} muted style={{ marginVertical: 4 }}>
          Works with WhatsApp and Telegram for now. TierTone matches the name the chat app shows, or the phone number when the app
          shares it.
        </T>
        {cfg.vips.length === 0 ? (
          <Card tone="lowest">
            <LaneIcon lane="vip" size={48} />
            <T size={16} weight="500">
              No VIPs yet
            </T>
            <T muted>Add the people you never want to miss. Try adding "Mom", then send the WhatsApp test from Home.</T>
          </Card>
        ) : null}
        {cfg.vips.map((v) => (
          <Pressable
            key={v.id}
            onPress={() => setEditing(v.id)}
            style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, borderRadius: 16, backgroundColor: pressed ? c.surfaceStrong : c.surface })}
          >
            <View style={{ width: 44, height: 44, borderRadius: 22, backgroundColor: tint(c.lane.vip, 0.18), alignItems: 'center', justifyContent: 'center' }}>
              <T size={16} weight="600" color={c.lane.vip}>
                {v.name.trim()[0]?.toUpperCase() ?? '?'}
              </T>
            </View>
            <View style={{ flex: 1 }}>
              <T size={16} weight="500">
                {v.name}
              </T>
              <T size={12} muted>
                {[v.phones[0], v.aliases.length ? `also ${v.aliases.join(', ')}` : ''].filter(Boolean).join(' · ')}
              </T>
            </View>
            <LaneBadge lane="vip" text={toneSummary(v.sound, v.vibration, cfg.silentMode)} style={{ alignSelf: 'center' }} />
          </Pressable>
        ))}
      </ScrollView>

      <Pressable
        onPress={() => setAdding(true)}
        style={{
          position: 'absolute',
          right: 16,
          bottom: 16,
          flexDirection: 'row',
          alignItems: 'center',
          gap: 10,
          height: 56,
          paddingLeft: 16,
          paddingRight: 20,
          borderRadius: 16,
          backgroundColor: c.primaryContainer,
          elevation: 4,
        }}
      >
        <Icon name="add" color={c.onPrimaryContainer} />
        <T weight="500" color={c.onPrimaryContainer}>
          Add VIP
        </T>
      </Pressable>

      <AddVip
        visible={adding}
        onClose={() => setAdding(false)}
        onAdd={(name, number) => {
          let id = ''
          demo.update((cfg2) => {
            const r = addVip(cfg2, { name, number: number || undefined })
            id = r.vip.id
            return r.config
          })
          setAdding(false)
          setEditing(id)
        }}
      />
      {vip ? <VipEditor vip={vip} demo={demo} onClose={() => setEditing(null)} /> : null}
    </View>
  )
}

function Field(props: { label: string; value: string; onChange: (v: string) => void; placeholder?: string; help?: string; phone?: boolean }) {
  const c = useColors()
  return (
    <View style={{ gap: 6 }}>
      <T size={12} weight="500" color={c.primary}>
        {props.label}
      </T>
      <TextInput
        value={props.value}
        onChangeText={props.onChange}
        placeholder={props.placeholder}
        placeholderTextColor={c.muted}
        keyboardType={props.phone ? 'phone-pad' : 'default'}
        style={{ height: 52, paddingHorizontal: 14, borderRadius: 8, borderWidth: 1, borderColor: c.outline, fontSize: 16, color: c.text }}
      />
      {props.help ? (
        <T size={12} muted>
          {props.help}
        </T>
      ) : null}
    </View>
  )
}

function AddVip({ visible, onClose, onAdd }: { visible: boolean; onClose: () => void; onAdd: (name: string, number: string) => void }) {
  const [name, setName] = useState('')
  const [number, setNumber] = useState('')
  const close = () => {
    setName('')
    setNumber('')
    onClose()
  }
  return (
    <Dialog
      visible={visible}
      title="Add VIP"
      onClose={close}
      actions={
        <>
          <Button kind="text" label="Cancel" onPress={close} />
          <Button
            kind="text"
            label="Add"
            onPress={() => {
              if (!name.trim()) return
              onAdd(name.trim(), number.trim())
              setName('')
              setNumber('')
            }}
          />
        </>
      }
    >
      <T muted>The real app opens your phone's contact picker. In the demo, type a name.</T>
      <Field label="Name" value={name} onChange={setName} placeholder="e.g. Mom" />
      <Field label="Phone number (optional)" value={number} onChange={setNumber} placeholder="+91 98765 43210" phone />
    </Dialog>
  )
}

function VipEditor({ vip, demo, onClose }: { vip: Vip; demo: DemoHandle; onClose: () => void }) {
  const c = useColors()
  const [aliases, setAliases] = useState(vip.aliases.join(', '))
  const [picking, setPicking] = useState(false)
  const [confirmRemove, setConfirmRemove] = useState(false)

  const close = () => {
    const list = [...new Set(aliases.split(',').map((a) => a.trim()).filter(Boolean))]
    if (list.join('\n') !== vip.aliases.join('\n')) demo.update((cfg) => updateVip(cfg, { ...vip, aliases: list }))
    onClose()
  }

  return (
    <>
      <Dialog visible={!picking && !confirmRemove} title={vip.name} onClose={close} actions={<Button kind="text" label="Done" onPress={close} />}>
        {vip.phones.length ? <T muted>{vip.phones.join('\n')}</T> : null}
        <Row
          title="Sound & vibration"
          subtitle={toneSummary(vip.sound, vip.vibration, demo.state.config.silentMode)}
          onPress={() => setPicking(true)}
          trailing={
            <Pressable
              onPress={() => {
                if (!demo.state.config.silentMode) playSound(vip.sound)
                vibrate(vip.vibration)
              }}
              hitSlop={8}
              accessibilityLabel="Play sound and vibration"
            >
              <Icon name="play-arrow" color={c.muted} />
            </Pressable>
          }
        />
        <Field
          label="Also match these names"
          value={aliases}
          onChange={setAliases}
          placeholder="e.g. Mum, Amma"
          help="If a chat app shows them differently, e.g. their Telegram name. Separate with commas."
        />
        <Button kind="text" danger label="Remove VIP" onPress={() => setConfirmRemove(true)} />
      </Dialog>

      <TonePicker
        visible={picking}
        title={vip.name}
        current={{ sound: vip.sound, vibration: vip.vibration }}
        silentMode={demo.state.config.silentMode}
        onClose={() => setPicking(false)}
        onPick={({ sound, vibration }) => demo.update((cfg) => updateVip(cfg, { ...vip, sound, vibration }))}
      />
      <Dialog
        visible={confirmRemove}
        title={`Remove ${vip.name}?`}
        onClose={() => setConfirmRemove(false)}
        actions={
          <>
            <Button kind="text" label="Cancel" onPress={() => setConfirmRemove(false)} />
            <Button
              kind="text"
              danger
              label="Remove"
              onPress={() => {
                demo.update((cfg) => removeVip(cfg, vip.id))
                onClose()
              }}
            />
          </>
        }
      >
        <T>Their chats will follow the app's tier again.</T>
      </Dialog>
    </>
  )
}
