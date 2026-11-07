import { useEffect, useState, type ReactNode } from 'react'
import { Alert, BackHandler, ScrollView, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import type { Lane } from '../../../src/model'
import { useColors } from '../theme'
import { Button, Card, Icon, LaneIcon, T, type IconName } from '../ui'
import type { DemoHandle } from '../useDemo'

type Step = 'welcome' | 'access' | 'post' | 'done'
const STEPS: Step[] = ['welcome', 'access', 'post', 'done']

export function Onboarding({ demo }: { demo: DemoHandle }) {
  const [index, setIndex] = useState(0)
  const next = () => setIndex((i) => Math.min(i + 1, STEPS.length - 1))

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (index === 0) return false
      setIndex((i) => i - 1)
      return true
    })
    return () => sub.remove()
  }, [index])

  switch (STEPS[index]) {
    case 'welcome':
      return <Welcome next={next} />
    case 'access':
      return <AccessStep demo={demo} next={next} />
    case 'post':
      return <PostStep demo={demo} next={next} />
    default:
      return <Done finish={() => demo.update((c) => ({ ...c, onboarded: true }))} />
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
  const c = useColors()
  const insets = useSafeAreaInsets()
  return (
    <View style={{ flex: 1, backgroundColor: c.bg, paddingTop: insets.top + 32, paddingBottom: insets.bottom + 16, paddingHorizontal: 24 }}>
      <ScrollView contentContainerStyle={{ gap: 16 }} showsVerticalScrollIndicator={false}>
        {props.icon ? (
          <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: c.primaryContainer, alignItems: 'center', justifyContent: 'center' }}>
            <Icon name={props.icon} size={32} color={c.onPrimaryContainer} />
          </View>
        ) : null}
        <T size={28} weight="600">
          {props.title}
        </T>
        <T size={16} muted>
          {props.body}
        </T>
        {props.children}
      </ScrollView>
      <View style={{ paddingTop: 16, alignItems: 'center', gap: 4 }}>
        <Button big label={props.primary} onPress={props.onPrimary} />
        {props.secondary && props.onSecondary ? <Button kind="text" label={props.secondary} onPress={props.onSecondary} style={{ alignSelf: 'center' }} /> : null}
      </View>
    </View>
  )
}

function TierLine({ lane, title, body }: { lane: Lane; title: string; body: string }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
      <LaneIcon lane={lane} size={44} />
      <View style={{ flex: 1 }}>
        <T size={16} weight="500">
          {title}
        </T>
        <T muted>{body}</T>
      </View>
    </View>
  )
}

function DemoNote() {
  return (
    <Card tone="primary">
      <T weight="500">You're trying the demo</T>
      <T>
        Expo Go can't read other apps' notifications, so this demo simulates them. Everything else, the screens, rules and
        tier sounds, works like the real app.
      </T>
    </Card>
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
      <View style={{ gap: 14, marginTop: 8 }}>
        <TierLine lane="vip" title="VIP contacts" body="Their own sound, even when the app is muted" />
        <TierLine lane="important" title="Important" body="Apps you never want to miss" />
        <TierLine lane="normal" title="Normal" body="Everything else, as usual" />
        <TierLine lane="muted" title="Muted" body="Still in your shade, never a sound" />
      </View>
      <DemoNote />
    </StepLayout>
  )
}

function Bullets({ title, items, positive }: { title: string; items: string[]; positive: boolean }) {
  const c = useColors()
  return (
    <View style={{ gap: 6 }}>
      <T weight="500">{title}</T>
      {items.map((line) => (
        <View key={line} style={{ flexDirection: 'row', gap: 10 }}>
          <Icon name={positive ? 'check' : 'close'} size={20} color={positive ? c.primary : c.error} />
          <T style={{ flex: 1 }}>{line}</T>
        </View>
      ))}
    </View>
  )
}

function AccessStep({ demo, next }: { demo: DemoHandle; next: () => void }) {
  const c = useColors()
  const granted = demo.state.access.listener
  const open = () =>
    Alert.alert(
      'Notification access',
      'In the real app this opens Android\'s "Notification read, reply & control" screen, where you switch TierTone on. In the demo it is simulated.',
      [{ text: 'Grant (simulated)', onPress: () => demo.setAccess({ listener: true }) }],
    )
  return (
    <StepLayout
      icon="lock"
      title="Notification access"
      body="To change a notification's sound, TierTone has to see it arrive. Android calls this notification access."
      primary={granted ? 'Continue' : 'Open notification access'}
      onPrimary={granted ? next : open}
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
      <T weight="600">Everything is processed on this phone.</T>
      {granted ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <Icon name="check" color={c.good} />
          <T size={16} weight="500" color={c.good}>
            Access granted
          </T>
        </View>
      ) : null}
    </StepLayout>
  )
}

function PostStep({ demo, next }: { demo: DemoHandle; next: () => void }) {
  const granted = demo.state.access.post
  return (
    <StepLayout
      icon="notifications"
      title="Let TierTone show notifications"
      body="TierTone re-posts each sorted notification with its tier's sound, so it needs permission to show them. Until it has it, TierTone leaves every notification untouched."
      primary={granted ? 'Continue' : 'Allow'}
      onPrimary={() => {
        if (!granted) demo.setAccess({ post: true })
        next()
      }}
      secondary={granted ? undefined : 'Not now'}
      onSecondary={next}
    />
  )
}

function Done({ finish }: { finish: () => void }) {
  return (
    <StepLayout icon="check" title="You're set" body="Nothing changes until you choose. Apps you haven't listed keep their own sound." primary="Start sorting" onPrimary={finish}>
      <View style={{ gap: 14 }}>
        <TierLine lane="important" title="Pick your apps" body="In Apps, mark what's Important and what's Muted" />
        <TierLine lane="vip" title="Add VIPs" body="Choose people and give each a sound" />
        <TierLine lane="normal" title="Try it" body="On Home, send yourself pretend notifications and hear the tiers" />
      </View>
    </StepLayout>
  )
}
