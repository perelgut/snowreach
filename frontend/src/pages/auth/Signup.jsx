/**
 * Signup.jsx — New user registration.
 *
 * Flow:
 *  1. createUserWithEmailAndPassword — creates the Firebase Auth account.
 *  2. POST /api/users — creates the Firestore user profile.
 *  3. refreshProfile — loads the profile into AuthContext.
 *  4. Navigate to the role-appropriate landing page.
 */

import { useState }                          from 'react'
import { Link, useNavigate }                 from 'react-router-dom'
import useAuth                               from '../../hooks/useAuth'
import * as api                              from '../../services/api'
import Button                                from '../../components/Button/Button'
import Input                                 from '../../components/Input/Input'
import styles                                from './Login.module.css'

const TOS_VERSION            = '1.0'
const PRIVACY_POLICY_VERSION = '1.0'

function friendlyError(code) {
  switch (code) {
    case 'auth/email-already-in-use':  return 'An account with that email already exists.'
    case 'auth/invalid-email':         return 'Please enter a valid email address.'
    case 'auth/weak-password':         return 'Password must be at least 6 characters.'
    default:                           return 'Sign-up failed. Please try again.'
  }
}

function defaultPathForRoles(roles = []) {
  if (roles.includes('admin'))                                    return '/admin'
  if (roles.includes('worker') && !roles.includes('requester'))  return '/worker'
  return '/requester'
}

export default function Signup() {
  const { signUp, refreshProfile } = useAuth()
  const navigate = useNavigate()

  const [name,               setName]               = useState('')
  const [email,              setEmail]              = useState('')
  const [password,           setPassword]           = useState('')
  const [confirm,            setConfirm]            = useState('')
  const [dob,                setDob]                = useState('')
  const [phone,              setPhone]              = useState('')
  const [services,           setServices]           = useState([]) // 'snow-requester' | 'snow-worker' | 'lawn-requester' | 'lawn-worker'
  const [homeAddressText,    setHomeAddressText]    = useState('')
  const [baseAddressText,    setBaseAddressText]    = useState('')
  const [serviceRadiusKm,    setServiceRadiusKm]    = useState(5)

  function toggleService(id) {
    setServices(prev => prev.includes(id) ? prev.filter(s => s !== id) : [...prev, id])
  }

  // Derive backend roles from selected services
  function deriveRoles() {
    const roles = []
    if (services.some(s => s === 'snow-requester' || s === 'lawn-requester')) roles.push('requester')
    if (services.some(s => s === 'snow-worker'    || s === 'lawn-worker'))    roles.push('worker')
    return roles
  }

  const isRequester = services.some(s => s === 'snow-requester' || s === 'lawn-requester')
  const isWorker    = services.some(s => s === 'snow-worker'    || s === 'lawn-worker')
  const [tosAccepted, setTosAccepted] = useState(false)

  const [error,      setError]      = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')

    if (password !== confirm) { setError('Passwords do not match.'); return }
    if (services.length === 0) { setError('Please select at least one service.'); return }
    if (isRequester && !homeAddressText.trim()) { setError('Please enter your home address.'); return }
    if (isWorker    && !baseAddressText.trim()) { setError('Please enter your base address.'); return }
    if (!tosAccepted)          { setError('You must accept the Terms of Service and Privacy Policy.'); return }

    const roles = deriveRoles()

    setSubmitting(true)
    try {
      // Step 1 — create Firebase Auth account
      await signUp(email.trim(), password)

      // Step 2 — create Firestore profile (token is now available via auth.currentUser)
      await api.createUser({
        name:                  name.trim(),
        dateOfBirth:           dob,
        roles,
        tosVersion:            TOS_VERSION,
        privacyPolicyVersion:  PRIVACY_POLICY_VERSION,
        phoneNumber:           phone.trim() || null,
        homeAddressText:       isRequester ? homeAddressText.trim() : null,
      })

      // Step 2b — activate worker profile if worker role selected
      if (isWorker) {
        await api.activateWorker({
          designation:          'personal',
          baseAddressFullText:  baseAddressText.trim(),
          serviceRadiusKm,
        })
      }

      // Step 3 — pull the new profile into AuthContext
      await refreshProfile()

      // Step 4 — send to the right section
      navigate(defaultPathForRoles(roles), { replace: true })

    } catch (err) {
      setError(err.code ? friendlyError(err.code) : (err.response?.data?.message || err.message || 'Sign-up failed.'))
      setSubmitting(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>

        <div className={styles.brand}>
          <h1 className={styles.title}>YoSnowMow</h1>
          <p className={styles.subtitle}>Create your account</p>
        </div>

        <form onSubmit={handleSubmit} className={styles.form} noValidate>

          <Input label="Full name" type="text" value={name} onChange={e => setName(e.target.value)}
            placeholder="Jane Smith" required disabled={submitting} />

          <Input label="Email" type="email" value={email} onChange={e => setEmail(e.target.value)}
            placeholder="you@example.com" required disabled={submitting} />

          <Input label="Password" type="password" value={password} onChange={e => setPassword(e.target.value)}
            placeholder="At least 6 characters" required disabled={submitting} />

          <Input label="Confirm password" type="password" value={confirm} onChange={e => setConfirm(e.target.value)}
            placeholder="••••••••" required disabled={submitting} />

          <Input label="Date of birth (YYYY-MM-DD)" type="text" value={dob} onChange={e => setDob(e.target.value)}
            placeholder="1957-11-23" required disabled={submitting} />

          <Input label="Phone number (optional)" type="tel" value={phone} onChange={e => setPhone(e.target.value)}
            placeholder="+1 613 555 0100" disabled={submitting} />

          {/* Services */}
          <div>
            <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: '#1A202C' }}>
              I want to… (select all that apply)
            </div>
            {[
              { id: 'snow-requester', label: 'Request snow clearing' },
              { id: 'snow-worker',    label: 'Offer snow clearing' },
              { id: 'lawn-requester', label: 'Request lawn mowing' },
              { id: 'lawn-worker',    label: 'Offer lawn mowing' },
            ].map(opt => (
              <label key={opt.id} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, cursor: 'pointer', fontSize: 14 }}>
                <input type="checkbox" value={opt.id}
                  checked={services.includes(opt.id)}
                  onChange={() => toggleService(opt.id)}
                  disabled={submitting} />
                {opt.label}
              </label>
            ))}
          </div>

          {/* Home address — shown when requester service selected */}
          {isRequester && (
            <div>
              <Input
                label="Home address"
                type="text"
                value={homeAddressText}
                onChange={e => setHomeAddressText(e.target.value)}
                placeholder="123 Main St, Toronto, ON M5V 3A8"
                required
                disabled={submitting}
              />
              <p style={{ fontSize: 12, color: '#718096', marginTop: 2 }}>
                Used as a shortcut when you post a job for your own property.
              </p>
            </div>
          )}

          {/* Base address + service radius — shown when worker service selected */}
          {isWorker && (
            <div>
              <Input
                label="Base address"
                type="text"
                value={baseAddressText}
                onChange={e => setBaseAddressText(e.target.value)}
                placeholder="456 Oak Ave, Etobicoke, ON M8Y 2B3"
                required
                disabled={submitting}
              />
              <p style={{ fontSize: 12, color: '#718096', marginTop: 2, marginBottom: 12 }}>
                Your starting point — used to find nearby jobs.
              </p>

              <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: '#1A202C' }}>
                Acceptable distance from your base address
              </div>
              {[
                { label: '250 m',  value: 0.25 },
                { label: '1 km',   value: 1    },
                { label: '5 km',   value: 5    },
                { label: '10 km',  value: 10   },
                { label: '25 km',  value: 25   },
                { label: '50 km',  value: 50   },
              ].map(opt => (
                <label key={opt.value} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, cursor: 'pointer', fontSize: 14 }}>
                  <input
                    type="radio"
                    name="serviceRadius"
                    value={opt.value}
                    checked={serviceRadiusKm === opt.value}
                    onChange={() => setServiceRadiusKm(opt.value)}
                    disabled={submitting}
                  />
                  {opt.label}
                </label>
              ))}
            </div>
          )}

          {/* ToS */}
          <label style={{ display: 'flex', alignItems: 'flex-start', gap: 8, cursor: 'pointer', fontSize: 13, color: '#4A5568' }}>
            <input type="checkbox" checked={tosAccepted} onChange={e => setTosAccepted(e.target.checked)}
              disabled={submitting} style={{ marginTop: 2, flexShrink: 0 }} />
            I accept the Terms of Service (v{TOS_VERSION}) and Privacy Policy (v{PRIVACY_POLICY_VERSION}).
          </label>

          {error && <p className={styles.errorMsg} role="alert">{error}</p>}

          <Button type="submit" variant="primary" fullWidth loading={submitting}>
            Create account
          </Button>
        </form>

        <p className={styles.footer}>
          Already have an account?{' '}
          <Link to="/login" className={styles.link}>Sign in</Link>
        </p>

      </div>
    </div>
  )
}
