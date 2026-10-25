import { AppIcon, Card, LaneBadge, LaneIcon, ScreenTitle, Switch, WarningCard } from '../components/ui'
import { clock, isPaused, pauseFor, plural, relativeTime, resume, setEnabled, type Lane } from '../model'
import { TierTone } from '../native/tiertone'
import type { AppHandle } from '../useTierTone'

export function Home({ app, openApps, openVips }: { app: AppHandle; openApps: () => void; openVips: () => void }) {
  const { config: c, access, week, recent, now } = app.state
  const firstVip = c.vips.length ? Math.min(...c.vips.map((v) => v.addedAt)) : 0

  const allowPost = async () => {
    const r = await TierTone.requestNotifications()
    await app.refresh()
    // Once Android stops showing the dialog, the only way is its settings screen.
    if (!r.granted) void TierTone.openSettings({ screen: 'appNotifications' })
  }

  return (
    <main className="screen">
      <ScreenTitle title="TierTone" subtitle="Hear who matters." />

      {!access.listener ? (
        <WarningCard
          title="Notification access is off"
          body="TierTone can't see notifications, so every app is using its own sound."
          action="Turn on access"
          onAction={() => void TierTone.openSettings({ screen: 'notificationAccess' })}
        />
      ) : (
        <Card tone="strong">
          <div className="status">
            <div>
              <div className="status-title">
                {!c.enabled ? 'TierTone is off' : isPaused(c, now) ? `Paused until ${clock(c.pausedUntil)}` : 'Sorting is on'}
              </div>
              <div className="muted">
                {!c.enabled
                  ? 'Every app is using its own sound.'
                  : isPaused(c, now)
                    ? 'Apps keep their own sounds until then.'
                    : `${plural(Object.keys(c.apps).length, 'app')} sorted · ${plural(c.vips.length, 'VIP')}`}
              </div>
            </div>
            <Switch checked={c.enabled} onChange={(on) => app.update((x) => setEnabled(x, on))} label="TierTone on" />
          </div>
          {c.enabled &&
            (isPaused(c, now) ? (
              <button className="btn tonal" onClick={() => app.update(resume)}>
                Resume now
              </button>
            ) : (
              <PauseChips app={app} />
            ))}
        </Card>
      )}

      {access.listener && !access.post && (
        <WarningCard
          title="TierTone can't show notifications"
          body="It needs this to re-post notifications with their tier's sound. Until then, everything is left untouched."
          action="Allow"
          onAction={() => void allowPost()}
        />
      )}

      {Object.keys(c.apps).length === 0 && c.vips.length === 0 && (
        <Card tone="primary">
          <strong>Start with two taps</strong>
          <p>Mark a noisy app Muted, an essential one Important, and add the people you never want to miss.</p>
          <div className="button-row">
            <button className="btn filled" onClick={openApps}>
              Sort apps
            </button>
            <button className="btn surface" onClick={openVips}>
              Add VIPs
            </button>
          </div>
        </Card>
      )}

      <Card>
        <strong>Last 7 days</strong>
        <div className="week">
          {(['vip', 'important', 'normal', 'muted'] as Lane[]).map((lane) => (
            <div key={lane} className="week-tile">
              <LaneIcon lane={lane} size={40} />
              <span className="week-count">{week[lane] ?? 0}</span>
              <span className="week-label">{lane === 'vip' ? 'VIP' : lane[0].toUpperCase() + lane.slice(1)}</span>
            </div>
          ))}
        </div>
        {(week.untouched ?? 0) > 0 && <p className="small muted">{week.untouched} from apps you haven't listed kept their own sound.</p>}
        {firstVip > 0 && (
          <p className="small muted">
            VIP rules on for {now - firstVip < 86_400_000 ? 'less than a day' : plural(Math.floor((now - firstVip) / 86_400_000), 'day')}.
          </p>
        )}
      </Card>

      <div>
        <h3 className="list-title">Recent</h3>
        <p className="small muted">Kept on this phone only. Never includes message text.</p>
      </div>
      {recent.length === 0 && <p className="muted">Nothing yet. New notifications will appear here as they're sorted.</p>}
      <ul className="recent">
        {recent.slice(0, 15).map((e, i) => (
          <li key={`${e.time}-${i}`}>
            <AppIcon pkg={e.pkg} size={36} />
            <div className="recent-text">
              <span>{e.label}</span>
              <LaneBadge lane={e.lane} text={e.lane === 'vip' && e.vip ? `VIP · ${e.vip}` : undefined} />
            </div>
            <span className="small muted">{relativeTime(e.time, now)}</span>
          </li>
        ))}
      </ul>
    </main>
  )
}

export function PauseChips({ app }: { app: AppHandle }) {
  return (
    <div className="chips">
      <span className="muted">Pause</span>
      {[
        [30, '30 min'],
        [60, '1 hour'],
        [480, '8 hours'],
      ].map(([minutes, label]) => (
        <button key={label} className="chip" onClick={() => app.update((c) => pauseFor(c, minutes as number))}>
          {label}
        </button>
      ))}
    </div>
  )
}
