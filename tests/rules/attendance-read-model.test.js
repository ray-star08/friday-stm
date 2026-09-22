import fs from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDocs, query, where, setDoc, updateDoc } from 'firebase/firestore';

// Characterize the actual query shapes used by the compatibility reader.
// These tests do not loosen or deploy rules, or use a production project.
const projectId = 'demo-friday-stm-test';
const day = '2026-09-18';
let env;
const db = (uid) => env.authenticatedContext(uid).firestore();
const ids = (snapshot) => snapshot.docs.map((d) => d.id).sort();

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId,
    firestore: { host: '127.0.0.1', port: 8085, rules: fs.readFileSync('firestore.rules', 'utf8') },
  });
});
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const seed = ctx.firestore();
    for (const [uid, role, kelas] of [
      ['alice', 'student', 'XI A'], ['bob', 'student', 'XI B'],
      ['teacher', 'instructor', ''], ['admin', 'admin', ''],
    ]) {
      await setDoc(doc(seed, `users/${uid}`), { uid, role, kelas, grade: 11, nama: `Fixture ${uid}` });
    }
    for (const [uid, kelas] of [['alice', 'XI A'], ['bob', 'XI B']]) {
      await setDoc(doc(seed, `attendance/${uid}_${day}`), {
        uid, date: day, grade: 11, status: 'incomplete',
        pembiasaan: { checkedIn: true, valid: true, time: '06:45' },
      });
      await setDoc(doc(seed, `presensi_records/${uid}-legacy`), {
        userId: uid, studentClass: kelas, timestamp: `${day}T06:40:00`,
        studentName: `Fixture ${uid}`, imageUrl: 'https://example.invalid/fixture.jpg',
      });
    }
    await setDoc(doc(seed, 'attendance/alice_2026-09-11'), {
      uid: 'alice', date: '2026-09-11', grade: 11, status: 'incomplete',
    });
  });
});

test('student can read both own source collections with exact ownership filters', async () => {
  const own = db('alice');
  const canonical = await assertSucceeds(getDocs(query(collection(own, 'attendance'), where('uid', '==', 'alice'))));
  const legacy = await assertSucceeds(getDocs(query(collection(own, 'presensi_records'), where('userId', '==', 'alice'))));
  expect(ids(canonical)).toEqual(['alice_2026-09-11', `alice_${day}`]);
  expect(ids(legacy)).toEqual(['alice-legacy']);
});

test.each([['attendance', 'uid'], ['presensi_records', 'userId']])(
  'student cannot read foreign owner through %s query', async (name, ownerField) => {
    await assertFails(getDocs(query(collection(db('alice'), name), where(ownerField, '==', 'bob'))));
  },
);

test('student cannot use staff date-only attendance query', async () => {
  await assertFails(getDocs(query(collection(db('alice'), 'attendance'), where('date', '==', day))));
});

test('student cannot use staff legacy class query', async () => {
  await assertFails(getDocs(query(collection(db('alice'), 'presensi_records'), where('studentClass', '==', 'XI A'))));
});

test.each(['teacher', 'admin'])('staff %s can join bounded canonical query to class roster', async (uid) => {
  const staff = db(uid);
  const roster = await assertSucceeds(getDocs(query(collection(staff, 'users'), where('kelas', '==', 'XI A'))));
  const rosterUids = new Set(roster.docs.map((d) => d.data().uid));
  const canonical = await assertSucceeds(getDocs(query(
    collection(staff, 'attendance'), where('date', '>=', day), where('date', '<=', day),
  )));
  // Current rules allow staff across classes; the client must narrow to roster.
  expect(ids(canonical)).toEqual([`alice_${day}`, `bob_${day}`]);
  expect(canonical.docs.filter((d) => rosterUids.has(d.data().uid)).map((d) => d.id)).toEqual([`alice_${day}`]);
  const legacy = await Promise.all([...rosterUids].map((owner) => assertSucceeds(getDocs(
    query(collection(staff, 'presensi_records'), where('userId', '==', owner)),
  ))));
  expect(legacy.flatMap(ids)).toEqual(['alice-legacy']);
});

test.each([['attendance', 'uid'], ['presensi_records', 'userId']])(
  'anonymous cannot query %s even with owner filter', async (name, ownerField) => {
    const anon = env.unauthenticatedContext().firestore();
    await assertFails(getDocs(query(collection(anon, name), where(ownerField, '==', 'alice'))));
  },
);



test.each(['teacher', 'admin'])('staff %s owner queries follow transfers for legacy and permits and exclude poisoned Larkam', async (uid) => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const seed = ctx.firestore();
    for (const [owner, kelas] of [['alice', 'XI A'], ['bob', 'XI B']]) {
      await setDoc(doc(seed, `izin_records/${owner}-permit`), {
        userId: owner, kelas, startDate: day, endDate: day, status: 'APPROVED', tipe: 'IZIN',
      });
    }
    await setDoc(doc(seed, 'larkam_records/bob-run'), {
      userId: 'bob', timestamp: `${day}T06:40:00`, imageUrl: 'https://example.invalid/run.jpg', distanceKm: 1,
    });
  });
  // This malformed owner write is legal under unchanged rules; query isolation must protect other classes.
  await assertSucceeds(setDoc(doc(db('alice'), 'larkam_records/alice-poison'), {
    userId: 'alice', timestamp: { invalid: true }, imageUrl: { invalid: true },
  }));
  await assertSucceeds(updateDoc(doc(db('admin'), 'users/alice'), { kelas: 'XI B' }));
  await assertSucceeds(updateDoc(doc(db('admin'), 'users/bob'), { kelas: 'XI A' }));
  const staff = db(uid);
  const roster = await assertSucceeds(getDocs(query(collection(staff, 'users'), where('kelas', '==', 'XI A'))));
  const owners = roster.docs.map((d) => d.data().uid);
  expect(owners).toEqual(['bob']);
  for (const [name, expected] of [
    ['presensi_records', 'bob-legacy'], ['izin_records', 'bob-permit'], ['larkam_records', 'bob-run'],
  ]) {
    const snapshots = await Promise.all(owners.map((owner) => assertSucceeds(getDocs(
      query(collection(staff, name), where('userId', '==', owner)),
    ))));
    expect(snapshots.flatMap(ids)).toEqual([expected]);
    const records = snapshots.flatMap((snapshot) => snapshot.docs.map((d) => d.data()));
    expect(records.map((record) => record.userId)).toEqual(['bob']);
    if (name === 'presensi_records') expect(records[0].studentClass).toBe('XI B');
    if (name === 'izin_records') expect(records[0].kelas).toBe('XI B');
    if (name === 'larkam_records') expect(typeof records[0].timestamp).toBe('string');
  }
});

test.each(['izin_records', 'larkam_records'])('owner filter does not grant student or anonymous foreign reads of %s', async (name) => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `${name}/bob-record`), { userId: 'bob' });
  });
  for (const reader of [db('alice'), env.unauthenticatedContext().firestore()]) {
    await assertFails(getDocs(query(collection(reader, name), where('userId', '==', 'bob'))));
  }
});

test('owner with no records receives genuine empty results from both collections', async () => {
  const empty = db('no-records');
  for (const [name, field] of [['attendance', 'uid'], ['presensi_records', 'userId']]) {
    const snapshot = await assertSucceeds(getDocs(query(collection(empty, name), where(field, '==', 'no-records'))));
    expect(snapshot.empty).toBe(true);
  }
});
