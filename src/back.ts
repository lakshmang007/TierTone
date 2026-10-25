import { App as CapApp } from '@capacitor/app'
import { useEffect, useRef } from 'react'

// Android's back button: the most recently opened thing (a dialog, then a non-Home tab,
// then an onboarding step) handles it. With nothing open, back closes the app so its
// process can be reclaimed.

const handlers: (() => void)[] = []

export function useBackHandler(enabled: boolean, handler: () => void) {
  const latest = useRef(handler)
  useEffect(() => {
    latest.current = handler
  })
  useEffect(() => {
    if (!enabled) return
    const h = () => latest.current()
    handlers.push(h)
    return () => {
      const i = handlers.lastIndexOf(h)
      if (i >= 0) handlers.splice(i, 1)
    }
  }, [enabled])
}

export function installBackButton() {
  void CapApp.addListener('backButton', () => {
    const top = handlers[handlers.length - 1]
    if (top) top()
    else void CapApp.exitApp()
  })
}
