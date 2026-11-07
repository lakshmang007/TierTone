# TierTone demo (Expo Go)

Try TierTone's screens, rules and tier sounds on any phone with **Expo Go** from the Play Store,
with no APK install and nothing for Play Protect to block.

**What's simulated:** Expo Go can't read other apps' notifications (no app inside Expo Go can), so
the **Try it** panel on Home sends pretend notifications from WhatsApp, Slack, Instagram and others.
For each one you see what TierTone would do (VIP, Important, Normal, Muted or left alone), why, and
you hear the tier's sound and feel its vibration pattern (try **Silent mode** on Home: sounds off,
vibrations only). Real sorting needs the APK.

**What's the same as the real app:** the screens and flows, the six bundled tones, and the rules
and config helpers. `../src/model.ts` is shared with the main app (Metro `watchFolders`), and
`src/demo.ts` mirrors the Kotlin RuleEngine's main decisions.

## Run it

1. Install **Expo Go** on your phone from the Play Store.
2. Put the phone on the same Wi-Fi as this PC.
3. On the PC:

```bash
cd expo-demo
npm install
npx expo start
```

4. In Expo Go, tap **Scan QR code** and scan the QR code shown in the terminal.

If the phone is on a different network (or the QR code doesn't connect), use a tunnel instead:

```bash
npx expo start --tunnel
```

Your demo rules and activity are saved on the phone. *Settings → Start the demo over* resets them.

## Develop

```bash
npx tsc --noEmit     # type-check (includes ../src/model.ts)
npx expo-doctor      # dependency checks
```

Expo SDK 58, React Native 0.88. Use `npx expo install <package>` so versions match the SDK.
