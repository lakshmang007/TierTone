import { useState } from 'react'
import { useBackHandler } from './back'
import { Icon, type IconName } from './components/icons'
import { Apps } from './screens/Apps'
import { Home } from './screens/Home'
import { Onboarding } from './screens/Onboarding'
import { Settings } from './screens/Settings'
import { Vips } from './screens/Vips'
import { useTierTone, type AppHandle } from './useTierTone'

type Tab = 'home' | 'apps' | 'vips' | 'settings'
const TABS: [Tab, string, IconName][] = [
  ['home', 'Home', 'home'],
  ['apps', 'Apps', 'apps'],
  ['vips', 'VIPs', 'star'],
  ['settings', 'Settings', 'settings'],
]

export default function App() {
  const { state, update, refresh } = useTierTone()
  if (!state) return <div className="loading" />
  const app: AppHandle = { state, update, refresh }
  return state.config.onboarded ? <Tabs app={app} /> : <Onboarding app={app} />
}

function Tabs({ app }: { app: AppHandle }) {
  const [tab, setTab] = useState<Tab>('home')
  useBackHandler(tab !== 'home', () => setTab('home'))
  return (
    <>
      {tab === 'home' && <Home app={app} openApps={() => setTab('apps')} openVips={() => setTab('vips')} />}
      {tab === 'apps' && <Apps app={app} />}
      {tab === 'vips' && <Vips app={app} />}
      {tab === 'settings' && <Settings app={app} />}
      <nav className="nav-bar">
        {TABS.map(([key, label, icon]) => (
          <button key={key} className={tab === key ? 'active' : ''} aria-current={tab === key ? 'page' : undefined} onClick={() => setTab(key)}>
            <span className="nav-pill">
              <Icon name={icon} />
            </span>
            {label}
          </button>
        ))}
      </nav>
    </>
  )
}
