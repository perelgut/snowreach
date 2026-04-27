/**
 * Login.jsx — Email + password sign-in page.
 *
 * Behaviour:
 *  • If the user is already signed in, redirects immediately to their
 *    default section (admin / worker / requester) based on roles[].
 *  • On successful sign-in, waits for AuthContext to fetch the Firestore
 *    profile, then redirects to either:
 *      – the page they were trying to reach (location.state.from), or
 *      – the role-appropriate default route.
 *  • Translates Firebase error codes into friendly messages.
 */

import { useState, useEffect }      from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import useAuth                       from '../../hooks/useAuth'
import Button                        from '../../components/Button/Button'
import Input                         from '../../components/Input/Input'
import styles                        from './Login.module.css'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Maps Firebase auth error codes to user-facing messages.
 * Firebase v9 collapses user-not-found + wrong-password into
 * auth/invalid-credential for security; we handle both spellings.
 */
function friendlyError(code) {
  switch (code) {
    case 'auth/invalid-credential':
    case 'auth/user-not-found':
    case 'auth/wrong-password':
      return 'Incorrect email or password.'
    case 'auth/invalid-email':
      return 'Please enter a valid email address.'
    case 'auth/user-disabled':
      return 'This account has been suspended. Please contact support.'
    case 'auth/too-many-requests':
      return 'Too many failed attempts. Please try again later.'
    default:
      return 'Sign-in failed. Please try again.'
  }
}

/**
 * Returns the default landing path for a user given their roles array.
 * Admin takes precedence; worker-only lands on /worker; everyone else
 * (requester, dual-role, unknown) lands on /requester.
 */
function defaultPathForRoles(roles = []) {
  if (roles.includes('admin'))                                    return '/admin'
  if (roles.includes('worker') && !roles.includes('requester'))  return '/worker'
  return '/requester'
}

/**
 * Returns `from` only if the user's roles permit that section.
 * Prevents an admin being sent to /requester just because the app
 * root redirected there before they signed in.
 */
function resolveDestination(from, roles = []) {
  if (from) {
    if (from.startsWith('/admin')     && roles.includes('admin'))     return from
    if (from.startsWith('/worker')    && roles.includes('worker'))    return from
    if (from.startsWith('/requester') && roles.includes('requester')) return from
  }
  return defaultPathForRoles(roles)
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export default function Login() {
  const { signIn, currentUser, userProfile, loading } = useAuth()
  const navigate  = useNavigate()
  const location  = useLocation()

  const [email,      setEmail]      = useState('')
  const [password,   setPassword]   = useState('')
  const [error,      setError]      = useState('')
  const [submitting, setSubmitting] = useState(false)

  // The page the user was trying to reach before being redirected to /login
  const from = location.state?.from ?? null

  // Redirect as soon as we have both a logged-in user and their profile.
  // Covers two cases:
  //   1. User was already signed in when they landed on /login.
  //   2. Sign-in just completed and AuthContext finished loading the profile.
  useEffect(() => {
    if (!loading && currentUser && userProfile) {
      navigate(resolveDestination(from, userProfile.roles), { replace: true })
    }
  }, [loading, currentUser, userProfile, navigate, from])

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await signIn(email.trim(), password)
      // AuthContext's onAuthStateChanged listener will fetch the Firestore
      // profile; the useEffect above handles the redirect once it's ready.
    } catch (err) {
      setError(friendlyError(err.code))
      setSubmitting(false)
    }
  }

  // Render nothing while the initial auth state is being resolved so the
  // page doesn't flash before a redirect.
  if (loading) return null

  return (
    <div className={styles.page}>
      <div className={styles.card}>

        {/* Brand header */}
        <div className={styles.brand}>
          <h1 className={styles.title}>YoSnowMow</h1>
          <p className={styles.subtitle}>Sign in to your account</p>
        </div>

        {/* Sign-in form */}
        <form onSubmit={handleSubmit} className={styles.form} noValidate>
          <Input
            label="Email"
            type="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            placeholder="you@example.com"
            required
            disabled={submitting}
          />

          <Input
            label="Password"
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="••••••••"
            autoComplete="current-password"
            required
            disabled={submitting}
          />

          {error && (
            <p className={styles.errorMsg} role="alert">{error}</p>
          )}

          <Button type="submit" variant="primary" fullWidth loading={submitting}>
            Sign in
          </Button>
        </form>

        {/* Footer links */}
        <p className={styles.footer}>
          Don&rsquo;t have an account?{' '}
          <Link to="/signup" className={styles.link}>Sign up</Link>
        </p>

      </div>
    </div>
  )
}
