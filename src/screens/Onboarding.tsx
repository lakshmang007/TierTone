import { useState, type ReactNode } from 'react'
import { useBackHandler } from '../back'
import { Icon, type IconName } from '../components/icons'
import { Card, LaneIcon } from '../components/ui'
import type { Lane } from '../model'
import { TierTone } from '../native/tiertone'
import type { AppHandle } from '../useTierTone'

type Step = 'welcome' | 'access' | 'post' | 'done'

export function Onboarding({ app }: { app: AppHandle }) {
  const steps: Step[] = app.state.sdk >= 33 ? ['welcome', 'access', 'post', 'done'] : ['welcome', 'access', 'done']
  const [index, setIndex] = useState(0)
  const next = () => setIndex((i) => Math.min(i + 1, steps.length - 1))
  useBackHandler(index > 0, () => setIndex((i) => i - 1))

  switch (steps[index]) {
    case 'welcome':
      return <Welcome next={next} />
    case 'access':
      return <AccessStep app={app} next={next} />
    case 'post':
      return <PostStep app={app} next={next} />
    default:
      return <Done finish={() => app.update((c) => ({ ...c, onboarded: true }))} />
  }
}

function StepLayout(props: {
  icon?: IconName
  title: string
  body: string
  primary: string
  onPrimary: () => void
  secondary?: string
  onSecondary?: () => void
  children?: ReactNode
}) {
  return (
    <main className="step">
      <div className="step-content">
        {props.icon && (
          <span className="step-icon">
            <Icon name={props.icon} size={32} />
          </span>
        )}
        <h1>{props.title}</h1>
        <p className="lead muted">{props.body}</p>
        {props.children}
      </div>
      <div className="step-actions">
        <button className="btn filled big" onClick={props.onPrimary}>
          {props.primary}
        </button>
        {props.secondary && props.onSecondary && (
          <button className="btn text" onClick={props.onSecondary}>
            {props.secondary}
          </button>
        )}
      </div>
    </main>
  )
}

function TierLine({ lane, title, body }: { lane: Lane; title: string; body: string }) {
  return (
    <div className="tier-line">
      <LaneIcon lane={lane} size={44} />
      <div>
        <div className="row-title">{title}</div>
        <div className="muted">{body}</div>
      </div>
    </div>
  )
}

function Welcome({ next }: { next: () => void }) {
  return (
    <StepLayout
      title="Hear who matters"
      body="TierTone gives every notification a sound that tells you how much it matters, before you even look at your phone."
      primary="Get started"
      onPrimary={next}
    >
      <div className="tier-lines">
        <TierLine lane="vip" title="VIP contacts" body="Their own sound, even when the app is muted" />
        <TierLine lane="important" title="Important" body="Apps you never want to miss" />
        <TierLine lane="normal" title="Normal" body="Everything else, as usual" />
        <TierLine lane="muted" title="Muted" body="Still in your shade, never a sound" />
      </div>
    </StepLayout>
  )
}

function Bullets({ title, items, positive }: { title: string; items: string[]; positive: boolean }) {
  return (
    <div className="bullets">
      <h3>{title}</h3>
      {items.map((line) => (
        <div key={line} className={`bullet ${positive ? 'yes' : 'no'}`}>
          <Icon name={positive ? 'check' : 'close'} size={20} />
          <span>{line}</span>
        </div>
      ))}
    </div>
  )
}

function AccessStep({ app, next }: { app: AppHandle; next: () => void }) {
  const granted = app.state.access.listener
  const [help, setHelp] = useState(false)
  return (
    <StepLayout
      icon="lock"
      title="Notification access"
      body="To change a notification's sound, TierTone has to see it arrive. Android calls this notification access."
      primary={granted ? 'Continue' : 'Open notification access'}
      onPrimary={() => (granted ? next() : void TierTone.openSettings({ screen: 'notificationAccess' }))}
      secondary={granted ? undefined : 'The switch is greyed out?'}
      onSecondary={() => setHelp((h) => !h)}
    >
      <Bullets
        title="What TierTone reads"
        positive
        items={[
          'Which app sent a notification',
          "The sender's name, to recognise your VIPs",
          'The notification itself, only so it can be re-posted with the right sound',
        ]}
      />
      <Bullets
        title="What it never does"
        positive={false}
        items={[
          'Upload anything: it has no internet permission at all',
          'Keep message text: it is never saved or logged',
          'Read your contact list: you pick VIPs one at a time',
        ]}
      />
      <p className="strong">Everything is processed on this phone.</p>
      {granted && (
        <p className="granted">
          <Icon name="check" /> Access granted
        </p>
      )}
      {help && !granted && (
        <Card tone="primary">
          <p>
            Android 13+ blocks this switch for apps installed from a file. Open App info, tap ⋮ in the top corner, choose
            “Allow restricted settings”, then come back and try again.
          </p>
          <button className="btn text" onClick={() => void TierTone.openSettings({ screen: 'appDetails' })}>
            Open App info
          </button>
        </Card>
      )}
    </StepLayout>
  )
}

function PostStep({ app, next }: { app: AppHandle; next: () => void }) {
  const [denied, setDenied] = useState(false)
  const granted = app.state.access.post
  const allow = async () => {
    const r = await TierTone.requestNotifications()
    await app.refresh()
    if (r.granted) next()
    else setDenied(true)
  }
  return (
    <StepLayout
      icon="bell"
      title="Let TierTone show notifications"
      body="TierTone re-posts each sorted notification with its tier's sound, so it needs permission to show them. Until it has it, TierTone leaves every notification untouched."
      primary={granted ? 'Continue' : denied ? 'Open settings' : 'Allow'}
      onPrimary={() => {
        if (granted) next()
        else if (denied) void TierTone.openSettings({ screen: 'appNotifications' })
        else void allow()
      }}
      secondary={granted ? undefined : 'Not now'}
      onSecondary={next}
    />
  )
}

function Done({ finish }: { finish: () => void }) {
  return (
    <StepLayout
      icon="check"
      title="You're set"
      body="Nothing changes until you choose. Apps you haven't listed keep their own sound."
      primary="Start sorting"
      onPrimary={finish}
    >
      <div className="tier-lines">
        <TierLine lane="important" title="Pick your apps" body="In Apps, mark what's Important and what's Muted" />
        <TierLine lane="vip" title="Add VIPs" body="Choose people from your contacts and give each a sound" />
        <TierLine lane="normal" title="Pause anytime" body="From Home, for 30 minutes, an hour or the night" />
      </div>
    </StepLayout>
  )
}
