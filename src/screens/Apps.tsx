import { useEffect, useMemo, useState } from 'react'
import { Icon } from '../components/icons'
import { AppIcon, ScreenTitle } from '../components/ui'
import { CHAT_APPS, setTier, TIERS, type AppInfo, type Tier } from '../model'
import { TierTone } from '../native/tiertone'
import type { AppHandle } from '../useTierTone'

type Filter = 'all' | 'sorted' | 'recent'
const FILTERS: [Filter, string][] = [
  ['all', 'All'],
  ['sorted', 'Sorted'],
  ['recent', 'Notified recently'],
]

export function Apps({ app }: { app: AppHandle }) {
  const { config: c, seen } = app.state
  const [apps, setApps] = useState<AppInfo[] | null>(null)
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('all')

  // Configured and recently-seen packages are listed even if they have no launcher icon.
  const [extra] = useState(() => [...Object.keys(c.apps), ...Object.keys(seen)])
  useEffect(() => {
    void TierTone.listApps({ extra }).then((r) => setApps(r.apps))
  }, [extra])

  // Sorted by recent activity, then name; deliberately not by tier, so rows don't jump while you tap.
  const shown = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (apps ?? [])
      .filter((a) => !q || a.label.toLowerCase().includes(q) || a.pkg.toLowerCase().includes(q))
      .filter((a) => filter === 'all' || (filter === 'sorted' ? a.pkg in c.apps : a.pkg in seen))
      .sort((a, b) => (seen[b.pkg] ?? 0) - (seen[a.pkg] ?? 0) || a.label.localeCompare(b.label))
  }, [apps, query, filter, c.apps, seen])

  return (
    <main className="screen">
      <ScreenTitle
        title="Apps"
        subtitle={c.sortUnlisted ? "Apps you don't list use the Normal sound." : "Apps you don't list keep their own sound."}
      />
      <label className="search">
        <Icon name="search" />
        <input type="search" placeholder="Search apps" value={query} onChange={(e) => setQuery(e.target.value)} />
      </label>
      <div className="chips">
        {FILTERS.map(([key, label]) => (
          <button key={key} className={`chip ${filter === key ? 'selected' : ''}`} onClick={() => setFilter(key)}>
            {label}
          </button>
        ))}
      </div>
      {apps === null && <div className="spinner" aria-label="Loading apps" />}
      {apps !== null && shown.length === 0 && <p className="muted">No apps here yet.</p>}
      <ul className="app-list">
        {shown.map((a) => (
          <AppRow
            key={a.pkg}
            app={a}
            tier={c.apps[a.pkg]}
            sortUnlisted={c.sortUnlisted}
            onTier={(t) => app.update((x) => setTier(x, a.pkg, t))}
          />
        ))}
      </ul>
    </main>
  )
}

const STATUS: Record<Tier, string> = {
  important: 'Important sound, pops up on screen',
  normal: 'Normal sound',
  muted: 'Silent, still in your notification shade',
}

function AppRow(props: { app: AppInfo; tier?: Tier; sortUnlisted: boolean; onTier: (t: Tier | null) => void }) {
  const { app, tier } = props
  const status = tier ? STATUS[tier] : props.sortUnlisted ? 'Not listed · Normal sound' : 'Not listed · keeps its own sound'
  return (
    <li className="app-row">
      <div className="app-row-head">
        <AppIcon pkg={app.pkg} />
        <div className="row-text">
          <div className="row-title ellipsis">{app.label}</div>
          <div className="row-sub">{CHAT_APPS.has(app.pkg) ? `${status} · VIPs override` : status}</div>
        </div>
        {tier && (
          <button className="icon-btn" aria-label="Remove from list" onClick={() => props.onTier(null)}>
            <Icon name="close" />
          </button>
        )}
      </div>
      <div className="segmented" role="radiogroup" aria-label={`${app.label} tier`}>
        {TIERS.map((t) => (
          <button
            key={t.key}
            role="radio"
            aria-checked={tier === t.key}
            className={`lane-${t.key} ${tier === t.key ? 'selected' : ''}`}
            onClick={() => props.onTier(tier === t.key ? null : t.key)}
          >
            {tier === t.key && <Icon name="check" size={16} />}
            {t.label}
          </button>
        ))}
      </div>
    </li>
  )
}
