const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSchematicPayload(uid, overrides = {}) {
  const ts = new Date(Date.now() - 5000);
  return {
    userId: uid,
    name: "ESP32 Sensor Node Rev A",
    description: "Compact 2-layer telemetry controller board",
    boardWidthMm: 80.5,
    boardHeightMm: 60.5,
    layerCount: 2,
    gridSnapMm: 2.54,
    components: [
      {
        uid: "comp_1",
        refDes: "R1",
        type: "RESISTOR",
        label: "Pull-Up Resistor",
        valueText: "10kΩ",
        footprint: "0603",
        manufacturerPart: "RC0603FR-0710KL",
        unitPriceUsd: 0.02,
        gridX: 4,
        gridY: 3,
        rotationDeg: 0,
        voltageRatingV: 50.0,
        powerRatingMw: 100.0,
      },
    ],
    wires: [
      {
        uid: "wire_1",
        netName: "VCC_3V3",
        fromCompUid: "comp_1",
        fromPinIndex: 0,
        toCompUid: "comp_2",
        toPinIndex: 1,
        traceWidthMm: 0.3,
        copperLayer: "TOP",
      },
    ],
    componentCount: 1,
    wireCount: 1,
    validationErrorCount: 0,
    validationSummary: "ERC passed: 0 errors",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

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

test("Unauthenticated user: cannot read, list, or write schematics", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("schematics").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schematics")
      .doc("proj_1")
      .set(validSchematicPayload(ALICE_UID))
  );
});

test("Authenticated owner: Kotlin-mirrored create, query, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("schematics")
    .doc("proj_alpha");

  await assertSucceeds(docRef.set(validSchematicPayload(ALICE_UID)));

  // Mirror Kotlin query: orderBy("updatedAt", DESCENDING)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schematics")
      .orderBy("updatedAt", "desc")
      .get()
  );

  // Mirror Kotlin partial update preserving createdAt and userId
  await assertSucceeds(
    docRef.update({
      name: "ESP32 Sensor Node Rev B",
      validationErrorCount: 1,
      validationSummary: "1 warning: floating input pin",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("Security bounds: reject cross-user access, ghost fields, immutable createdAt mutation, and invalid IDs", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("schematics")
      .doc("bob_board")
      .set(validSchematicPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Cross-user read, list, update, delete must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("schematics")
      .doc("bob_board")
      .get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("schematics").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("schematics")
      .doc("bob_board")
      .update({ name: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );

  // Ghost @DocumentId `id` field must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schematics")
      .doc("proj_ghost")
      .set(validSchematicPayload(ALICE_UID, { id: "proj_ghost" }))
  );

  // Spoofed userId must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schematics")
      .doc("proj_spoof")
      .set(validSchematicPayload(BOB_UID))
  );

  // Future timestamp must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schematics")
      .doc("proj_future")
      .set(
        validSchematicPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 60000),
        })
      )
  );

  // Invalid document ID must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schematics")
      .doc("bad id with spaces!")
      .set(validSchematicPayload(ALICE_UID))
  );

  // Mutating immutable createdAt on update must fail
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schematics")
      .doc("proj_mut")
      .set(validSchematicPayload(ALICE_UID))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schematics")
      .doc("proj_mut")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );
});
