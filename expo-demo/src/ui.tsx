import { MaterialIcons } from '@expo/vector-icons'
import type { ComponentProps, ReactNode } from 'react'
import { Modal, Pressable, ScrollView, StyleSheet, Switch as RNSwitch, Text, View, type StyleProp, type ViewStyle } from 'react-native'
import { LANE_LABEL, type Lane } from '../../src/model'
import { appByPkg } from './demo'
import { tint, useColors } from './theme'

export type IconName = ComponentProps<typeof MaterialIcons>['name']

export const LANE_ICON: Record<Lane, IconName> = {
  vip: 'star',
  important: 'bolt',
  normal: 'notifications',
  muted: 'notifications-off',
  untouched: 'notifications-none',
}

export function Icon({ name, size = 24, color }: { name: IconName; size?: number; color?: string }) {
  const c = useColors()
  return <MaterialIcons name={name} size={size} color={color ?? c.text} />
}

export function T(props: {
  children: ReactNode
  size?: number
  weight?: '400' | '500' | '600' | '700'
  color?: string
  muted?: boolean
  style?: StyleProp<import('react-native').TextStyle>
  numberOfLines?: number
}) {
  const c = useColors()
  return (
    <Text
      numberOfLines={props.numberOfLines}
      style={[
        { fontSize: props.size ?? 14, fontWeight: props.weight ?? '400', color: props.color ?? (props.muted ? c.muted : c.text), lineHeight: Math.round((props.size ?? 14) * 1.4) },
        props.style,
      ]}
    >
      {props.children}
    </Text>
  )
}

export function ScreenTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <View style={{ marginTop: 8, marginBottom: 4 }}>
      <T size={28} weight="600">
        {title}
      </T>
      {subtitle ? <T muted>{subtitle}</T> : null}
    </View>
  )
}

export function SectionLabel({ children }: { children: string }) {
  const c = useColors()
  return (
    <T size={12} weight="600" color={c.primary} style={{ marginTop: 16, letterSpacing: 0.8 }}>
      {children.toUpperCase()}
    </T>
  )
}

type Tone = 'default' | 'strong' | 'primary' | 'error' | 'lowest'

export function Card({ tone = 'default', children, style }: { tone?: Tone; children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const c = useColors()
  const bg = { default: c.surface, strong: c.surfaceStrong, primary: c.primaryContainer, error: c.errorContainer, lowest: c.surfaceLowest }[tone]
  return <View style={[styles.card, { backgroundColor: bg }, tone === 'lowest' && { borderWidth: 1, borderColor: c.outlineSoft }, style]}>{children}</View>
}

export function Switch({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  const c = useColors()
  return (
    <RNSwitch
      value={value}
      onValueChange={onChange}
      trackColor={{ false: c.surfaceStrong, true: c.primary }}
      thumbColor={value ? c.onPrimary : c.muted}
    />
  )
}

export function Row(props: { title: string; subtitle?: string; trailing?: ReactNode; onPress?: () => void }) {
  const c = useColors()
  return (
    <Pressable
      onPress={props.onPress}
      disabled={!props.onPress}
      style={({ pressed }) => [styles.row, pressed && { backgroundColor: c.outlineSoft }]}
    >
      <View style={{ flex: 1 }}>
        <T size={16} weight="500">
          {props.title}
        </T>
        {props.subtitle ? (
          <T size={12} muted>
            {props.subtitle}
          </T>
        ) : null}
      </View>
      {props.trailing}
    </Pressable>
  )
}

export function SwitchRow(props: { title: string; subtitle?: string; value: boolean; onChange: (v: boolean) => void }) {
  return <Row title={props.title} subtitle={props.subtitle} onPress={() => props.onChange(!props.value)} trailing={<Switch value={props.value} onChange={props.onChange} />} />
}

type ButtonKind = 'filled' | 'tonal' | 'text' | 'outlined' | 'surface'

export function Button(props: { label: string; onPress: () => void; kind?: ButtonKind; danger?: boolean; big?: boolean; icon?: IconName; style?: StyleProp<ViewStyle> }) {
  const c = useColors()
  const kind = props.kind ?? 'filled'
  const bg = { filled: c.primary, tonal: tint(c.primary, 0.18), text: 'transparent', outlined: 'transparent', surface: c.surfaceLowest }[kind]
  const fg = props.danger ? c.error : kind === 'filled' ? c.onPrimary : kind === 'tonal' ? c.text : c.primary
  return (
    <Pressable
      onPress={props.onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg, opacity: pressed ? 0.8 : 1 },
        kind === 'outlined' && { borderWidth: 1, borderColor: c.outline },
        kind === 'text' && { paddingHorizontal: 12 },
        props.big && { height: 52, alignSelf: 'stretch' },
        props.style,
      ]}
    >
      {props.icon ? <Icon name={props.icon} size={20} color={fg} /> : null}
      <T size={props.big ? 16 : 14} weight="500" color={fg}>
        {props.label}
      </T>
    </Pressable>
  )
}

export function Chip({ label, selected, onPress }: { label: string; selected?: boolean; onPress: () => void }) {
  const c = useColors()
  return (
    <Pressable
      onPress={onPress}
      style={[styles.chip, { borderColor: selected ? 'transparent' : c.outline, backgroundColor: selected ? tint(c.primary, 0.2) : 'transparent' }]}
    >
      <T weight="500">{label}</T>
    </Pressable>
  )
}

export function LaneIcon({ lane, size = 36 }: { lane: Lane; size?: number }) {
  const c = useColors()
  const color = c.lane[lane]
  return (
    <View style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: tint(color, 0.16), alignItems: 'center', justifyContent: 'center' }}>
      <MaterialIcons name={LANE_ICON[lane]} size={Math.round(size * 0.55)} color={color} />
    </View>
  )
}

export function LaneBadge({ lane, text, style }: { lane: Lane; text?: string; style?: StyleProp<ViewStyle> }) {
  const c = useColors()
  const color = c.lane[lane]
  return (
    <View style={[styles.badge, { backgroundColor: tint(color, 0.14) }, style]}>
      <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: color }} />
      <T size={11} weight="500" color={color}>
        {text ?? LANE_LABEL[lane]}
      </T>
    </View>
  )
}

/** Stand-in app icon: a coloured rounded square with the app's initial. */
export function AppIcon({ pkg, size = 40 }: { pkg: string; size?: number }) {
  const app = appByPkg(pkg)
  return (
    <View style={{ width: size, height: size, borderRadius: size * 0.28, backgroundColor: app?.color ?? '#888', alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ color: '#fff', fontSize: size * 0.45, fontWeight: '600' }}>{(app?.label ?? pkg)[0]}</Text>
    </View>
  )
}

export function Dialog(props: { visible: boolean; title: string; children: ReactNode; actions: ReactNode; onClose: () => void }) {
  const c = useColors()
  return (
    <Modal visible={props.visible} transparent animationType="fade" onRequestClose={props.onClose} statusBarTranslucent navigationBarTranslucent>
      <Pressable style={[styles.scrim, { backgroundColor: c.scrim }]} onPress={props.onClose}>
        <Pressable style={[styles.dialog, { backgroundColor: c.surface }]} onPress={() => undefined}>
          <T size={24} style={{ marginBottom: 16 }}>
            {props.title}
          </T>
          <ScrollView style={{ flexGrow: 0 }} contentContainerStyle={{ gap: 12 }} keyboardShouldPersistTaps="handled">
            {props.children}
          </ScrollView>
          <View style={styles.dialogActions}>{props.actions}</View>
        </Pressable>
      </Pressable>
    </Modal>
  )
}

export function WarningCard(props: { title: string; body: string; action: string; onAction: () => void }) {
  const c = useColors()
  return (
    <Card tone="error">
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
        <Icon name="warning" color={c.onErrorContainer} />
        <T size={16} weight="500" color={c.onErrorContainer}>
          {props.title}
        </T>
      </View>
      <T color={c.onErrorContainer}>{props.body}</T>
      <Button kind="text" label={props.action} onPress={props.onAction} />
    </Card>
  )
}

export const styles = StyleSheet.create({
  card: { borderRadius: 20, padding: 16, gap: 8 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12, borderRadius: 12 },
  button: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, height: 40, paddingHorizontal: 20, borderRadius: 999, alignSelf: 'flex-start' },
  chip: { height: 32, paddingHorizontal: 14, borderRadius: 8, borderWidth: 1, justifyContent: 'center' },
  badge: { flexDirection: 'row', alignItems: 'center', gap: 5, paddingHorizontal: 8, paddingVertical: 2, borderRadius: 999, alignSelf: 'flex-start' },
  scrim: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  dialog: { width: '100%', maxWidth: 420, maxHeight: '88%', borderRadius: 28, padding: 24, paddingBottom: 12 },
  dialogActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8, paddingTop: 16 },
})
