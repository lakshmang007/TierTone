# TierTone

An Android app that gives every notification a sound that tells you how much it matters.
UI in **React 19 + TypeScript + Vite**, wrapped as an Android APK with **Capacitor 8**. The
notification engine is native Kotlin, because Android only lets native code read notifications.
No internet permission, no contacts permission.

## Install on your phone

**Download:** open the repository's **Releases** page on your phone and download
`TierTone-<version>.apk` from the latest release. Newer versions install over older ones.

Android's Play Protect often blocks apps that ask for notification access when they're installed
from a downloaded file. If it does, either turn off *Play Store → Play Protect → Settings → Scan apps
with Play Protect* while installing (then turn it back on), or install over USB, which isn't blocked:

1. On the phone, open **Settings → About phone** and tap **Build number** 7 times. Then in
   **Settings → Developer options**, turn on **USB debugging** (on Xiaomi, also **Install via USB**).
2. Plug the phone in, tap **Allow** on the prompt, and run:

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r TierTone-0.2.0.apk
```

Open TierTone and follow the setup. If the notification-access switch is greyed out (Android 13+,
file installs), open App info, tap ⋮ and choose "Allow restricted settings".

## Releases

`.github/workflows/release.yml` builds, tests, signs and publishes the APK.

**To publish a version:** on GitHub, open **Actions → Release APK → Run workflow**, type a version
such as `0.2.1`, and run it. A few minutes later the release appears on the Releases page with
`TierTone-0.2.1.apk` attached, and the tag `v0.2.1` is created on GitHub. Leave the version empty to
build an APK without publishing it (it's attached to the workflow run instead).

Pushing a tag does the same (`git tag v0.2.1 && git push origin v0.2.1`), as long as local tag
signing works.

The version sets both numbers: `1.2.3` → versionName 1.2.3, versionCode 10203. Always publish a
higher version than the last one, or phones will refuse the update.

**Signing.** Every release is signed with the same key, so updates install over each other. The key
is `android/tiertone-release.jks`, and its passwords are in `android/keystore.properties`. Both
are git-ignored and must never be committed. GitHub gets them as four repository secrets (Settings →
Secrets and variables → Actions): `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. The values are generated in
`android/release-secrets.txt` (also git-ignored); delete that file once the secrets are added.

**Back up `tiertone-release.jks` and `keystore.properties` somewhere safe** (e.g. a password
manager). If the key is lost, phones with TierTone installed can't take updates until they
uninstall it.

## Project layout

```
src/                     React app (screens, components, state)
  native/tiertone.ts     TypeScript interface of the native plugin
  native/web.ts          Browser mock, so `npm run dev` works without a phone
android/                 Capacitor's Android project
  app/src/main/java/app/tiertone/
    TierTonePlugin.kt    Bridge: state, rules, app list, icons, contact & sound pickers, settings
    MainActivity.kt      Hosts the WebView (runs in its own ":ui" process)
    service/             NotificationListenerService, re-posting, action relay
    engine/              Rules engine and VIP matcher (pure Kotlin, unit tested)
    core/                Rules file, channels, activity stats
  app/src/test/          32 JVM unit tests
  sender/                Debug-only test app that posts WhatsApp-shaped chats
tools/gen_sounds.py      Generates the six bundled tones
```

## How it works

- **React UI** reads state with `TierTone.getState()` and saves rules with
  `TierTone.setConfig()`. Every change is written at once to `files/rules.json`, the same file the
  native listener reads, so the UI and engine never share memory.
- **SorterService** (native, main process) assigns every notification a tier. For handled ones it
  posts a copy on that tier's or VIP's channel, then cancels the original so the sound really
  changes. Calls, alarms, media, navigation and similar are never touched.
- **Two processes**: the WebView UI lives in `:ui` and is freed when you close the app. Measured on
  an API 35 emulator (release build): the always-on listener uses **7.7 MB PSS**; with the app open,
  the UI process uses about 80 MB plus about 82 MB for the WebView renderer.
- **Privacy**: the UI is bundled in the APK and served locally, so the app has no INTERNET
  permission. VIPs come from the system contact picker, so there's no READ_CONTACTS permission.
  Capacitor's bridge logging is off (`loggingBehavior: 'none'`) so nothing personal reaches Logcat.

## Develop

Needs Node 22+, the Android SDK (platform 36) and JDK 21 (Android Studio's bundled one works).

```bash
npm install
npm run dev            # UI in the browser with mock data: http://localhost:5173
npm run apk            # build web, sync into android/, build the debug APK
npm run apk:release    # same, release APK (R8-minified, ~1.1 MB)
```

Set `JAVA_HOME` to Android Studio's JDK before building, for example
`export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.

Native unit tests:

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

Release builds use `android/keystore.properties` if present (storeFile, storePassword, keyAlias,
keyPassword), otherwise the debug key so testers can install them.

## Test without WhatsApp

Debug builds treat the bundled test sender (and `adb shell cmd notification post`) as chat apps, so
VIPs can be tried on any phone or emulator:

```bash
adb install -r android/sender/build/outputs/apk/debug/sender-debug.apk
adb shell am start -n app.tiertone.sender/.PostActivity --es from Mom --es text "'call me back'"
adb shell am start -n app.tiertone.sender/.PostActivity --es from Ravi --es chat Family --es text "'keys?'" --ez silent true
adb logcat -s TierTone     # debug builds: one line per notification, never message text
```

## Device testing checklist

Run on at least one Samsung, one Xiaomi/Redmi/POCO and one Pixel or stock-Android phone:

- [ ] Onboarding grants access; Home shows "Sorting is on".
- [ ] A WhatsApp chat from a VIP plays the VIP sound, even with WhatsApp marked Muted.
- [ ] A WhatsApp group muted inside WhatsApp stays silent.
- [ ] Inline Reply from TierTone's copy reaches the chat; the copy disappears.
- [ ] Tapping a copy opens the right chat.
- [ ] Telegram: same checks. If the name differs from the phone contact, add an alias.
- [ ] VIP bypass on: a VIP rings in Do Not Disturb and with the ringer on Silent.
- [ ] After a reboot, sorting works without opening the app.
- [ ] After 24 hours in the background, sorting still works. If not, exclude TierTone from
      battery optimization (Xiaomi: also enable Autostart; Samsung: remove from Sleeping apps).

## Known limitations

- **A short blip of the original sound** can play before TierTone's: Android starts the app's
  sound before the listener sees the notification. Cancelling cuts it off immediately.
- **Read in the app, copy stays**: if you read a chat directly in WhatsApp, TierTone's copy stays
  until you swipe or tap it. Tapping, replying or marking as read from the copy clears it.
- **Notification cooldown** (Android 15+) can lower the volume of back-to-back notifications from
  one app, and all copies come from TierTone.
- **VIP matching** uses the name the chat app shows (WhatsApp uses your contacts' names) or a phone
  number when the app shares one; Telegram names can differ, so use aliases. It covers WhatsApp,
  WhatsApp Business, Telegram and Telegram X.
- Copies leave out inline images, custom layouts and conversation shortcuts; those belong to the
  source app and Android doesn't let another app re-post them.
- The previous native-only version (Jetpack Compose UI) is on the `main` branch.
