import { Pressable, ScrollView, View } from 'react-native'
import { LANE_LABEL, clock, isPaused, pauseFor, plural, relativeTime, resume, setEnabled, type Lane } from '../../../src/model'
import { SCENARIOS, appByPkg, exampleSetup, type Scenario } from '../demo'
import { useColors } from '../theme'
import { AppIcon, Button, Card, Chip, Icon, LaneBadge, LaneIcon, ScreenTitle, Switch, T } from '../ui'
import type { DemoHandle, Simulation } from '../useDemo'

export function Home(props: { demo: DemoHandle; last: Simulation | null; onSimulate: (s: Scenario) => void; openApps: () => void; openVips: () => void }) {
  const { demo, last } = props
  const { config: c, week, recent } = demo.state
  const now = demo.now
  const firstVip = c.vips.length ? Math.min(...c.vips.map((v) => v.addedAt)) : 0

  return (
    <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }}>
      <ScreenTitle title="TierTone" subtitle="Hear who matters." />

      <Card tone="strong">
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <View style={{ flex: 1 }}>
            <T size={22} weight="600">
              {!c.enabled ? 'TierTone is off' : isPaused(c, now) ? `Paused until ${clock(c.pausedUntil)}` : 'Sorting is on'}
            </T>
            <T muted>
              {!c.enabled
                ? 'Every app is using its own sound.'
                : isPaused(c, now)
                  ? 'Apps keep their own sounds until then.'
                  : `${plural(Object.keys(c.apps).length, 'app')} sorted · ${plural(c.vips.length, 'VIP')}${
                      c.silentMode ? ' · Silent mode: vibrations only' : ''
                    }`}
            </T>
          </View>
          <Switch value={c.enabled} onChange={(on) => demo.update((x) => setEnabled(x, on))} />
        </View>
        {c.enabled ? (
          isPaused(c, now) ? (
            <Button kind="tonal" label="Resume now" onPress={() => demo.update(resume)} />
          ) : (
            <>
              <PauseChips demo={demo} />
              <View style={{ flexDirection: 'row' }}>
                <Chip
                  label={c.silentMode ? 'Silent mode on' : 'Silent mode'}
                  selected={c.silentMode}
                  onPress={() => demo.update((x) => ({ ...x, silentMode: !x.silentMode }))}
                />
              </View>
            </>
          )
        ) : null}
      </Card>

      <TryIt {...props} />

      {Object.keys(c.apps).length === 0 && c.vips.length === 0 ? (
        <Card tone="primary">
          <T size={16} weight="500">
            Start with two taps
          </T>
          <T>Mark a noisy app Muted, an essential one Important, and add the people you never want to miss.</T>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            <Button label="Sort apps" onPress={props.openApps} />
            <Button kind="surface" label="Add VIPs" onPress={props.openVips} />
          </View>
          <Button kind="text" label="Or load an example setup" onPress={() => demo.update(exampleSetup)} />
        </Card>
      ) : null}

      <Card>
        <T size={16} weight="500">
          Last 7 days
        </T>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginVertical: 4 }}>
          {(['vip', 'important', 'normal', 'muted'] as Lane[]).map((lane) => (
            <View key={lane} style={{ alignItems: 'center', minWidth: 64, gap: 2 }}>
              <LaneIcon lane={lane} size={40} />
              <T size={22} weight="600" style={{ marginTop: 4 }}>
                {week[lane] ?? 0}
              </T>
              <T size={11} muted>
                {LANE_LABEL[lane]}
              </T>
            </View>
          ))}
        </View>
        {(week.untouched ?? 0) > 0 ? (
          <T size={12} muted>
            {week.untouched} from apps you haven't listed kept their own sound.
          </T>
        ) : null}
        {firstVip > 0 ? (
          <T size={12} muted>
            VIP rules on for {now - firstVip < 86_400_000 ? 'less than a day' : plural(Math.floor((now - firstVip) / 86_400_000), 'day')}.
          </T>
        ) : null}
      </Card>

      <View>
        <T size={16} weight="500" style={{ marginTop: 8 }}>
          Recent
        </T>
        <T size={12} muted>
          Kept on this phone only. Never includes message text.
        </T>
      </View>
      {recent.length === 0 ? <T muted>Nothing yet. Try a notification above and it shows up here.</T> : null}
      {recent.slice(0, 12).map((e, i) => (
        <View key={`${e.time}-${i}`} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <AppIcon pkg={e.pkg} size={36} />
          <View style={{ flex: 1, gap: 2 }}>
            <T size={16}>{e.label}</T>
            <LaneBadge lane={e.lane} text={e.lane === 'vip' && e.vip ? `VIP · ${e.vip}` : undefined} />
          </View>
          <T size={12} muted>
            {relativeTime(e.time, now)}
          </T>
        </View>
      ))}
    </ScrollView>
  )
}

export function PauseChips({ demo }: { demo: DemoHandle }) {
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
      <T muted>Pause</T>
      {(
        [
          [30, '30 min'],
          [60, '1 hour'],
          [480, '8 hours'],
        ] as const
      ).map(([minutes, label]) => (
        <Chip key={label} label={label} onPress={() => demo.update((c) => pauseFor(c, minutes))} />
      ))}
    </View>
  )
}

function TryIt({ last, onSimulate }: { last: Simulation | null; onSimulate: (s: Scenario) => void }) {
  const c = useColors()
  return (
    <Card tone="lowest">
      <T size={16} weight="500">
        Try it
      </T>
      <T size={12} muted>
        Send yourself a pretend notification and hear what TierTone does with it.
      </T>
      <View style={{ gap: 8, marginTop: 4 }}>
        {SCENARIOS.map((s) => (
          <ScenarioButton key={s.id} s={s} onPress={() => onSimulate(s)} />
        ))}
      </View>
      {last ? (
        <View style={{ marginTop: 8, padding: 12, borderRadius: 12, backgroundColor: c.surface, gap: 6 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <LaneIcon lane={last.outcome.lane} size={28} />
            <LaneBadge lane={last.outcome.lane} text={last.outcome.vip ? `VIP · ${last.outcome.vip.name}` : undefined} />
          </View>
          <T>{last.outcome.why}</T>
        </View>
      ) : null}
    </Card>
  )
}

function ScenarioButton({ s, onPress }: { s: Scenario; onPress: () => void }) {
  const c = useColors()
  const app = appByPkg(s.pkg)?.label ?? s.pkg
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => ({
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
        paddingVertical: 10,
        paddingHorizontal: 12,
        borderRadius: 16,
        borderWidth: 1,
        borderColor: c.outlineSoft,
        backgroundColor: pressed ? c.surface : 'transparent',
      })}
    >
      <AppIcon pkg={s.pkg} size={32} />
      <View style={{ flex: 1 }}>
        <T weight="500">{s.group ? `${s.sender} in ${s.group}` : s.sender}</T>
        <T size={12} muted>
          {s.mutedInApp ? `${app} · group muted in ${app}` : app}
        </T>
      </View>
      <Icon name="send" size={20} color={c.primary} />
    </Pressable>
  )
}
