import fs from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp } from 'firebase/firestore';

let env;
const db = (uid = 'alice') => env.authenticatedContext(uid).firestore();
const ref = () => doc(db(), 'attendance/alice_2026-09-18');
const checkin = () => ({ uid: 'alice', date: '2026-09-18', grade: 11, pembiasaan: { activity: 'talim', checkedIn: true, time: '06:45', serverTime: serverTimestamp(), lat: -6.9028, lng: 107.5386, selfieUrl: 'https://example.invalid/selfie.jpg', valid: true }, updatedAt: serverTimestamp() });
const checkout = () => ({ uid: 'alice', date: '2026-09-18', checkout: { checkedOut: true, time: '08:05', serverTime: serverTimestamp() }, status: 'complete', updatedAt: serverTimestamp() });
beforeAll(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-friday-stm-test', firestore: { host: '127.0.0.1', port: 8085, rules: fs.readFileSync('firestore.rules', 'utf8') } });
});
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    for (const uid of ['alice', 'bob']) {
      await setDoc(doc(ctx.firestore(), `users/${uid}`), { uid, nama: `Fixture ${uid}`, kelas: 'XI A', grade: 11, role: 'student' });
    }
  });
});

test('owner can read missing daily record before checkin', async () => {
  await assertSucceeds(getDoc(ref()));
});
test('normal checkin then checkout preserves checkin and marks complete', async () => {
  await assertSucceeds(setDoc(ref(), checkin(), { merge: true }));
  const before = (await getDoc(ref())).data();
  await assertSucceeds(setDoc(ref(), checkout(), { merge: true }));
  const after = (await getDoc(ref())).data();
  expect(after.status).toBe('complete');
  expect(after.pembiasaan).toEqual(before.pembiasaan);
  expect(after.checkout.checkedOut).toBe(true);
});
test('checkout cannot create a record with no checkin', async () => {
  await assertFails(setDoc(ref(), checkout(), { merge: true }));
});
test('checkin and complete cannot be forged in one create', async () => {
  await assertFails(setDoc(ref(), { ...checkin(), ...checkout() }));
});
test('checkout cannot complete an unchecked legacy record', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'attendance/alice_2026-09-18'), { ...checkin(), pembiasaan: { checkedIn: false } });
  });
  await assertFails(setDoc(ref(), checkout(), { merge: true }));
});
test('identity date and grade cannot be rewritten after checkin', async () => {
  await setDoc(ref(), checkin());
  for (const [field, value] of [['uid', 'bob'], ['date', '2026-09-25'], ['grade', 12]]) {
    await assertFails(updateDoc(ref(), { [field]: value, updatedAt: serverTimestamp() }));
  }
});
test('nested checkin time must be server authored', async () => {
  const payload = checkin();
  payload.pembiasaan.serverTime = Timestamp.fromMillis(0);
  await assertFails(setDoc(ref(), payload));
});
test('nested checkout time must be server authored', async () => {
  await setDoc(ref(), checkin());
  const payload = checkout();
  payload.checkout.serverTime = Timestamp.fromMillis(0);
  await assertFails(setDoc(ref(), payload, { merge: true }));
});
test('terminal day cannot be checked in or out again', async () => {
  await setDoc(ref(), checkin());
  await setDoc(ref(), checkout(), { merge: true });
  await assertFails(setDoc(ref(), checkin(), { merge: true }));
  await assertFails(setDoc(ref(), checkout(), { merge: true }));
});
test('legacy apel-only record can advance without losing apel', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'attendance/alice_2026-09-18'), { uid: 'alice', date: '2026-09-18', grade: 11, apel: { checkedIn: true }, pembiasaan: null, checkout: null, status: 'incomplete' });
  });
  await assertSucceeds(setDoc(ref(), checkin(), { merge: true }));
  await assertSucceeds(setDoc(ref(), checkout(), { merge: true }));
  expect((await getDoc(ref())).data().apel).toEqual({ checkedIn: true });
});
test('second checkin cannot replace existing phase evidence', async () => {
  await setDoc(ref(), checkin());
  await assertFails(setDoc(ref(), checkin(), { merge: true }));
});
test('create rejects unknown fields and profile grade mismatch', async () => {
  await assertFails(setDoc(ref(), { ...checkin(), grade: 12 }));
  await assertFails(setDoc(ref(), { ...checkin(), unexpectedPrivilege: true }));
});
test('document receipt time cannot be client authored', async () => {
  await assertFails(setDoc(ref(), { ...checkin(), updatedAt: Timestamp.fromMillis(0) }));
});
test('another account cannot create a daily record for owner', async () => {
  await assertFails(setDoc(doc(db('bob'), 'attendance/alice_2026-09-18'), checkin()));
});
test('other provisioned student cannot update owner checkin', async () => {
  await setDoc(ref(), checkin());
  await assertFails(setDoc(doc(db('bob'), 'attendance/alice_2026-09-18'), checkout(), { merge: true }));
});
test('class representative can check in and check out', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await updateDoc(doc(ctx.firestore(), 'users/alice'), { role: 'class_rep' });
  });
  await assertSucceeds(setDoc(ref(), checkin()));
  await assertSucceeds(setDoc(ref(), checkout(), { merge: true }));
});
test('flagged day cannot be completed by student', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'attendance/alice_2026-09-18'), { ...checkin(), status: 'flagged' });
  });
  await assertFails(setDoc(ref(), checkout(), { merge: true }));
});
test('owner cannot delete evidence', async () => {
  await setDoc(ref(), checkin());
  await assertFails(deleteDoc(ref()));
});
