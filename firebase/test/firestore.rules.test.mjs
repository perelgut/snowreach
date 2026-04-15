/**
 * YoSnowMow — Firestore Security Rules Tests (P1-27)
 *
 * Tests every allow/deny path in firestore.rules against the local Firestore
 * emulator using @firebase/rules-unit-testing v4.
 *
 * Prerequisites:
 *   firebase emulators:start --only firestore
 *   npm run test:rules          (from firebase/ directory)
 *
 * Architecture reminder:
 *   ALL operational writes flow through the Spring Boot backend via Admin SDK
 *   and bypass these rules.  Rules govern only:
 *     - Client-side reads (React onSnapshot / getDoc)
 *     - One narrow client write: marking a notification as read
 *
 * Test structure mirrors firestore.rules top-to-bottom:
 *   1. /users/{uid}
 *   2. /jobs/{jobId}
 *   3. /jobRequests/{requestId}
 *   4. /ratings/{ratingId}
 *   5. /notifications/{uid}/feed/{notifId}
 *   6. /disputes/{disputeId}
 *   7. Backend-only collections (geocache, stripeEvents)
 *   8. Catch-all (unknown collection)
 */

import { initializeTestEnvironment, assertFails, assertSucceeds }
  from '@firebase/rules-unit-testing';
import { setLogLevel } from 'firebase/app';
import { readFileSync }    from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath }   from 'node:url';
import { doc, getDoc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── Test environment lifecycle ────────────────────────────────────────────────

// Suppress the Firebase SDK's verbose gRPC PERMISSION_DENIED warnings that appear
// for every assertFails test.  The rules are working correctly; the warnings are noise.
setLogLevel('error');

/** Shared environment — initialised once, cleaned up after every test. */
let testEnv;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-yosnowmow',
    firestore: {
      rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8'),
      host: 'localhost',
      port: 8080,
    },
  });
}, 30_000);

/** Clear all emulator data between tests for strict isolation. */
afterEach(async () => {
  await testEnv.clearFirestore();
});

afterAll(async () => {
  await testEnv.cleanup();
});

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Seed a document at collection/id bypassing security rules (Admin SDK equivalent).
 * Used to set up pre-existing state that the test will then try to read or modify.
 */
async function seedDoc(collPath, id, data) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), collPath, id), data);
  });
}

/**
 * Seed a subcollection document bypassing security rules.
 * pathSegments: alternating collection/doc strings, e.g. ['notifications','uid','feed','notifId']
 */
async function seedSubDoc(pathSegments, data) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), ...pathSegments), data);
  });
}

/**
 * Returns a Firestore instance scoped to an authenticated user with the given roles.
 * The roles array maps to the Firebase ID token's "roles" custom claim.
 */
function asUser(uid, roles = []) {
  return testEnv.authenticatedContext(uid, { roles }).firestore();
}

/** Firestore instance authenticated as an admin. */
function asAdmin() {
  return testEnv.authenticatedContext('admin-uid-1', { roles: ['admin'] }).firestore();
}

/** Firestore instance with no authentication (unauthenticated client). */
function unauthed() {
  return testEnv.unauthenticatedContext().firestore();
}

// ── 1. /users/{uid} ───────────────────────────────────────────────────────────

describe('/users/{uid}', () => {
  beforeEach(async () => {
    await seedDoc('users', 'user-1', { uid: 'user-1', name: 'Alice', roles: ['requester'] });
  });

  test('owner can read own document', async () => {
    await assertSucceeds(getDoc(doc(asUser('user-1', ['requester']), 'users', 'user-1')));
  });

  test('admin can read any user document', async () => {
    await assertSucceeds(getDoc(doc(asAdmin(), 'users', 'user-1')));
  });

  test('different authenticated user cannot read', async () => {
    await assertFails(getDoc(doc(asUser('user-2', ['requester']), 'users', 'user-1')));
  });

  test('unauthenticated client cannot read', async () => {
    await assertFails(getDoc(doc(unauthed(), 'users', 'user-1')));
  });

  test('owner cannot write (writes always via Admin SDK)', async () => {
    await assertFails(
      setDoc(doc(asUser('user-1', ['requester']), 'users', 'user-1'), { name: 'Hacked' })
    );
  });

  test('admin cannot write (writes always via Admin SDK)', async () => {
    await assertFails(
      setDoc(doc(asAdmin(), 'users', 'user-1'), { name: 'Admin override' })
    );
  });
});

// ── 2. /jobs/{jobId} ─────────────────────────────────────────────────────────

describe('/jobs/{jobId}', () => {
  beforeEach(async () => {
    await seedDoc('jobs', 'job-1', {
      jobId:       'job-1',
      requesterId: 'req-uid-1',
      workerId:    'wkr-uid-1',
      status:      'IN_PROGRESS',
    });
  });

  test('requester can read their own job', async () => {
    await assertSucceeds(getDoc(doc(asUser('req-uid-1', ['requester']), 'jobs', 'job-1')));
  });

  test('assigned worker can read the job', async () => {
    await assertSucceeds(getDoc(doc(asUser('wkr-uid-1', ['worker']), 'jobs', 'job-1')));
  });

  test('admin can read any job', async () => {
    await assertSucceeds(getDoc(doc(asAdmin(), 'jobs', 'job-1')));
  });

  test('unrelated authenticated user cannot read', async () => {
    await assertFails(getDoc(doc(asUser('stranger-uid', ['requester']), 'jobs', 'job-1')));
  });

  test('unauthenticated client cannot read', async () => {
    await assertFails(getDoc(doc(unauthed(), 'jobs', 'job-1')));
  });

  test('nobody can write (writes always via Admin SDK)', async () => {
    await assertFails(
      setDoc(doc(asUser('req-uid-1', ['requester']), 'jobs', 'job-1'), { status: 'CANCELLED' })
    );
  });
});

// ── 3. /jobRequests/{requestId} ───────────────────────────────────────────────

describe('/jobRequests/{requestId}', () => {
  beforeEach(async () => {
    await seedDoc('jobRequests', 'job-1_wkr-uid-1', {
      jobId:    'job-1',
      workerId: 'wkr-uid-1',
      status:   'PENDING',
    });
  });

  test('target worker can read their offer', async () => {
    await assertSucceeds(
      getDoc(doc(asUser('wkr-uid-1', ['worker']), 'jobRequests', 'job-1_wkr-uid-1'))
    );
  });

  test('admin can read any job request', async () => {
    await assertSucceeds(
      getDoc(doc(asAdmin(), 'jobRequests', 'job-1_wkr-uid-1'))
    );
  });

  test('different worker cannot read the offer', async () => {
    await assertFails(
      getDoc(doc(asUser('wkr-uid-2', ['worker']), 'jobRequests', 'job-1_wkr-uid-1'))
    );
  });

  test('nobody can write (writes always via Admin SDK)', async () => {
    await assertFails(
      setDoc(
        doc(asUser('wkr-uid-1', ['worker']), 'jobRequests', 'job-1_wkr-uid-1'),
        { status: 'ACCEPTED' }
      )
    );
  });
});

// ── 4. /ratings/{ratingId} ────────────────────────────────────────────────────

describe('/ratings/{ratingId}', () => {
  beforeEach(async () => {
    await seedDoc('ratings', 'job-1_REQUESTER', {
      raterUid: 'req-uid-1',
      rateeUid: 'wkr-uid-1',
      stars:    5,
      jobId:    'job-1',
    });
  });

  test('rater can read their own submitted rating', async () => {
    await assertSucceeds(
      getDoc(doc(asUser('req-uid-1', ['requester']), 'ratings', 'job-1_REQUESTER'))
    );
  });

  test('ratee can read their incoming rating', async () => {
    await assertSucceeds(
      getDoc(doc(asUser('wkr-uid-1', ['worker']), 'ratings', 'job-1_REQUESTER'))
    );
  });

  test('admin can read any rating', async () => {
    await assertSucceeds(getDoc(doc(asAdmin(), 'ratings', 'job-1_REQUESTER')));
  });

  test('unrelated user cannot read rating', async () => {
    await assertFails(
      getDoc(doc(asUser('stranger-uid', ['requester']), 'ratings', 'job-1_REQUESTER'))
    );
  });

  test('nobody can write ratings (writes always via Admin SDK)', async () => {
    await assertFails(
      setDoc(
        doc(asUser('req-uid-1', ['requester']), 'ratings', 'job-1_REQUESTER'),
        { stars: 1 }
      )
    );
  });
});

// ── 5. /notifications/{uid}/feed/{notifId} ────────────────────────────────────

describe('/notifications/{uid}/feed/{notifId}', () => {
  beforeEach(async () => {
    await seedSubDoc(
      ['notifications', 'user-1', 'feed', 'notif-1'],
      { message: 'Your job is confirmed.', isRead: false }
    );
  });

  test('owner can read their own notification', async () => {
    await assertSucceeds(
      getDoc(doc(asUser('user-1', ['requester']), 'notifications', 'user-1', 'feed', 'notif-1'))
    );
  });

  test('owner can mark notification as read (isRead is the only affected key)', async () => {
    await assertSucceeds(
      updateDoc(
        doc(asUser('user-1', ['requester']), 'notifications', 'user-1', 'feed', 'notif-1'),
        { isRead: true }
      )
    );
  });

  test('owner cannot update non-isRead fields alongside isRead', async () => {
    // Both isRead AND message change — affectedKeys contains more than just isRead → denied
    await assertFails(
      updateDoc(
        doc(asUser('user-1', ['requester']), 'notifications', 'user-1', 'feed', 'notif-1'),
        { isRead: true, message: 'Tampered content' }
      )
    );
  });

  test('owner cannot update only non-isRead fields', async () => {
    await assertFails(
      updateDoc(
        doc(asUser('user-1', ['requester']), 'notifications', 'user-1', 'feed', 'notif-1'),
        { message: 'Tampered' }
      )
    );
  });

  test('owner cannot create notifications (Admin SDK only)', async () => {
    await assertFails(
      setDoc(
        doc(asUser('user-1', ['requester']), 'notifications', 'user-1', 'feed', 'notif-new'),
        { message: 'Forged notification', isRead: false }
      )
    );
  });

  test('owner cannot delete notifications (Admin SDK only)', async () => {
    await assertFails(
      deleteDoc(
        doc(asUser('user-1', ['requester']), 'notifications', 'user-1', 'feed', 'notif-1')
      )
    );
  });

  test('other user cannot read the feed', async () => {
    await assertFails(
      getDoc(
        doc(asUser('user-2', ['requester']), 'notifications', 'user-1', 'feed', 'notif-1')
      )
    );
  });
});

// ── 6. /disputes/{disputeId} ─────────────────────────────────────────────────

describe('/disputes/{disputeId}', () => {
  beforeEach(async () => {
    await seedDoc('disputes', 'dispute-1', {
      disputeId:   'dispute-1',
      jobId:       'job-1',
      requesterId: 'req-uid-1',
      workerId:    'wkr-uid-1',
      status:      'OPEN',
    });
  });

  test("job's requester can read the dispute", async () => {
    await assertSucceeds(
      getDoc(doc(asUser('req-uid-1', ['requester']), 'disputes', 'dispute-1'))
    );
  });

  test("job's worker can read the dispute", async () => {
    await assertSucceeds(
      getDoc(doc(asUser('wkr-uid-1', ['worker']), 'disputes', 'dispute-1'))
    );
  });

  test('admin can read any dispute', async () => {
    await assertSucceeds(getDoc(doc(asAdmin(), 'disputes', 'dispute-1')));
  });

  test('unrelated user cannot read dispute', async () => {
    await assertFails(
      getDoc(doc(asUser('stranger-uid', ['requester']), 'disputes', 'dispute-1'))
    );
  });

  test('nobody can write disputes (writes always via Admin SDK)', async () => {
    await assertFails(
      setDoc(
        doc(asUser('req-uid-1', ['requester']), 'disputes', 'dispute-1'),
        { status: 'RESOLVED' }
      )
    );
  });
});

// ── 7. Backend-only collections: geocache, stripeEvents ───────────────────────

describe('backend-only collections (geocache, stripeEvents)', () => {
  beforeEach(async () => {
    await seedDoc('geocache',     'sha256abc', { lat: 43.6532, lng: -79.3832, method: 'google' });
    await seedDoc('stripeEvents', 'evt_test1', { processed: true });
  });

  test('admin cannot read geocache (backend-only)', async () => {
    await assertFails(getDoc(doc(asAdmin(), 'geocache', 'sha256abc')));
  });

  test('authenticated user cannot read geocache', async () => {
    await assertFails(getDoc(doc(asUser('user-1', ['requester']), 'geocache', 'sha256abc')));
  });

  test('nobody can write geocache', async () => {
    await assertFails(
      setDoc(doc(asUser('user-1', ['requester']), 'geocache', 'new-hash'), { lat: 0 })
    );
  });

  test('admin cannot read stripeEvents (backend-only)', async () => {
    await assertFails(getDoc(doc(asAdmin(), 'stripeEvents', 'evt_test1')));
  });

  test('nobody can write stripeEvents', async () => {
    await assertFails(
      setDoc(doc(asAdmin(), 'stripeEvents', 'evt_test2'), { processed: false })
    );
  });
});

// ── 8. Catch-all: unknown collections denied ──────────────────────────────────

describe('catch-all (any collection not in the rules is denied)', () => {
  test('admin cannot read from an unknown collection', async () => {
    await assertFails(getDoc(doc(asAdmin(), 'unknownCollection', 'doc-1')));
  });

  test('authenticated user cannot read from an unknown collection', async () => {
    await assertFails(
      getDoc(doc(asUser('user-1', ['requester']), 'unknownCollection', 'doc-1'))
    );
  });

  test('admin cannot write to an unknown collection', async () => {
    await assertFails(
      setDoc(doc(asAdmin(), 'unknownCollection', 'doc-1'), { data: 'x' })
    );
  });
});
