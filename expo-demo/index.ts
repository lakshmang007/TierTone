import { registerRootComponent } from 'expo'
import { randomUUID } from 'expo-crypto'

import App from './App'

// The shared model (../src/model.ts) uses crypto.randomUUID, which React Native lacks.
const g = globalThis as { crypto?: { randomUUID?: () => string } }
g.crypto = g.crypto ?? {}
g.crypto.randomUUID = g.crypto.randomUUID ?? randomUUID

registerRootComponent(App)
