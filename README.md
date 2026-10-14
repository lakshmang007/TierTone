# TierTone

An Android app that gives every notification a sound that tells you how much it matters.
Native Kotlin + Jetpack Compose, no internet permission, no contacts permission.

## What's in the MVP

| Spec item | Where |
|---|---|
| 1. Notification access onboarding (what it reads / never does, grant via Android settings) | `ui/Onboarding.kt` |
| 2. Three tiers (Important, Normal, Muted), each with its own sound | `core/Model.kt`, `core/Channels.kt` |
| 3. App-level tiering, unlisted apps default to Normal | `ui/AppsScreen.kt`, `engine/RuleEngine.kt` |
| 4. VIP contacts picked from the phone, each with a sound, override the app tier | `ui/VipsScreen.kt`, `engine/VipMatcher.kt` |
| 5. Cancel the original and re-post it on the tier's or VIP's channel | `service/SorterService.kt`, `service/Reposter.kt` |
| 6. Optional VIP Do Not Disturb bypass | `Channels.vipChannel` (`setBypassDnd` + alarm audio usage) |
| 7. Settings: on/off, pause, reset rules | `ui/SettingsScreen.kt`, Home status card |

Also included: on-device activity log and weekly counts (supports the success metric without
analytics), local-only crash report you can share from Settings, six bundled tones
(`tools/gen_sounds.py`), and a debug test sender app (`sender/`).

**Unlisted apps** are assigned Normal but left untouched by default, so installing TierTone
changes nothing until you choose. "Sort apps you haven't listed" (Settings) re-posts them on the
Normal channel too; core system apps are never taken over.

## How it works

- `SorterService` (a `NotificationListenerService`) runs in the app's main process. For each
  notification, `Extract` reads safe facts (package, category, flags, sender name, person URIs;
  never stored), `RuleEngine` decides, and `Reposter` posts a rebuilt copy on the right channel
  before cancelling the original. Cancelling the original also stops its sound.
- The UI runs in a separate `:ui` process. Once it's closed, Compose's memory is reclaimed and
  only the small listener stays. Measured on an API 35 emulator, release build: **11.2 MB PSS**
  for the listener process.
- Rules live in `files/rules.json` (written only by the UI). The listener reloads it when the file
  changes or when the UI broadcasts a change. Activity counts live in `files/activity.json`
  (written only by the listener).
- Channel sounds can't change after creation, so channel IDs include a hash of the sound.
  Changing a sound creates a new channel; the old one is deleted once nothing shows on it.
- Never touched: calls, alarms, navigation, media, progress, ongoing/foreground, full-screen,
  bubbles, other users' notifications. If TierTone can't post (permission revoked, channel
  blocked, 45+ own notifications), the original is left alone.
- Action buttons (Reply, Mark as read) go through `ActionRelay`, which fires the app's own action
  (including typed replies) and then removes TierTone's copy.

## Build

Needs the Android SDK (platform 36) and JDK 17+. Android Studio's bundled JDK works:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Outputs: `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/release/app-release.apk`
(about 1.5 MB). Release builds use `keystore.properties` if present (storeFile, storePassword,
keyAlias, keyPassword), otherwise the debug key so testers can install them.

## Try it on a device or emulator

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r sender/build/outputs/apk/debug/sender-debug.apk
```

Open TierTone and follow onboarding. On Android 13+, an APK installed from a browser or file
manager can't get notification access until you open App info, tap ⋮ and choose "Allow restricted
settings". The onboarding screen explains this.

Debug builds log one line per notification to Logcat (package, app, sender, category, decision;
never message text). This is build step 1 of the plan:

```bash
adb logcat -s TierTone
```

The test sender posts WhatsApp-shaped chats (per-chat MessagingStyle child, group summary, Reply
and Mark as read). Debug builds treat it, and `adb shell cmd notification post`, as chat apps so
VIPs can be tested without WhatsApp:

```bash
adb shell am start -n app.tiertone.sender/.PostActivity --es from Mom --es text "'call me back'"
adb shell am start -n app.tiertone.sender/.PostActivity --es from Ravi --es chat Family --es text "'keys?'" --ez silent true
```

## Device testing checklist (build step 5)

Run on at least one Samsung, one Xiaomi/Redmi/POCO, and one Pixel or stock-Android phone:

- [ ] Onboarding grants access; Home shows "Sorting is on".
- [ ] WhatsApp chat from a VIP plays the VIP sound, even with WhatsApp marked Muted.
- [ ] WhatsApp group the user muted inside WhatsApp stays silent.
- [ ] Inline Reply from TierTone's copy reaches the chat; the copy disappears.
- [ ] Tapping a copy opens the right chat.
- [ ] Telegram: same checks. If the name differs from the phone contact, add an alias.
- [ ] VIP bypass on: VIP rings in Do Not Disturb and with the ringer on Silent.
- [ ] After a reboot, sorting still works without opening the app.
- [ ] After 24 h in the background, sorting still works. If not, exclude TierTone from battery
      optimization (Xiaomi: also enable Autostart; Samsung: remove from Sleeping apps).

## Known limitations

- **A short blip of the original sound** can be heard before TierTone's, because Android starts
  the app's sound before the listener sees the notification. Cancelling cuts it off immediately.
- **Read in the app, copy stays.** When you read a chat directly in WhatsApp, WhatsApp removes its
  own notification, but that one was already replaced, so TierTone's copy stays until you swipe or
  tap it. Tapping, replying or marking as read from the copy all clear it.
- **Notification cooldown** (Android 15+) may lower the volume of back-to-back notifications
  from one app, and all copies come from TierTone. Turning off cooldown keeps VIP sounds loud.
- **VIP matching** uses the name the chat app shows (WhatsApp uses your contacts' names), or a
  phone number when the app shares one. Telegram names can differ, so use aliases.
- Copies leave out inline images, custom layouts and conversation shortcuts. These belong to the
  source app and Android doesn't let another app re-post them.
- VIP matching covers WhatsApp, WhatsApp Business, Telegram and Telegram X
  (`RuleEngine.CHAT_APPS`).
- Library versions are pinned to ones cached on the dev machine (AGP 8.13.1, Kotlin 2.2.20,
  Compose BOM 2025.10.01). Lint reports newer versions; upgrading is safe but not required.
