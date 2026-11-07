import { StatusBar } from 'expo-status-bar'
import { useCallback, useEffect, useState } from 'react'
import { BackHandler, Pressable, View } from 'react-native'
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context'
import { Banner } from './src/Banner'
import type { Scenario } from './src/demo'
import { Apps } from './src/screens/Apps'
import { Home } from './src/screens/Home'
import { Onboarding } from './src/screens/Onboarding'
import { Settings } from './src/screens/Settings'
import { Vips } from './src/screens/Vips'
import { tint, useColors } from './src/theme'
import { Icon, T, type IconName } from './src/ui'
import { useDemo, type DemoHandle, type Simulation } from './src/useDemo'

type Tab = 'home' | 'apps' | 'vips' | 'settings'
const TABS: [Tab, string, IconName][] = [
  ['home', 'Home', 'home'],
  ['apps', 'Apps', 'list'],
  ['vips', 'VIPs', 'star'],
  ['settings', 'Settings', 'settings'],
]

export default function App() {
  return (
    <SafeAreaProvider>
      <Root />
    </SafeAreaProvider>
  )
}

function Root() {
  const c = useColors()
  const demo = useDemo()
  return (
    <View style={{ flex: 1, backgroundColor: c.bg }}>
      <StatusBar style="auto" />
      {demo ? demo.state.config.onboarded ? <Tabs demo={demo} /> : <Onboarding demo={demo} /> : null}
    </View>
  )
}

function Tabs({ demo }: { demo: DemoHandle }) {
  const c = useColors()
  const insets = useSafeAreaInsets()
  const [tab, setTab] = useState<Tab>('home')
  const [last, setLast] = useState<Simulation | null>(null)
  const [banner, setBanner] = useState<Simulation | null>(null)

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (tab === 'home') return false
      setTab('home')
      return true
    })
    return () => sub.remove()
  }, [tab])

  const simulate = useCallback(
    (s: Scenario) => {
      const sim = demo.simulate(s)
      setLast(sim)
      setBanner(sim)
    },
    [demo],
  )
  const hideBanner = useCallback(() => setBanner(null), [])

  return (
    <View style={{ flex: 1, paddingTop: insets.top }}>
      <View style={{ flex: 1 }}>
        {tab === 'home' && <Home demo={demo} last={last} onSimulate={simulate} openApps={() => setTab('apps')} openVips={() => setTab('vips')} />}
        {tab === 'apps' && <Apps demo={demo} />}
        {tab === 'vips' && <Vips demo={demo} />}
        {tab === 'settings' && <Settings demo={demo} />}
      </View>
      <View style={{ flexDirection: 'row', backgroundColor: c.surface, paddingBottom: insets.bottom, height: 80 + insets.bottom }}>
        {TABS.map(([key, label, icon]) => {
          const active = tab === key
          return (
            <Pressable key={key} onPress={() => setTab(key)} style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: 4 }}>
              <View style={{ width: 64, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center', backgroundColor: active ? tint(c.primary, 0.22) : 'transparent' }}>
                <Icon name={icon} color={active ? c.text : c.muted} />
              </View>
              <T size={12} weight="500" color={active ? c.text : c.muted}>
                {label}
              </T>
            </Pressable>
          )
        })}
      </View>
      <Banner sim={banner} onHide={hideBanner} />
    </View>
  )
}
