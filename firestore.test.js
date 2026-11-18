const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_runner_01";
const BOB_UID = "bob_runner_02";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write leaderboard, profiles, or runs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("leaderboard").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("leaderboard")
      .doc(ALICE_UID)
      .set({
        userId: ALICE_UID,
        displayName: "Rogue",
        bestScore: 500,
        maxDistanceMeters: 120,
        bestCombo: 4,
        gamesPlayed: 1,
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Leaderboard: owner can create and update monotonically, and signed-in players can query globally", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t1 = new Date(Date.now() - 10000);
  const t2 = new Date(Date.now() - 1000);

  // Create own leaderboard entry (Kotlin-mirrored payload, no @DocumentId 'id' field)
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      bestScore: 1250,
      maxDistanceMeters: 310,
      bestCombo: 6,
      gamesPlayed: 1,
      createdAt: t1,
      updatedAt: t1,
    })
  );

  // Partial update with higher score succeeds
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      displayName: "Alice Cyber",
      bestScore: 2400,
      maxDistanceMeters: 520,
      bestCombo: 9,
      gamesPlayed: 2,
      updatedAt: t2,
    })
  );

  // Reject score downgrade
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      bestScore: 100,
      updatedAt: t2,
    })
  );

  // Reject ghost field or @DocumentId 'id' field
  await assertFails(
    bobDb.collection("leaderboard").doc(BOB_UID).set({
      id: BOB_UID,
      userId: BOB_UID,
      displayName: "Bob",
      bestScore: 999999,
      maxDistanceMeters: 1000,
      bestCombo: 5,
      gamesPlayed: 1,
      createdAt: t1,
      updatedAt: t1,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    bobDb.collection("leaderboard").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      bestScore: 500,
      maxDistanceMeters: 100,
      bestCombo: 2,
      gamesPlayed: 1,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Bob cannot overwrite Alice's leaderboard entry
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({
      bestScore: 99999,
      updatedAt: t2,
    })
  );

  // Global leaderboard query succeeds for another signed-in user
  await assertSucceeds(
    bobDb.collection("leaderboard").orderBy("bestScore", "desc").limit(50).get()
  );
});

test("User profile and runs: strict owner-only isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t1 = new Date(Date.now() - 5000);
  const t2 = new Date(Date.now() - 1000);

  // Alice creates her profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      musicEnabled: true,
      sfxEnabled: true,
      totalOrbsCollected: 0,
      totalRuns: 0,
      createdAt: t1,
      updatedAt: t1,
    })
  );

  // Alice updates audio preferences & stats
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      musicEnabled: false,
      totalOrbsCollected: 14,
      totalRuns: 1,
      updatedAt: t2,
    })
  );

  // Bob cannot read or write Alice's private profile (contains email PII)
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      musicEnabled: true,
      updatedAt: t2,
    })
  );

  // Alice logs a run under users/{uid}/runs/{runId}
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("runs").doc("run_1").set({
      userId: ALICE_UID,
      score: 1850,
      distanceMeters: 420,
      orbsCollected: 14,
      shieldsUsed: 1,
      boostsUsed: 2,
      peakIntensity: 3,
      createdAt: t2,
    })
  );

  // Runs are immutable (updates rejected)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("runs").doc("run_1").update({
      score: 999999,
    })
  );

  // Alice can list her own runs (matches Kotlin repository query)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("runs")
      .orderBy("createdAt", "desc")
      .limit(20)
      .get()
  );

  // Bob cannot list or read Alice's runs
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("runs").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("runs").doc("run_1").get()
  );
});
