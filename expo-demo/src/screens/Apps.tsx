import { useMemo, useState } from 'react'
import { Pressable, ScrollView, TextInput, View } from 'react-native'
import { CHAT_APPS, TIERS, setTier, type Tier } from '../../../src/model'
import { DEMO_APPS, type DemoApp } from '../demo'
import { tint, useColors } from '../theme'
import { AppIcon, Chip, Icon, ScreenTitle, T } from '../ui'
import type { DemoHandle } from '../useDemo'

type Filter = 'all' | 'sorted'

const STATUS: Record<Tier, string> = {
  important: 'Important sound, pops up on screen',
  normal: 'Normal sound',
  muted: 'Silent, still in your notification shade',
}

export function Apps({ demo }: { demo: DemoHandle }) {
  const c = useColors()
  const cfg = demo.state.config
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('all')

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase()
    return DEMO_APPS.filter((a) => !q || a.label.toLowerCase().includes(q)).filter((a) => filter === 'all' || a.pkg in cfg.apps)
  }, [query, filter, cfg.apps])

  return (
    <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }} keyboardShouldPersistTaps="handled">
      <ScreenTitle title="Apps" subtitle={cfg.sortUnlisted ? "Apps you don't list use the Normal sound." : "Apps you don't list keep their own sound."} />
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, height: 56, paddingHorizontal: 16, borderRadius: 28, backgroundColor: c.surfaceStrong }}>
        <Icon name="search" color={c.muted} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Search apps"
          placeholderTextColor={c.muted}
          style={{ flex: 1, fontSize: 16, color: c.text }}
        />
      </View>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Chip label="All" selected={filter === 'all'} onPress={() => setFilter('all')} />
        <Chip label="Sorted" selected={filter === 'sorted'} onPress={() => setFilter('sorted')} />
      </View>
      <T size={12} muted>
        Demo list. The real app shows the apps installed on your phone.
      </T>
      {shown.length === 0 ? <T muted>No apps here yet.</T> : null}
      {shown.map((a) => (
        <AppRow key={a.pkg} app={a} tier={cfg.apps[a.pkg]} sortUnlisted={cfg.sortUnlisted} onTier={(t) => demo.update((x) => setTier(x, a.pkg, t))} />
      ))}
    </ScrollView>
  )
}

function AppRow(props: { app: DemoApp; tier?: Tier; sortUnlisted: boolean; onTier: (t: Tier | null) => void }) {
  const c = useColors()
  const { app, tier } = props
  const status = tier ? STATUS[tier] : props.sortUnlisted ? 'Not listed · Normal sound' : 'Not listed · keeps its own sound'
  return (
    <View style={{ paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: c.outlineSoft }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <AppIcon pkg={app.pkg} />
        <View style={{ flex: 1 }}>
          <T size={16} weight="500" numberOfLines={1}>
            {app.label}
          </T>
          <T size={12} muted>
            {CHAT_APPS.has(app.pkg) ? `${status} · VIPs override` : status}
          </T>
        </View>
        {tier ? (
          <Pressable onPress={() => props.onTier(null)} hitSlop={8} accessibilityLabel="Remove from list">
            <Icon name="close" color={c.muted} />
          </Pressable>
        ) : null}
      </View>
      <View style={{ flexDirection: 'row', marginTop: 10, marginLeft: 52, borderWidth: 1, borderColor: c.outline, borderRadius: 999, overflow: 'hidden' }}>
        {TIERS.map((t, i) => {
          const selected = tier === t.key
          const color = c.lane[t.key]
          return (
            <Pressable
              key={t.key}
              onPress={() => props.onTier(selected ? null : t.key)}
              style={{
                flex: 1,
                height: 40,
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 4,
                borderLeftWidth: i === 0 ? 0 : 1,
                borderLeftColor: c.outline,
                backgroundColor: selected ? tint(color, 0.18) : 'transparent',
              }}
            >
              {selected ? <Icon name="check" size={16} color={color} /> : null}
              <T weight="500" color={selected ? color : c.text}>
                {t.label}
              </T>
            </Pressable>
          )
        })}
      </View>
    </View>
  )
}
