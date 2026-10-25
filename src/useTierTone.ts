import { App as CapApp } from '@capacitor/app'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { Config, NativeState } from './model'
import { TierTone } from './native/tiertone'

export interface AppHandle {
  state: NativeState
  /** Apply a change to the rules. Shown at once, saved to the native rules file in the background. */
  update: (change: (c: Config) => Config) => void
  refresh: () => Promise<void>
}

/**
 * Loads state from the native side and keeps it fresh while the screen is visible
 * (activity counts change as notifications arrive). Nothing polls when the app is hidden.
 */
export function useTierTone() {
  const [state, setState] = useState<NativeState | null>(null)
  const current = useRef<NativeState | null>(null)
  const pendingWrites = useRef(0)

  const refresh = useCallback(async () => {
    const next = await TierTone.getState()
    // While a save is in flight, keep the local rules so the UI never flickers back.
    if (pendingWrites.current > 0 && current.current) next.config = current.current.config
    current.current = next
    setState(next)
  }, [])

  useEffect(() => {
    void refresh()
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') void refresh()
    }, 4000)
    const onVisible = () => {
      if (document.visibilityState === 'visible') void refresh()
    }
    document.addEventListener('visibilitychange', onVisible)
    // Coming back from Android settings (e.g. after granting access) fires 'resume'.
    const resume = CapApp.addListener('resume', () => void refresh())
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
      void resume.then((h) => h.remove())
    }
  }, [refresh])

  const update = useCallback((change: (c: Config) => Config) => {
    const cur = current.current
    if (!cur) return
    const config = change(cur.config)
    if (config === cur.config) return
    const next = { ...cur, config }
    current.current = next
    setState(next)
    pendingWrites.current++
    TierTone.setConfig({ config })
      .catch((e: unknown) => console.error('Saving rules failed', e))
      .finally(() => pendingWrites.current--)
  }, [])

  return { state, update, refresh }
}
