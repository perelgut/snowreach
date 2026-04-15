/**
 * useAuth — convenience hook for reading Firebase Auth state.
 *
 * Returns { currentUser, userProfile, loading, signIn, signOut }
 * from the nearest AuthProvider.
 *
 * Throws if called outside of <AuthProvider> so misconfiguration
 * is caught at development time rather than silently returning null.
 */

import { useContext } from 'react'
import { AuthContext } from '../context/AuthContext'

export default function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be called inside <AuthProvider>')
  return ctx
}
