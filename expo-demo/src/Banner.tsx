import { useEffect, useRef } from 'react'
import { Animated, Pressable, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { vibrationLabel } from '../../src/model'
import { appByPkg } from './demo'
import { soundLabel } from './SoundPicker'
import { useColors } from './theme'
import { AppIcon, LaneBadge, T } from './ui'
import type { Simulation } from './useDemo'

/** Looks like Android's heads-up notification, showing what TierTone did with it. */
export function Banner({ sim, onHide }: { sim: Simulation | null; onHide: () => void }) {
  const c = useColors()
  const insets = useSafeAreaInsets()
  const y = useRef(new Animated.Value(-200)).current

  useEffect(() => {
    if (!sim) return
    y.setValue(-200)
    Animated.spring(y, { toValue: 0, useNativeDriver: true, bounciness: 4 }).start()
    const t = setTimeout(() => Animated.timing(y, { toValue: -200, duration: 200, useNativeDriver: true }).start(onHide), 4500)
    return () => clearTimeout(t)
  }, [sim, y, onHide])

  if (!sim) return null
  const { scenario: s, outcome: o } = sim
  const app = appByPkg(s.pkg)?.label ?? s.pkg
  const sorted = o.lane !== 'untouched'
  const parts = [o.sound ? `plays ${soundLabel(o.sound)}` : '', o.vibration ? vibrationLabel(o.vibration) : ''].filter(Boolean)
  const feel = parts.length ? parts.join(' · ') : sorted ? 'no sound or vibration' : "the app's own sound"
  return (
    <Animated.View style={{ position: 'absolute', left: 8, right: 8, top: insets.top + 8, transform: [{ translateY: y }], zIndex: 10 }}>
      <Pressable onPress={onHide} style={{ borderRadius: 24, padding: 14, gap: 6, backgroundColor: c.surfaceStrong, elevation: 8 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <AppIcon pkg={s.pkg} size={20} />
          <T size={12} muted>
            {sorted ? `TierTone • ${app}` : app} • now
          </T>
        </View>
        <T size={15} weight="600">
          {s.group ? `${s.group}: ${s.sender}` : s.sender}
        </T>
        <T numberOfLines={2}>{s.text}</T>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 2 }}>
          <LaneBadge lane={o.lane} text={o.vip ? `VIP · ${o.vip.name}` : undefined} />
          <T size={12} muted>
            {feel}
          </T>
        </View>
      </Pressable>
    </Animated.View>
  )
}
