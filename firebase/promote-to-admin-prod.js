#!/usr/bin/env node
/**
 * promote-to-admin-prod.js
 *
 * One-time script to grant the "admin" role to an existing Firebase Auth user
 * in the PRODUCTION project (yosnowmow-prod).
 *
 * What it does:
 *   1. Looks up the target email in Firebase Auth (prod).
 *   2. Reads any roles the account already holds.
 *   3. Adds "admin" to the roles list (idempotent — safe to run twice).
 *   4. Sets the updated roles as a Firebase custom claim (so ID tokens carry them).
 *   5. Merges the updated roles array into the Firestore users/{uid} document.
 *
 * Credentials:
 *   Uses Application Default Credentials (ADC).  Before running, authenticate:
 *
 *     gcloud auth application-default login
 *
 * Usage (from the repo root):
 *
 *   cd firebase
 *   npm install          # if node_modules not present
 *   node promote-to-admin-prod.js
 *
 * This script does NOT touch the emulators.  It operates directly against
 * the production Firebase project.  Review the output carefully before
 * considering the promotion complete.
 */

'use strict';

const admin = require('firebase-admin');

// ── Target ────────────────────────────────────────────────────────────────────

const TARGET_EMAIL   = 'perelgut@gmail.com';
const PROD_PROJECT   = 'yosnowmow-prod';
const ROLE_TO_GRANT  = 'admin';

// ── Initialise Admin SDK against production ───────────────────────────────────
// No emulator env vars are set, so all calls go to the real Firebase services.
//
// Credential resolution order (first match wins):
//   1. GOOGLE_APPLICATION_CREDENTIALS env var  → path to a service account JSON key
//   2. gcloud ADC file (~/.config/gcloud/application_default_credentials.json)
//      Created by: gcloud auth application-default login
//
// If neither is present the SDK falls through to the GCE metadata server and
// fails with ENOTFOUND metadata.google.internal on a local machine.

const credPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;

let credential;
if (credPath) {
  credential = admin.credential.cert(credPath);
  console.log(`Using service account key: ${credPath}`);
} else {
  // ADC — requires: gcloud auth application-default login
  credential = admin.credential.applicationDefault();
  console.log('Using Application Default Credentials (ADC)');
}

admin.initializeApp({ credential, projectId: PROD_PROJECT });

const auth = admin.auth();
const db   = admin.firestore();

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  console.log('══════════════════════════════════════════════════════════');
  console.log(' YoSnowMow — Promote to Admin (PRODUCTION)');
  console.log(`  Project:  ${PROD_PROJECT}`);
  console.log(`  Target:   ${TARGET_EMAIL}`);
  console.log(`  Role:     ${ROLE_TO_GRANT}`);
  console.log('══════════════════════════════════════════════════════════');
  console.log('');

  // ── Step 1: look up the Firebase Auth account ─────────────────────────────
  let userRecord;
  try {
    userRecord = await auth.getUserByEmail(TARGET_EMAIL);
  } catch (err) {
    if (err.code === 'auth/user-not-found') {
      console.error(`ERROR: No Firebase Auth account found for ${TARGET_EMAIL}.`);
      console.error('       The user must sign up before being promoted.');
      process.exit(1);
    }
    throw err;
  }

  const uid = userRecord.uid;
  console.log(`[AUTH]      Found account: ${TARGET_EMAIL} (uid=${uid})`);

  // ── Step 2: read existing roles from the Firestore document ──────────────
  // The Firestore document is the authoritative source for roles.
  // Custom claims must stay in sync with it.
  const snap = await db.collection('users').doc(uid).get();

  let currentRoles = [];
  if (snap.exists) {
    currentRoles = snap.data().roles ?? [];
    console.log(`[FIRESTORE] Current roles: [${currentRoles.join(', ') || 'none'}]`);
  } else {
    console.warn('[FIRESTORE] No users/{uid} document found — will create one.');
  }

  // ── Step 3: merge the new role (idempotent) ───────────────────────────────
  const updatedRoles = currentRoles.includes(ROLE_TO_GRANT)
    ? currentRoles
    : [...currentRoles, ROLE_TO_GRANT];

  if (currentRoles.includes(ROLE_TO_GRANT)) {
    console.log(`[SKIP]      ${TARGET_EMAIL} already has the "${ROLE_TO_GRANT}" role.`);
  }

  console.log(`[ROLES]     Updated roles: [${updatedRoles.join(', ')}]`);
  console.log('');

  // ── Step 4: set Firebase custom claims ───────────────────────────────────
  // The Spring FirebaseTokenFilter reads request.auth.token.roles from the
  // ID token custom claims to build Spring Security authorities.
  await auth.setCustomUserClaims(uid, { roles: updatedRoles });
  console.log('[AUTH]      Custom claims set:');
  console.log(`              roles = ${JSON.stringify(updatedRoles)}`);

  // ── Step 5: update Firestore document ────────────────────────────────────
  // Use merge:true so any existing fields (Stripe IDs, FCM token, etc.) are preserved.
  const now = admin.firestore.Timestamp.now();
  await db.collection('users').doc(uid).set(
    { uid, roles: updatedRoles, updatedAt: now },
    { merge: true },
  );
  console.log(`[FIRESTORE] users/${uid} updated with roles=[${updatedRoles.join(', ')}]`);

  console.log('');
  console.log('══════════════════════════════════════════════════════════');
  console.log(' Promotion complete.');
  console.log('');
  console.log(' IMPORTANT: The user must sign out and sign back in (or');
  console.log(' call getIdToken(true)) for the new role to appear in');
  console.log(' their ID token.');
  console.log('══════════════════════════════════════════════════════════');
}

main()
  .then(() => process.exit(0))
  .catch(err => {
    console.error('');
    console.error('FAILED:', err.message || err);
    process.exit(1);
  });
