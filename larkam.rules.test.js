import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc, updateDoc, deleteDoc } from "firebase/firestore";
import fs from "fs";

const PROJECT_ID = "demo-friday-stm-test";
const RULES_PATH = "./firestore.rules";

describe("firestore.rules: larkam_runs append-only & isolated per user", () => {
  let testEnv;

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: { rules: fs.readFileSync(RULES_PATH, "utf8"), host: "127.0.0.1", port: 8085 },
    });
  });

  afterAll(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  test("owner can create own run", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(alice, "larkam_runs/run1"), { userId: "alice", distance: 1200 }));
  });

  test("user cannot create run for another uid (forged userId)", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(alice, "larkam_runs/run2"), { userId: "bob", distance: 500 }));
  });

  test("unauthenticated cannot create", async () => {
    const unauth = testEnv.unauthenticatedContext().firestore();
    await assertFails(setDoc(doc(unauth, "larkam_runs/run3"), { userId: "any", distance: 100 }));
  });

  test("owner can read own run, other user cannot", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    const bob = testEnv.authenticatedContext("bob").firestore();
    // Alice creates
    await assertSucceeds(setDoc(doc(alice, "larkam_runs/run1"), { userId: "alice", distance: 1200 }));
    // Alice can read
    await assertSucceeds(getDoc(doc(alice, "larkam_runs/run1")));
    // Bob cannot read Alice's run
    await assertFails(getDoc(doc(bob, "larkam_runs/run1")));
  });

  test("unauthenticated cannot read", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(alice, "larkam_runs/run1"), { userId: "alice", distance: 1200 }));
    const unauth = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(unauth, "larkam_runs/run1")));
  });

  test("update is always denied (append-only)", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(alice, "larkam_runs/run1"), { userId: "alice", distance: 1200 }));
    await assertFails(updateDoc(doc(alice, "larkam_runs/run1"), { distance: 9999 }));
  });

  test("delete is always denied", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(alice, "larkam_runs/run1"), { userId: "alice", distance: 1200 }));
    await assertFails(deleteDoc(doc(alice, "larkam_runs/run1")));
  });

  test("even other user cannot update/delete", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    const bob = testEnv.authenticatedContext("bob").firestore();
    await assertSucceeds(setDoc(doc(alice, "larkam_runs/run1"), { userId: "alice", distance: 1200 }));
    await assertFails(updateDoc(doc(bob, "larkam_runs/run1"), { distance: 1 }));
    await assertFails(deleteDoc(doc(bob, "larkam_runs/run1")));
  });
});
