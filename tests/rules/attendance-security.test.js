import fs from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, query, where, setDoc, updateDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';

const projectId = 'demo-friday-stm-test';
let env;
const profile = (uid, role = 'student', kelas = 'XI A') => ({ uid, role, kelas, grade: 11, nis: `fixture-${uid}`, nama: `Fixture ${uid}`, photoUrl: '' });
const presensi = (userId) => ({ userId, studentName: `Fixture ${userId}`, studentClass: 'XI A', timestamp: '2026-09-18T06:45:00', imageUrl: 'https://example.invalid/selfie.jpg', lat: -6.9028, lng: 107.5386, createdAt: serverTimestamp() });
const db = (uid) => env.authenticatedContext(uid).firestore();

beforeAll(async () => {
  env = await initializeTestEnvironment({ projectId, firestore: { host: '127.0.0.1', port: 8085, rules: fs.readFileSync('firestore.rules', 'utf8') } });
});
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const seed = ctx.firestore();
    for (const [uid, role] of [['alice', 'student'], ['bob', 'student'], ['rep', 'class_rep'], ['teacher', 'instructor'], ['admin', 'admin']]) {
      await setDoc(doc(seed, `users/${uid}`), profile(uid, role));
    }
    for (const name of ['presensi', 'presensi_records']) {
      await setDoc(doc(seed, `${name}/bob-record`), presensi('bob'));
    }
  });
});

describe('profile identity is managed by school admins', () => {
  test.each(['uid', 'nis', 'nama', 'grade', 'kelas', 'role'])('owner cannot change %s', async (field) => {
    const value = field === 'grade' ? 12 : 'forged';
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { [field]: value }));
  });
  test('class representative cannot self-transfer class', async () => {
    await assertFails(updateDoc(doc(db('rep'), 'users/rep'), { kelas: 'XI B' }));
  });
  test('owner can update only photo and FCM token', async () => {
    await assertSucceeds(updateDoc(doc(db('alice'), 'users/alice'), { photoUrl: 'https://example.invalid/avatar.jpg', fcmToken: 'fixture-token' }));
  });
  test('unprovisioned account cannot create its own school identity', async () => {
    await assertFails(setDoc(doc(db('new'), 'users/new'), profile('new')));
  });
  test('admin can provision and update a student', async () => {
    await assertSucceeds(setDoc(doc(db('admin'), 'users/new'), profile('new')));
    await assertSucceeds(updateDoc(doc(db('admin'), 'users/alice'), { kelas: 'XII B', grade: 12 }));
  });
});

for (const name of ['presensi', 'presensi_records']) {
  describe(`${name} ownership`, () => {
    test('owner can create and read own record', async () => {
      await assertSucceeds(setDoc(doc(db('alice'), `${name}/alice-record`), presensi('alice')));
      await assertSucceeds(getDoc(doc(db('alice'), `${name}/alice-record`)));
    });
    test('unprovisioned identity cannot submit a record', async () => {
      await assertFails(setDoc(doc(db('new'), `${name}/unprovisioned`), presensi('new')));
    });
    test.each(['studentName', 'studentClass'])('student cannot forge %s', async (field) => {
      await assertFails(setDoc(doc(db('alice'), `${name}/forged-identity`), { ...presensi('alice'), [field]: 'forged' }));
    });
    test('createdAt must be a server timestamp', async () => {
      await assertFails(setDoc(doc(db('alice'), `${name}/forged-time`), { ...presensi('alice'), createdAt: 'yesterday' }));
    });
    test('owner cannot edit or delete submitted evidence', async () => {
      await assertFails(updateDoc(doc(db('bob'), `${name}/bob-record`), { imageUrl: 'changed' }));
      await assertFails(deleteDoc(doc(db('bob'), `${name}/bob-record`)));
    });
    test('student cannot forge another student owner', async () => {
      await assertFails(setDoc(doc(db('alice'), `${name}/forged`), presensi('bob')));
    });
    test('student cannot read another student record', async () => {
      await assertFails(getDoc(doc(db('alice'), `${name}/bob-record`)));
    });
    test('unfiltered student list is denied while owner history works', async () => {
      await assertFails(getDocs(collection(db('alice'), name)));
      await assertSucceeds(getDocs(query(collection(db('bob'), name), where('userId', '==', 'bob'))));
    });
    test('staff can query class records', async () => {
      await assertSucceeds(getDocs(query(collection(db('teacher'), name), where('studentClass', '==', 'XI A'))));
    });
    test('unauthenticated caller cannot create or read', async () => {
      const anon = env.unauthenticatedContext().firestore();
      await assertFails(setDoc(doc(anon, `${name}/anon`), presensi('alice')));
      await assertFails(getDoc(doc(anon, `${name}/bob-record`)));
    });
  });
}
