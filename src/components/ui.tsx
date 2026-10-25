import { useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useBackHandler } from '../back'
import { LANE_LABEL, type Lane } from '../model'
import { TierTone } from '../native/tiertone'
import { Icon, LANE_ICON } from './icons'

export function ScreenTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <header className="screen-title">
      <h1>{title}</h1>
      {subtitle && <p className="muted">{subtitle}</p>}
    </header>
  )
}

export function SectionLabel({ children }: { children: ReactNode }) {
  return <h2 className="section-label">{children}</h2>
}

type CardTone = 'default' | 'strong' | 'primary' | 'error' | 'lowest'

export function Card({ tone = 'default', children }: { tone?: CardTone; children: ReactNode }) {
  return <section className={`card card-${tone}`}>{children}</section>
}

export function Switch({ checked, onChange, label }: { checked: boolean; onChange: (on: boolean) => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      className={`switch ${checked ? 'on' : ''}`}
      onClick={(e) => {
        e.stopPropagation()
        onChange(!checked)
      }}
    >
      <span className="thumb" />
    </button>
  )
}

export function SwitchRow(props: { title: string; subtitle?: string; checked: boolean; onChange: (on: boolean) => void }) {
  return (
    <div className="row clickable" onClick={() => props.onChange(!props.checked)}>
      <div className="row-text">
        <div className="row-title">{props.title}</div>
        {props.subtitle && <div className="row-sub">{props.subtitle}</div>}
      </div>
      <Switch checked={props.checked} onChange={props.onChange} label={props.title} />
    </div>
  )
}

export function Row(props: { title: string; subtitle?: ReactNode; trailing?: ReactNode; onClick?: () => void }) {
  return (
    <div className={`row ${props.onClick ? 'clickable' : ''}`} onClick={props.onClick}>
      <div className="row-text">
        <div className="row-title">{props.title}</div>
        {props.subtitle && <div className="row-sub">{props.subtitle}</div>}
      </div>
      {props.trailing}
    </div>
  )
}

export function LaneIcon({ lane, size = 36 }: { lane: Lane; size?: number }) {
  return (
    <span className={`lane-icon lane-${lane}`} style={{ width: size, height: size }}>
      <Icon name={LANE_ICON[lane]} size={Math.round(size * 0.55)} />
    </span>
  )
}

export function LaneBadge({ lane, text }: { lane: Lane; text?: string }) {
  return (
    <span className={`badge lane-${lane}`}>
      <span className="dot" />
      {text ?? LANE_LABEL[lane]}
    </span>
  )
}

export function WarningCard(props: { title: string; body: string; action: string; onAction: () => void }) {
  return (
    <Card tone="error">
      <div className="warning-title">
        <Icon name="warning" />
        <strong>{props.title}</strong>
      </div>
      <p>{props.body}</p>
      <button className="btn text on-error" onClick={props.onAction}>
        {props.action}
      </button>
    </Card>
  )
}

export function Dialog(props: { title: string; children: ReactNode; actions: ReactNode; onClose: () => void }) {
  useBackHandler(true, props.onClose)
  return createPortal(
    <div className="scrim" onClick={props.onClose}>
      <div className="dialog" role="dialog" aria-modal="true" aria-label={props.title} onClick={(e) => e.stopPropagation()}>
        <h2>{props.title}</h2>
        <div className="dialog-body">{props.children}</div>
        <div className="dialog-actions">{props.actions}</div>
      </div>
    </div>,
    document.body,
  )
}

// App icons are fetched one by one, only as they scroll into view, and kept for this session.
const iconCache = new Map<string, string | null>()

export function AppIcon({ pkg, size = 40 }: { pkg: string; size?: number }) {
  const ref = useRef<HTMLSpanElement>(null)
  const [, setLoaded] = useState(0)
  const url = iconCache.get(pkg)

  useEffect(() => {
    if (iconCache.has(pkg)) return
    const el = ref.current
    if (!el) return
    let alive = true
    const observer = new IntersectionObserver(
      (entries) => {
        if (!entries.some((e) => e.isIntersecting)) return
        observer.disconnect()
        TierTone.appIcon({ pkg, size: Math.round(size * window.devicePixelRatio) })
          .then((r) => iconCache.set(pkg, r.dataUrl ?? null))
          .catch(() => iconCache.set(pkg, null))
          .finally(() => alive && setLoaded((n) => n + 1))
      },
      { rootMargin: '300px' },
    )
    observer.observe(el)
    return () => {
      alive = false
      observer.disconnect()
    }
  }, [pkg, size])

  return (
    <span ref={ref} className="app-icon" style={{ width: size, height: size }}>
      {url && <img src={url} alt="" width={size} height={size} />}
    </span>
  )
}
