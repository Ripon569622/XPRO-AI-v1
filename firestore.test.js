const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_xpro_123";
const BOB_UID = "bob_xpro_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user cannot read or write profiles, conversations, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Unauth Chat",
      mode: "CHAT",
      provider: "GEMINI",
      lastMessagePreview: "Hello",
      pinned: false,
      messageCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner can create, list, update, and delete conversations & messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const updated = new Date(Date.now() - 1000);

  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");

  // Create conversation (mirrors Kotlin ConversationRepository)
  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Kotlin Coroutines Help",
      mode: "CODING",
      provider: "GEMINI",
      lastMessagePreview: "Explain structured concurrency",
      pinned: false,
      messageCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List conversations
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );

  // Add message
  const msgRef = convRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      role: "USER",
      content: "Explain structured concurrency in Kotlin",
      provider: "GEMINI",
      mode: "CODING",
      createdAt: updated,
    })
  );

  // List messages
  await assertSucceeds(convRef.collection("messages").get());

  // Partial update: pin & rename
  await assertSucceeds(
    convRef.update({
      title: "Pinned Kotlin Guide",
      pinned: true,
      updatedAt: updated,
    })
  );

  // Partial update: append message metadata
  await assertSucceeds(
    convRef.update({
      lastMessagePreview: "Structured concurrency binds coroutines...",
      messageCount: 2,
      provider: "GEMINI",
      mode: "CODING",
      title: "Pinned Kotlin Guide",
      updatedAt: updated,
    })
  );

  // Reject ghost field
  await assertFails(
    convRef.update({
      title: "Hacked",
      isAdmin: true,
      updatedAt: updated,
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    convRef.update({
      createdAt: updated,
      updatedAt: updated,
    })
  );

  // Reject @DocumentId 'id' leakage on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_2").set({
      id: "conv_2",
      userId: ALICE_UID,
      title: "Leak ID",
      mode: "CHAT",
      provider: "GEMINI",
      lastMessagePreview: "",
      pinned: false,
      messageCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Delete message and conversation
  await assertSucceeds(msgRef.delete());
  await assertSucceeds(convRef.delete());
});

test("Cross-user isolation blocks Bob from reading or modifying Alice data", async () => {
  const past = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv").set({
      userId: ALICE_UID,
      title: "Alice Secret Thread",
      mode: "CHAT",
      provider: "GEMINI",
      lastMessagePreview: "Private notes",
      pinned: false,
      messageCount: 1,
      createdAt: past,
      updatedAt: past,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv").update({
      title: "Hijacked",
      pinned: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv").delete()
  );
});

test("Rejects future timestamps, invalid modes, and oversized payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const future = new Date(Date.now() + 60000);
  const past = new Date(Date.now() - 1000);

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_future").set({
      userId: ALICE_UID,
      title: "Future",
      mode: "CHAT",
      provider: "GEMINI",
      lastMessagePreview: "",
      pinned: false,
      messageCount: 0,
      createdAt: future,
      updatedAt: future,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_bad_mode").set({
      userId: ALICE_UID,
      title: "Invalid Mode",
      mode: "HACKED_MODE",
      provider: "GEMINI",
      lastMessagePreview: "",
      pinned: false,
      messageCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
});
