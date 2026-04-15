#!/usr/bin/env node
/**
 * seed-emulator.js
 *
 * Creates test users in the Firebase Auth and Firestore emulators so that
 * every developer starts with a consistent set of accounts after each
 * emulator restart.
 *
 * Usage (from repo root or firebase/ directory):
 *
 *   # 1. Start the emulators first
 *   firebase emulators:start
 *
 *   # 2. Install deps (once)
 *   cd firebase && npm install
 *
 *   # 3. Run the seed
 *   node firebase/seed-emulator.js
 *   # or from firebase/:
 *   npm run seed
 *
 * The script is idempotent — running it multiple times is safe.  Existing
 * Auth accounts are updated (password + displayName) and their Firestore
 * documents are merged (no data loss for fields not listed here).
 *
 * Test credentials created:
 *
 *   requester@yosnowmow.test  Requester123!  roles: requester
 *   worker@yosnowmow.test     Worker123!     roles: worker
 *   both@yosnowmow.test       Both123!       roles: requester, worker
 *   admin@yosnowmow.test      Admin123!      roles: admin
 *
 * Emulator ports (must match firebase/firebase.json):
 *   Auth:      localhost:9099
 *   Firestore: localhost:8080
 */

'use strict';

// ── Emulator host overrides ────────────────────────────────────────────────
// These MUST be set before firebase-admin is initialised.
process.env.FIREBASE_AUTH_EMULATOR_HOST = 'localhost:9099';
process.env.FIRESTORE_EMULATOR_HOST     = 'localhost:8080';

const admin = require('firebase-admin');

// Use the same project ID the emulator is started with (from .firebaserc default).
// The FIREBASE_AUTH_EMULATOR_HOST and FIRESTORE_EMULATOR_HOST env vars already
// redirect all Admin SDK calls to the local emulator — no real credentials needed.
admin.initializeApp({ projectId: 'yosnowmow-dev' });

const auth = admin.auth();
const db   = admin.firestore();

// ── Timestamp helper ──────────────────────────────────────────────────────
const now = admin.firestore.Timestamp.now();

// ── Test-user definitions ─────────────────────────────────────────────────
//
// Each entry drives both Auth account creation and the users/{uid} Firestore
// document.  Fields match the User and WorkerProfile Java models exactly.

const TEST_USERS = [

  // ── Requester only ───────────────────────────────────────────────────────
  {
    email:       'requester@yosnowmow.test',
    password:    'Requester123!',
    displayName: 'Sarah Kowalski',
    roles:       ['requester'],
    firestoreDoc: {
      name:                    'Sarah Kowalski',
      dateOfBirth:             '1990-05-15',
      ageVerifiedAt:           now,
      tosVersion:              '1.0',
      tosAcceptedAt:           now,
      privacyPolicyVersion:    '1.0',
      privacyPolicyAcceptedAt: now,
      roles:                   ['requester'],
      accountStatus:           'active',
      phoneNumber:             '+16135550001',
      phoneVerifiedAt:         now,
      fcmToken:                null,
      launchZoneId:            null,
      createdAt:               now,
      updatedAt:               now,
    },
  },

  // ── Worker only ──────────────────────────────────────────────────────────
  {
    email:       'worker@yosnowmow.test',
    password:    'Worker123!',
    displayName: 'Alex Moreau',
    roles:       ['worker'],
    firestoreDoc: {
      name:                    'Alex Moreau',
      dateOfBirth:             '1985-11-22',
      ageVerifiedAt:           now,
      tosVersion:              '1.0',
      tosAcceptedAt:           now,
      privacyPolicyVersion:    '1.0',
      privacyPolicyAcceptedAt: now,
      roles:                   ['worker'],
      accountStatus:           'active',
      phoneNumber:             '+16135550002',
      phoneVerifiedAt:         now,
      fcmToken:                null,
      launchZoneId:            null,
      createdAt:               now,
      updatedAt:               now,

      // WorkerProfile sub-object (spec §3.1)
      worker: {
        designation:          'personal',

        baseAddress: {
          streetNumber: '123',
          street:       'Maple Ave',
          city:         'Oakville',
          province:     'ON',
          postalCode:   'L6J 2V3',
          fullText:     '123 Maple Ave, Oakville, ON L6J 2V3',
        },

        // Geocoded coordinates for the base address above
        baseCoords:              new admin.firestore.GeoPoint(43.4675, -79.6877),
        addressGeocodeMethod:    'google_maps',

        serviceRadiusKm:         10,
        bufferOptIn:             true,

        // Distance-based pricing tiers
        tiers: [
          { maxDistanceKm: 5,  pricePerJobCents: 4500 },
          { maxDistanceKm: 10, pricePerJobCents: 6000 },
        ],

        hstRegistered:           false,
        hstBusinessNumber:       null,

        stripeConnectAccountId:  null,
        stripeConnectStatus:     'not_connected',

        status:                  'available',
        consecutiveNonResponses: 0,
        activeJobCount:          0,
        capacityMax:             1,

        isEarlyAdopter:                      true,
        earlyAdopterCommissionJobsRemaining: 10,
        earlyAdopterRateExpiry:              null,
        isFoundingWorker:                    false,

        referralCode:     'ALEXM-001',
        referredByUserId: null,

        phoneVerifiedForJobs: true,

        // Stats — new worker, no history yet
        rating:               null,
        ratingCount:          0,
        completedJobCount:    0,
        acceptanceRate:       null,
        avgResponseTimeSec:   null,
        cancellationRate:     null,
        disputeRate:          null,
        cannotCompleteCount90d: 0,

        backgroundCheckStatus: 'not_submitted',
        backgroundCheckDate:   null,
      },
    },
  },

  // ── Dual role: Requester + Worker ────────────────────────────────────────
  // Jordan has a history of 15 completed jobs — useful for testing rating display.
  {
    email:       'both@yosnowmow.test',
    password:    'Both123!',
    displayName: 'Jordan Tremblay',
    roles:       ['requester', 'worker'],
    firestoreDoc: {
      name:                    'Jordan Tremblay',
      dateOfBirth:             '1992-03-08',
      ageVerifiedAt:           now,
      tosVersion:              '1.0',
      tosAcceptedAt:           now,
      privacyPolicyVersion:    '1.0',
      privacyPolicyAcceptedAt: now,
      roles:                   ['requester', 'worker'],
      accountStatus:           'active',
      phoneNumber:             '+16135550003',
      phoneVerifiedAt:         now,
      fcmToken:                null,
      launchZoneId:            null,
      createdAt:               now,
      updatedAt:               now,

      worker: {
        designation:          'personal',

        baseAddress: {
          streetNumber: '456',
          street:       'Pine St',
          city:         'Burlington',
          province:     'ON',
          postalCode:   'L7R 1A1',
          fullText:     '456 Pine St, Burlington, ON L7R 1A1',
        },

        baseCoords:           new admin.firestore.GeoPoint(43.3255, -79.7990),
        addressGeocodeMethod: 'google_maps',

        serviceRadiusKm:  8,
        bufferOptIn:      false,

        tiers: [
          { maxDistanceKm: 8, pricePerJobCents: 5000 },
        ],

        hstRegistered:    false,
        hstBusinessNumber: null,

        stripeConnectAccountId: null,
        stripeConnectStatus:    'not_connected',

        status:                  'available',
        consecutiveNonResponses: 0,
        activeJobCount:          0,
        capacityMax:             1,

        isEarlyAdopter:                      false,
        earlyAdopterCommissionJobsRemaining: 0,
        earlyAdopterRateExpiry:              null,
        isFoundingWorker:                    false,

        referralCode:     'JORDT-001',
        referredByUserId: null,

        phoneVerifiedForJobs: true,

        // Experienced worker — good for testing rating UI
        rating:               4.8,
        ratingCount:          15,
        completedJobCount:    15,
        acceptanceRate:       0.93,
        avgResponseTimeSec:   142,
        cancellationRate:     0.0,
        disputeRate:          0.0,
        cannotCompleteCount90d: 0,

        backgroundCheckStatus: 'not_submitted',
        backgroundCheckDate:   null,
      },
    },
  },

  // ── Worker: Scarborough (Benfrisco Crescent) ─────────────────────────────
  {
    email:       'worker2@yosnowmow.test',
    password:    'Worker2123!',
    displayName: 'Marcus Webb',
    roles:       ['worker'],
    firestoreDoc: {
      name:                    'Marcus Webb',
      dateOfBirth:             '1988-07-14',
      ageVerifiedAt:           now,
      tosVersion:              '1.0',
      tosAcceptedAt:           now,
      privacyPolicyVersion:    '1.0',
      privacyPolicyAcceptedAt: now,
      roles:                   ['worker'],
      accountStatus:           'active',
      phoneNumber:             '+16135550004',
      phoneVerifiedAt:         now,
      fcmToken:                null,
      launchZoneId:            null,
      createdAt:               now,
      updatedAt:               now,

      worker: {
        designation:          'personal',

        baseAddress: {
          streetNumber: '12',
          street:       'Benfrisco Crescent',
          city:         'Scarborough',
          province:     'ON',
          postalCode:   'M1V 1L5',
          fullText:     '12 Benfrisco Crescent, Scarborough, ON M1V 1L5',
        },

        // Geocoded coordinates for Agincourt / Scarborough area
        baseCoords:              new admin.firestore.GeoPoint(43.7934, -79.2750),
        addressGeocodeMethod:    'google_maps',

        serviceRadiusKm:         8,
        bufferOptIn:             true,

        tiers: [
          { maxDistanceKm: 4,  pricePerJobCents: 4800 },
          { maxDistanceKm: 8,  pricePerJobCents: 6200 },
        ],

        hstRegistered:           false,
        hstBusinessNumber:       null,

        stripeConnectAccountId:  null,
        stripeConnectStatus:     'not_connected',

        status:                  'available',
        consecutiveNonResponses: 0,
        activeJobCount:          0,
        capacityMax:             1,

        isEarlyAdopter:                      true,
        earlyAdopterCommissionJobsRemaining: 7,
        earlyAdopterRateExpiry:              null,
        isFoundingWorker:                    false,

        referralCode:     'MARCW-001',
        referredByUserId: null,

        phoneVerifiedForJobs: true,

        // Experienced Scarborough worker
        rating:               4.5,
        ratingCount:          22,
        completedJobCount:    22,
        acceptanceRate:       0.88,
        avgResponseTimeSec:   210,
        cancellationRate:     0.0,
        disputeRate:          0.0,
        cannotCompleteCount90d: 0,

        backgroundCheckStatus: 'not_submitted',
        backgroundCheckDate:   null,
      },
    },
  },

  // ── Worker: North York (5000 Yonge Street) ────────────────────────────────
  {
    email:       'worker3@yosnowmow.test',
    password:    'Worker3123!',
    displayName: 'Priya Sharma',
    roles:       ['worker'],
    firestoreDoc: {
      name:                    'Priya Sharma',
      dateOfBirth:             '1994-02-20',
      ageVerifiedAt:           now,
      tosVersion:              '1.0',
      tosAcceptedAt:           now,
      privacyPolicyVersion:    '1.0',
      privacyPolicyAcceptedAt: now,
      roles:                   ['worker'],
      accountStatus:           'active',
      phoneNumber:             '+16135550005',
      phoneVerifiedAt:         now,
      fcmToken:                null,
      launchZoneId:            null,
      createdAt:               now,
      updatedAt:               now,

      worker: {
        designation:          'personal',

        baseAddress: {
          streetNumber: '5000',
          street:       'Yonge Street',
          city:         'Toronto',
          province:     'ON',
          postalCode:   'M2N 5N8',
          fullText:     '5000 Yonge Street, Toronto, ON M2N 5N8',
        },

        // Geocoded coordinates for Willowdale / North York area
        baseCoords:              new admin.firestore.GeoPoint(43.7685, -79.4101),
        addressGeocodeMethod:    'google_maps',

        serviceRadiusKm:         6,
        bufferOptIn:             false,

        tiers: [
          { maxDistanceKm: 6,  pricePerJobCents: 5500 },
        ],

        hstRegistered:           false,
        hstBusinessNumber:       null,

        stripeConnectAccountId:  null,
        stripeConnectStatus:     'not_connected',

        status:                  'available',
        consecutiveNonResponses: 0,
        activeJobCount:          0,
        capacityMax:             1,

        isEarlyAdopter:                      false,
        earlyAdopterCommissionJobsRemaining: 0,
        earlyAdopterRateExpiry:              null,
        isFoundingWorker:                    false,

        referralCode:     'PRIYS-001',
        referredByUserId: null,

        phoneVerifiedForJobs: true,

        // Newer worker, high rating — good for testing dispatch ranking
        rating:               4.7,
        ratingCount:          8,
        completedJobCount:    8,
        acceptanceRate:       1.0,
        avgResponseTimeSec:   95,
        cancellationRate:     0.0,
        disputeRate:          0.0,
        cannotCompleteCount90d: 0,

        backgroundCheckStatus: 'not_submitted',
        backgroundCheckDate:   null,
      },
    },
  },

  // ── Admin ────────────────────────────────────────────────────────────────
  {
    email:       'admin@yosnowmow.test',
    password:    'Admin123!',
    displayName: 'Admin User',
    roles:       ['admin'],
    firestoreDoc: {
      name:                    'Admin User',
      dateOfBirth:             '1980-01-01',
      ageVerifiedAt:           now,
      tosVersion:              '1.0',
      tosAcceptedAt:           now,
      privacyPolicyVersion:    '1.0',
      privacyPolicyAcceptedAt: now,
      roles:                   ['admin'],
      accountStatus:           'active',
      phoneNumber:             null,
      phoneVerifiedAt:         null,
      fcmToken:                null,
      launchZoneId:            null,
      createdAt:               now,
      updatedAt:               now,
    },
  },
];

// ── Core seeding logic ────────────────────────────────────────────────────

/**
 * Creates or updates a single test user in Auth and Firestore.
 *
 * Auth: creates the account if it doesn't exist; updates password +
 * displayName if it does.  Custom claims are always overwritten so roles
 * stay in sync with this file.
 *
 * Firestore: uses set() with merge:true so any extra fields written by
 * other scripts (e.g. Stripe account IDs added manually) are not wiped.
 *
 * @param {object} def - User definition from TEST_USERS array.
 * @returns {Promise<string>} The Firebase Auth UID.
 */
async function seedUser(def) {
  let uid;

  // ── Step 1: create or locate the Auth account ──────────────────────────
  try {
    const existing = await auth.getUserByEmail(def.email);
    uid = existing.uid;

    await auth.updateUser(uid, {
      password:      def.password,
      displayName:   def.displayName,
      emailVerified: true,
    });
    console.log(`  [EXISTS]    Auth account found and refreshed: ${def.email} (${uid})`);

  } catch (err) {
    if (err.code !== 'auth/user-not-found') throw err;

    const created = await auth.createUser({
      email:         def.email,
      password:      def.password,
      displayName:   def.displayName,
      emailVerified: true,
    });
    uid = created.uid;
    console.log(`  [CREATED]   Auth account created: ${def.email} (${uid})`);
  }

  // ── Step 2: set custom claims ──────────────────────────────────────────
  // The Spring FirebaseTokenFilter reads these to build Spring authorities.
  await auth.setCustomUserClaims(uid, { roles: def.roles });
  console.log(`  [CLAIMS]    roles=${JSON.stringify(def.roles)}`);

  // ── Step 3: write Firestore document ──────────────────────────────────
  await db.collection('users').doc(uid).set(
    { ...def.firestoreDoc, uid },
    { merge: true },
  );
  console.log(`  [FIRESTORE] users/${uid} written`);

  return uid;
}

// ── Entry point ───────────────────────────────────────────────────────────

async function main() {
  console.log('═══════════════════════════════════════════════════════');
  console.log(' YoSnowMow — Firebase Emulator Seed Script');
  console.log('═══════════════════════════════════════════════════════');
  console.log(`  Auth emulator:      ${process.env.FIREBASE_AUTH_EMULATOR_HOST}`);
  console.log(`  Firestore emulator: ${process.env.FIRESTORE_EMULATOR_HOST}`);
  console.log('');

  for (const def of TEST_USERS) {
    console.log(`Seeding ${def.email}  [${def.roles.join(', ')}]`);
    await seedUser(def);
    console.log('');
  }

  console.log('───────────────────────────────────────────────────────');
  console.log(' Seed complete — test credentials:');
  console.log('');
  for (const def of TEST_USERS) {
    const rolesStr = def.roles.join(', ').padEnd(20);
    console.log(`  ${def.email.padEnd(32)}  ${def.password.padEnd(16)}  [${rolesStr}]`);
  }
  console.log('═══════════════════════════════════════════════════════');
}

main()
  .then(() => process.exit(0))
  .catch(err => {
    console.error('');
    console.error('Seed FAILED:', err.message || err);
    process.exit(1);
  });
