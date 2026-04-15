/**
 * api.js — Axios HTTP client for the YoSnowMow Spring Boot backend (P1-19).
 *
 * Every outgoing request automatically attaches the current user's Firebase ID
 * token as an Authorization: Bearer header.  If no user is signed in the
 * request is sent without a token and the backend will return 401.
 *
 * Base URL is read from VITE_API_BASE_URL (set in .env.local):
 *   Local dev:    http://localhost:8080
 *   Cloud Run:    https://yosnowmow-api-<hash>.a.run.app   (set in P1-02)
 */

import axios from 'axios'
import { auth } from './firebase'

// ── Axios instance ──────────────────────────────────────────────────────────

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 15000,
})

// Attach Firebase ID token to every request (if a user is signed in)
api.interceptors.request.use(async (config) => {
  const user = auth.currentUser
  if (user) {
    const token = await user.getIdToken()
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Log 4xx/5xx errors; surface 401 as a warning
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      console.warn('[api] 401 Unauthorized — Firebase token missing or expired')
    } else if (error.response?.status === 403) {
      console.warn('[api] 403 Forbidden — user lacks required role')
    } else {
      console.error('[api] Request failed:', error.response?.status, error.message)
    }
    return Promise.reject(error)
  }
)

// ── Admin API ───────────────────────────────────────────────────────────────

/**
 * Fetch platform summary statistics for the admin overview tab.
 * @returns {Promise<AdminStatsResponse>}
 */
export const getAdminStats = () =>
  api.get('/api/admin/stats').then(r => r.data)

/**
 * Fetch a paginated, optionally filtered list of jobs.
 * @param {{ status?, requesterId?, workerId?, page?, size? }} params
 * @returns {Promise<PagedResponse<Job>>}
 */
export const getAdminJobs = (params = {}) =>
  api.get('/api/admin/jobs', { params }).then(r => r.data)

/**
 * Fetch a paginated, optionally filtered list of users.
 * @param {{ role?, status?, page?, size? }} params
 * @returns {Promise<PagedResponse<User>>}
 */
export const getAdminUsers = (params = {}) =>
  api.get('/api/admin/users', { params }).then(r => r.data)

/**
 * Override the status of a job (admin only).
 * @param {string} jobId
 * @param {string} targetStatus
 * @param {string} reason
 * @returns {Promise<Job>}
 */
export const overrideJobStatus = (jobId, targetStatus, reason) =>
  api.patch(`/api/admin/jobs/${jobId}/status`, { targetStatus, reason }).then(r => r.data)

/**
 * Issue a full refund for a job (admin only).
 * @param {string} jobId
 * @returns {Promise<void>}
 */
export const refundJob = (jobId) =>
  api.post(`/api/admin/jobs/${jobId}/refund`).then(r => r.data)

/**
 * Force-release the worker payout for a job (admin only).
 * @param {string} jobId
 * @returns {Promise<void>}
 */
export const releasePayment = (jobId) =>
  api.post(`/api/admin/jobs/${jobId}/release`).then(r => r.data)

// ── Job API ─────────────────────────────────────────────────────────────────

/**
 * Post a new job.
 * @param {{ scope: string[], propertyAddressText: string, startWindowEarliest?: string,
 *           startWindowLatest?: string, notesForWorker?: string,
 *           personalWorkerOnly?: boolean }} data
 * @returns {Promise<Job>}
 */
export const postJob = (data) =>
  api.post('/api/jobs', data).then(r => r.data)

/**
 * List jobs for the current user (requester sees their own jobs; admin sees all).
 * @param {{ status?: string, page?: number, size?: number }} params
 * @returns {Promise<Job[]>}
 */
export const listJobs = (params = {}) =>
  api.get('/api/jobs', { params }).then(r => r.data)

/**
 * Fetch a single job by ID (admin or job participant).
 * @param {string} jobId
 * @returns {Promise<Job>}
 */
export const getJob = (jobId) =>
  api.get(`/api/jobs/${jobId}`).then(r => r.data)

/**
 * Cancel a job. A $10 + HST fee applies if status is CONFIRMED.
 * @param {string} jobId
 * @returns {Promise<Job>}
 */
export const cancelJob = (jobId) =>
  api.post(`/api/jobs/${jobId}/cancel`).then(r => r.data)

/**
 * Open a dispute on a COMPLETE job (requester only, within 2-hour window).
 * @param {string} jobId
 * @param {string} reason
 * @returns {Promise<Job>}
 */
export const disputeJob = (jobId, reason) =>
  api.post(`/api/jobs/${jobId}/dispute`, { reason }).then(r => r.data)

// ── User API ─────────────────────────────────────────────────────────────────

/**
 * Fetch a user profile by UID (own profile or admin).
 * @param {string} userId
 * @returns {Promise<User>}
 */
export const getUser = (userId) =>
  api.get(`/api/users/${userId}`).then(r => r.data)

/**
 * Register or refresh the FCM device token for push notifications (P1-18).
 * Called after login and whenever the browser grants notification permission.
 * @param {string} userId
 * @param {string|null} fcmToken  null to clear the token
 * @returns {Promise<void>}
 */
export const updateFcmToken = (userId, fcmToken) =>
  api.patch(`/api/users/${userId}/fcm-token`, { fcmToken })
