import fs from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, query, where, setDoc, updateDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';

const projectId = 'demo-friday-stm-test';
const captureId = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';
const recordId = `capture_${captureId}`;
let env;
const db = (uid) => env.authenticatedContext(uid).firestore();
const profile = (uid, role = 'student') => ({ uid, role, kelas: 'XI A', grade: 11, nama: `Fixture ${uid}` });
const payload = (userId, larkam = false) => ({
  schemaVersion: 2, captureId, userId, studentName: `Fixture ${userId}`, studentClass: 'XI A',
  timestamp: '2026-09-18T07:50:00', imageUrl: 'https://res.cloudinary.com/fixture/image/upload/a.jpg',
  lat: -6.9, lng: 107.5, createdAt: serverTimestamp(),
  ...(larkam ? { distanceKm: 1.25, durationSeconds: 600, durationFormatted: '00:10:00', route: [{ lat: -6.9, lng: 107.5 }], routeUrl: 'https://res.cloudinary.com/fixture/image/upload/a.jpg' } : {}),
});

beforeAll(async () => {
  env = await initializeTestEnvironment({ projectId, firestore: { host: '127.0.0.1', port: 8085, rules: fs.readFileSync('firestore.rules', 'utf8') } });
});
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    for (const [uid, role] of [['alice', 'student'], ['bob', 'student'], ['rep', 'class_rep'], ['teacher', 'instructor']]) {
      await setDoc(doc(ctx.firestore(), `users/${uid}`), profile(uid, role));
    }
  });
});

for (const name of ['presensi_records', 'larkam_records']) {
  describe(`${name} durable capture`, () => {
    test('owner can check a missing deterministic id then create and read exact evidence', async () => {
      const ref = doc(db('alice'), name, recordId);
      const missing = await assertSucceeds(getDoc(ref));
      expect(missing.exists()).toBe(false);
      await assertSucceeds(setDoc(ref, payload('alice', name === 'larkam_records')));
      const actual = (await assertSucceeds(getDoc(ref))).data();
      expect(actual.captureId).toBe(captureId);
      expect(actual.userId).toBe('alice');
      expect(actual.createdAt.toMillis()).toBeGreaterThan(0);
      if (name === 'larkam_records') expect(actual.route).toEqual([{ lat: -6.9, lng: 107.5 }]);
    });
    test('missing check never exposes an existing foreign document or unfiltered list', async () => {
      await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(), name, recordId), payload('bob', name === 'larkam_records')));
      await assertFails(getDoc(doc(db('alice'), name, recordId)));
      await assertFails(getDocs(collection(db('alice'), name)));
      await assertSucceeds(getDocs(query(collection(db('alice'), name), where('userId', '==', 'alice'))));
    });
    test('unauthenticated and unprovisioned identities cannot probe missing captures', async () => {
      await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), name, recordId)));
      await assertFails(getDoc(doc(db('missing-profile'), name, recordId)));
    });
    test.each([
      ['capture id', { captureId: 'other' }],
      ['owner', { userId: 'bob' }],
      ['name', { studentName: 'forged' }],
      ['receipt time', { createdAt: 'yesterday' }],
      ['image type', { imageUrl: 42 }],
      ['image transport', { imageUrl: 'http://example.invalid/a.jpg' }],
      ['time type', { timestamp: 42 }],
      ['latitude', { lat: 91 }],
      ['partial coordinates', { lng: null }],
      ['unexpected trust claim', { verified: true }],
    ])('versioned payload rejects invalid %s', async (_, changed) => {
      await assertFails(setDoc(doc(db('alice'), name, recordId), { ...payload('alice', name === 'larkam_records'), ...changed }));
    });
    test('reserved capture ids cannot bypass v2 validation by omitting schema markers', async () => {
      const oldShape = payload('alice', name === 'larkam_records');
      delete oldShape.schemaVersion;
      delete oldShape.captureId;
      await assertFails(setDoc(doc(db('alice'), name, recordId), oldShape));
    });
    test('versioned payload cannot pick a different document id', async () => {
      await assertFails(setDoc(doc(db('alice'), name, 'arbitrary'), payload('alice', name === 'larkam_records')));
    });
    if (name === 'larkam_records') {
      test.each([
        ['negative distance', { distanceKm: -1 }],
        ['duration type', { durationSeconds: '600' }],
        ['negative duration', { durationSeconds: -1 }],
        ['route type', { route: 'missing' }],
      ])('Larkam preserves typed metadata and rejects %s', async (_, changed) => {
        await assertFails(setDoc(doc(db('alice'), name, recordId), { ...payload('alice', true), ...changed }));
      });
    }
    test('deterministic writes stay append-only even for an identical replay', async () => {
      const ref = doc(db('alice'), name, recordId);
      const data = payload('alice', name === 'larkam_records');
      await assertSucceeds(setDoc(ref, data));
      await assertFails(setDoc(ref, data));
      await assertFails(updateDoc(ref, { imageUrl: 'https://example.invalid/changed.jpg' }));
      await assertFails(deleteDoc(ref));
    });
  });
}
