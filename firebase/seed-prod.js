#!/usr/bin/env node
/**
 * seed-prod.js
 *
 * Creates (or resets) test accounts in the PRODUCTION Firebase project
 * (yosnowmow-prod).  Mirrors seed-emulator.js exactly — same users, same
 * Firestore structure — but targets the real Firebase services instead of
 * the local emulators.
 *
 * Credentials:
 *   Uses Application Default Credentials (ADC).  Before running, authenticate:
 *
 *     gcloud auth application-default login
 *
 *   Or point GOOGLE_APPLICATION_CREDENTIALS at a service account key JSON.
 *
 * Usage (from firebase/ directory):
 *
 *   node seed-prod.js           # seed only (assumes prod was already wiped)
 *   node seed-prod.js --wipe    # wipe ALL Auth users + Firestore collections first, then seed
 *
 * WARNING:
 *   --wipe permanently deletes ALL Firebase Auth accounts and ALL documents
 *   in the users, jobs, offers, and ratings Firestore collections in the
 *   PRODUCTION project.  There is no undo.  Use with extreme care.
 *
 * Test credentials created:
 *
 *   requester@yosnowmow.test   Requester123!   roles: requester
 *   worker@yosnowmow.test      Worker123!      roles: worker
 *   both@yosnowmow.test        Both123!        roles: requester, worker
 *   worker2@yosnowmow.test     Worker2123!     roles: worker
 *   worker3@yosnowmow.test     Worker3123!     roles: worker
 *   admin@yosnowmow.test       Admin123!       roles: admin
 *
 * After seeding, promote your personal admin account:
 *   node promote-to-admin-prod.js
 */

'use strict';

// ── NO emulator env vars — all calls go to the real Firebase project ──────

const admin = require('firebase-admin');

const PROD_PROJECT   = 'yosnowmow-prod';
const WIPE_REQUESTED = process.argv.includes('--wipe');

// Collections to wipe when --wipe is passed
const COLLECTIONS_TO_WIPE = [
  'users', 'jobs', 'jobOffers', 'ratings',
  'notifications', 'analyticsDaily', 'analyticsSummary',
  'disputes', 'geocache', 'auditLog',
];

// ── Credentials ───────────────────────────────────────────────────────────

const credPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
let credential;
if (credPath) {
  credential = admin.credential.cert(credPath);
  console.log(`Using service account key: ${credPath}`);
} else {
  credential = admin.credential.applicationDefault();
  console.log('Using Application Default Credentials (ADC)');
}

admin.initializeApp({ credential, projectId: PROD_PROJECT });

const auth = admin.auth();
const db   = admin.firestore();

// ── Timestamp helper ──────────────────────────────────────────────────────
const now = admin.firestore.Timestamp.now();

// ── Test-user definitions ─────────────────────────────────────────────────

const TEST_USERS = [

  // ── Requester only ───────────────────────────────────────────────────────
  {
    email:       'requester@yosnowmow.test',
    password:    'Requester123!',
    displayName: 'Sarah Kowalski',
    roles:       ['requester'],
    firestoreDoc: {
      name:                    'Sarah Kowalski',
      homeAddressText:         '10 Test Requester Lane, Toronto, ON M5V 1A1',
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

      worker: {
        designation:          'personal',

        baseAddress: {
          streetNumber: '600',
          street:       'The East Mall',
          city:         'Etobicoke',
          province:     'ON',
          postalCode:   'M9B 4B1',
          fullText:     '600 The East Mall, Etobicoke, ON M9B 4B1',
        },

        baseCoords:              new admin.firestore.GeoPoint(43.6500, -79.5500),
        addressGeocodeMethod:    'google_maps',

        serviceRadiusKm:         30,
        bufferOptIn:             true,

        tiers: [
          { maxDistanceKm: 10, pricePerJobCents: 4500 },
          { maxDistanceKm: 20, pricePerJobCents: 5500 },
          { maxDistanceKm: 30, pricePerJobCents: 6500 },
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

        jobRejectionCount90d:  0,
        isActive:              true,
        onboardingCompletedAt: now,
        insuranceStatus:       'not_submitted',
      },
    },
  },

  // ── Dual role: Requester + Worker ─────────────────────────────────────────
  {
    email:       'both@yosnowmow.test',
    password:    'Both123!',
    displayName: 'Jordan Tremblay',
    roles:       ['requester', 'worker'],
    firestoreDoc: {
      name:                    'Jordan Tremblay',
      homeAddressText:         '456 Pine St, Burlington, ON L7R 1A1',
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

        jobRejectionCount90d:  0,
        isActive:              true,
        onboardingCompletedAt: now,
        insuranceStatus:       'not_submitted',
      },
    },
  },

  // ── Worker: Scarborough ───────────────────────────────────────────────────
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

        jobRejectionCount90d:  0,
        isActive:              true,
        onboardingCompletedAt: now,
        insuranceStatus:       'not_submitted',
      },
    },
  },

  // ── Worker: North York ────────────────────────────────────────────────────
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

        baseCoords:              new admin.firestore.GeoPoint(43.7685, -79.4101),
        addressGeocodeMethod:    'google_maps',

        serviceRadiusKm:         25,
        bufferOptIn:             false,

        tiers: [
          { maxDistanceKm: 10, pricePerJobCents: 5000 },
          { maxDistanceKm: 25, pricePerJobCents: 6500 },
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

        jobRejectionCount90d:  0,
        isActive:              true,
        onboardingCompletedAt: now,
        insuranceStatus:       'not_submitted',
      },
    },
  },

  // ── Admin ─────────────────────────────────────────────────────────────────
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

// ── Wipe helpers ──────────────────────────────────────────────────────────

/**
 * Deletes ALL Firebase Auth users in the project, in pages of 1000.
 */
async function wipeAuthUsers() {
  console.log('[WIPE] Deleting all Firebase Auth users ...');
  let pageToken;
  let totalDeleted = 0;

  do {
    const listResult = await auth.listUsers(1000, pageToken);
    const uids = listResult.users.map(u => u.uid);

    if (uids.length > 0) {
      const result = await auth.deleteUsers(uids);
      totalDeleted += result.successCount;
      if (result.failureCount > 0) {
        console.warn(`  [WARN] ${result.failureCount} accounts failed to delete`);
      }
      console.log(`  Deleted ${totalDeleted} accounts so far ...`);
    }

    pageToken = listResult.pageToken;
  } while (pageToken);

  console.log(`[WIPE] Auth: ${totalDeleted} accounts deleted.\n`);
}

/**
 * Deletes every document in a Firestore collection, in batches of 500.
 *
 * @param {string} collectionPath - Top-level collection name.
 */
async function wipeCollection(collectionPath) {
  console.log(`[WIPE] Clearing Firestore collection: ${collectionPath} ...`);
  const ref = db.collection(collectionPath);
  let totalDeleted = 0;

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const snapshot = await ref.limit(500).get();
    if (snapshot.empty) break;

    const batch = db.batch();
    snapshot.docs.forEach(doc => batch.delete(doc.ref));
    await batch.commit();
    totalDeleted += snapshot.size;
    console.log(`  ... ${totalDeleted} documents deleted`);
  }

  console.log(`[WIPE] ${collectionPath}: ${totalDeleted} documents deleted.\n`);
}

// ── Seed helper ───────────────────────────────────────────────────────────

/**
 * Creates or updates a single test user in Auth and Firestore.
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
    console.log(`  [EXISTS]    Refreshed: ${def.email} (${uid})`);
  } catch (err) {
    if (err.code !== 'auth/user-not-found') throw err;

    const created = await auth.createUser({
      email:         def.email,
      password:      def.password,
      displayName:   def.displayName,
      emailVerified: true,
    });
    uid = created.uid;
    console.log(`  [CREATED]   Created:  ${def.email} (${uid})`);
  }

  // ── Step 2: set custom claims ─────────────────────────────────────────
  await auth.setCustomUserClaims(uid, { roles: def.roles });
  console.log(`  [CLAIMS]    roles=${JSON.stringify(def.roles)}`);

  // ── Step 3: write Firestore document ──────────────────────────────────
  await db.collection('users').doc(uid).set(
    { ...def.firestoreDoc, uid, email: def.email },
    { merge: true },
  );
  console.log(`  [FIRESTORE] users/${uid} written`);

  return uid;
}

// ── Entry point ───────────────────────────────────────────────────────────

async function main() {
  console.log('═══════════════════════════════════════════════════════');
  console.log(' YoSnowMow — Production Seed Script');
  console.log(`  Project: ${PROD_PROJECT}`);
  if (WIPE_REQUESTED) {
    console.log('  Mode:    WIPE + SEED  ⚠️  ALL DATA WILL BE DELETED');
  } else {
    console.log('  Mode:    SEED ONLY (upsert)');
  }
  console.log('═══════════════════════════════════════════════════════');
  console.log('');

  // ── Wipe phase ────────────────────────────────────────────────────────
  if (WIPE_REQUESTED) {
    console.log('⚠️  WIPE PHASE — deleting all production Auth users and Firestore data');
    console.log('   You have 5 seconds to Ctrl-C to abort ...');
    await new Promise(resolve => setTimeout(resolve, 5000));
    console.log('');

    await wipeAuthUsers();
    for (const col of COLLECTIONS_TO_WIPE) {
      await wipeCollection(col);
    }
  }

  // ── Seed phase ────────────────────────────────────────────────────────
  console.log('SEED PHASE — creating test accounts');
  console.log('');

  for (const def of TEST_USERS) {
    console.log(`Seeding ${def.email}  [${def.roles.join(', ')}]`);
    await seedUser(def);
    console.log('');
  }

  // ── Summary ───────────────────────────────────────────────────────────
  console.log('───────────────────────────────────────────────────────');
  console.log(' Seed complete.  Test credentials:');
  console.log('');
  for (const def of TEST_USERS) {
    const rolesStr = def.roles.join(', ').padEnd(22);
    console.log(`  ${def.email.padEnd(34)}  ${def.password.padEnd(16)}  [${rolesStr}]`);
  }
  console.log('');
  console.log(' Next: sign up with perelgut@gmail.com at yosnowmow.com,');
  console.log('       then run:  node promote-to-admin-prod.js');
  console.log('═══════════════════════════════════════════════════════');
}

main()
  .then(() => process.exit(0))
  .catch(err => {
    console.error('');
    console.error('Seed FAILED:', err.message || err);
    process.exit(1);
  });
