/**
 * AuthContext.jsx — Firebase Auth state for the entire app.
 *
 * Wraps the app with <AuthProvider> (see main.jsx).  All components
 * access auth state via the useAuth() hook (hooks/useAuth.js).
 *
 * What it manages:
 *   currentUser   — Firebase User object, or null when signed out
 *   userProfile   — Firestore users/{uid} document data (includes roles[])
 *   loading       — true until the first onAuthStateChanged callback fires
 *   signIn()      — email + password sign-in
 *   signOut()     — signs out and clears profile state
 */

import { createContext, useEffect, useState } from 'react'
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
} from 'firebase/auth'
import { doc, getDoc } from 'firebase/firestore'
import { auth, db } from '../services/firebase'

export const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  // Firebase Auth user object (null = signed out)
  const [currentUser, setCurrentUser] = useState(null)

  // Firestore users/{uid} document — contains roles[], accountStatus, etc.
  // null while loading OR when no Firestore record exists yet.
  const [userProfile, setUserProfile] = useState(null)

  // Remains true until the first auth state callback fires so that
  // ProtectedRoute doesn't flash the login page on reload.
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // onAuthStateChanged fires once immediately (with the persisted user or
    // null) and again on every subsequent sign-in / sign-out.
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      setCurrentUser(firebaseUser)

      if (firebaseUser) {
        try {
          const snap = await getDoc(doc(db, 'users', firebaseUser.uid))
          setUserProfile(snap.exists() ? snap.data() : null)
        } catch (err) {
          console.error('[AuthContext] Failed to fetch user profile:', err)
          setUserProfile(null)
        }
      } else {
        setUserProfile(null)
      }

      setLoading(false)
    })

    // Clean up the listener when the provider unmounts
    return unsubscribe
  }, [])

  /** Email + password sign-in.  Returns the Firebase UserCredential. */
  const signIn = (email, password) =>
    signInWithEmailAndPassword(auth, email, password)

  /** Signs out the current user and clears profile state immediately. */
  const signOut = async () => {
    setUserProfile(null)
    await firebaseSignOut(auth)
  }

  return (
    <AuthContext.Provider value={{ currentUser, userProfile, loading, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  )
}
