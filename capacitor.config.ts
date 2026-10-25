import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'app.tiertone',
  appName: 'TierTone',
  webDir: 'dist',
  // Capacitor's debug logging prints every bridge result (VIP names, numbers) to Logcat.
  // TierTone promises not to log personal data, so keep it off in every build.
  loggingBehavior: 'none',
  android: {
    // The UI is bundled into the APK; nothing is loaded from the network.
    allowMixedContent: false,
  },
}

export default config
