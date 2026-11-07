import AsyncStorage from '@react-native-async-storage/async-storage'
import { useCallback, useEffect, useRef, useState } from 'react'
import { defaultConfig, withDefaults, type Access, type Config, type Entry, type Lane } from '../../src/model'
import { appByPkg, decide, type Outcome, type Scenario } from './demo'
import { playSound, vibrate } from './sound'

const KEY = 'tiertone-demo-v1'

export interface DemoState {
  config: Config
  access: Access
  week: Record<Lane, number>
  recent: Entry[]
}

const EMPTY_WEEK: Record<Lane, number> = { vip: 0, important: 0, normal: 0, muted: 0, untouched: 0 }

function freshState(): DemoState {
  return { config: defaultConfig(), access: { listener: false, post: false, dnd: true }, week: { ...EMPTY_WEEK }, recent: [] }
}

export interface Simulation {
  scenario: Scenario
  outcome: Outcome
  at: number
}

export interface DemoHandle {
  state: DemoState
  now: number
  update: (change: (c: Config) => Config) => void
  setAccess: (access: Partial<Access>) => void
  simulate: (s: Scenario) => Simulation
  resetDemo: () => void
}

/** Everything the demo remembers, saved on the phone with AsyncStorage. */
export function useDemo(): DemoHandle | null {
  const [state, setState] = useState<DemoState | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const current = useRef<DemoState | null>(null)

  useEffect(() => {
    AsyncStorage.getItem(KEY)
      // Older saves lack newer fields (e.g. vibrations); fill them in.
      .then((raw) => (raw ? (JSON.parse(raw) as DemoState) : freshState()))
      .then((s) => ({ ...s, config: withDefaults(s.config) }))
      .catch(() => freshState())
      .then((s) => {
        current.current = s
        setState(s)
      })
    const timer = setInterval(() => setNow(Date.now()), 15_000)
    return () => clearInterval(timer)
  }, [])

  const commit = useCallback((next: DemoState) => {
    current.current = next
    setState(next)
    setNow(Date.now())
    void AsyncStorage.setItem(KEY, JSON.stringify(next))
  }, [])

  const update = useCallback(
    (change: (c: Config) => Config) => {
      const cur = current.current
      if (cur) commit({ ...cur, config: change(cur.config) })
    },
    [commit],
  )

  const setAccess = useCallback(
    (access: Partial<Access>) => {
      const cur = current.current
      if (cur) commit({ ...cur, access: { ...cur.access, ...access } })
    },
    [commit],
  )

  const simulate = useCallback(
    (scenario: Scenario): Simulation => {
      const cur = current.current!
      const at = Date.now()
      const outcome = decide(scenario, cur.config, at)
      playSound(outcome.sound)
      vibrate(outcome.vibration)
      const entry: Entry = {
        time: at,
        pkg: scenario.pkg,
        label: appByPkg(scenario.pkg)?.label ?? scenario.pkg,
        lane: outcome.lane,
        ...(outcome.vip ? { vip: outcome.vip.name } : {}),
      }
      commit({
        ...cur,
        week: { ...cur.week, [outcome.lane]: (cur.week[outcome.lane] ?? 0) + 1 },
        recent: [entry, ...cur.recent].slice(0, 20),
      })
      return { scenario, outcome, at }
    },
    [commit],
  )

  const resetDemo = useCallback(() => {
    void AsyncStorage.removeItem(KEY)
    commit(freshState())
  }, [commit])

  if (!state) return null
  return { state, now, update, setAccess, simulate, resetDemo }
}
