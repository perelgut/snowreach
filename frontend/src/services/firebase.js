/**
 * firebase.js — Firebase app initialisation (Phase 1+)
 *
 * Reads all config from VITE_ environment variables so that
 * no credentials are ever hard-coded or committed to Git.
 *
 * Set VITE_USE_EMULATORS=true in .env.local to point all
 * SDK calls at the local Firebase Emulator Suite instead of
 * the live cloud projects. Required for all local Phase 1 dev.
 *
 * Usage:
 *   import { auth, db, storage } from './firebase'
 *
 * Emulator ports (must match firebase/firebase.json):
 *   Auth      → localhost:9099
 *   Firestore → localhost:8080
 *   Storage   → localhost:9199
 */

import { initializeApp }         from 'firebase/app'
import { getAuth, connectAuthEmulator }                 from 'firebase/auth'
import { getFirestore, connectFirestoreEmulator }       from 'firebase/firestore'
import { getStorage, connectStorageEmulator }           from 'firebase/storage'

// ---------------------------------------------------------------------------
// Config — all values injected at build time from .env / .env.local
// ---------------------------------------------------------------------------
const firebaseConfig = {
  apiKey:            import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain:        import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId:         import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket:     import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId:             import.meta.env.VITE_FIREBASE_APP_ID,
}

// ---------------------------------------------------------------------------
// Initialise the Firebase app (singleton — Vite hot-reload safe)
// ---------------------------------------------------------------------------
const app  = initializeApp(firebaseConfig)
const auth = getAuth(app)
const db   = getFirestore(app)
const storage = getStorage(app)

// ---------------------------------------------------------------------------
// Connect to emulators in local development
// Guards prevent double-connection on Vite HMR reloads.
// ---------------------------------------------------------------------------
if (import.meta.env.VITE_USE_EMULATORS === 'true') {
  // connectXxxEmulator throws if called twice on the same instance.
  // We track connection state on the instance objects to prevent that.
  if (!auth._canInitEmulator) {
    connectAuthEmulator(auth, 'http://localhost:9099', { disableWarnings: true })
  }
  if (!db._settings?.host?.includes('localhost')) {
    connectFirestoreEmulator(db, 'localhost', 8080)
  }
  if (!storage._protocol?.includes('localhost')) {
    connectStorageEmulator(storage, 'localhost', 9199)
  }
}

export { app, auth, db, storage }
