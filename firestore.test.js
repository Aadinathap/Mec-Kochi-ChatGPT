const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_mec_123";
const BOB_UID = "bob_mec_456";

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

test("Unauthenticated caller cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("campus_events").where("campusId", "==", "mec_kochi").get());
  await assertFails(unauthDb.collection("chat_messages").where("campusId", "==", "mec_kochi").get());
  await assertFails(unauthDb.collection("department_resources").where("campusId", "==", "mec_kochi").get());
  await assertFails(unauthDb.collection("campus_alerts").where("campusId", "==", "mec_kochi").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Student profile enforces owner isolation and rejects ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Aadinath P",
      department: "CSE",
      semester: "S6",
      notificationsEnabled: true,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Cross-user read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: nowTimestamp(),
    })
  );

  // Ghost field / shadow update rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Aadinath P",
      department: "CSE",
      semester: "S6",
      notificationsEnabled: true,
      isAdmin: true,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );
});

test("Academic schedule is strictly isolated per student", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("academic_schedule").doc("slot_1").set({
      userId: ALICE_UID,
      courseCode: "CST301",
      courseTitle: "Formal Languages & Automata Theory",
      facultyName: "Dr. Priya Nair",
      room: "CS-302",
      dayOfWeek: "MON",
      startTime: "09:30 AM",
      endTime: "10:25 AM",
      slotType: "Lecture",
      reminderEnabled: true,
      isCompleted: false,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("academic_schedule").get()
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("academic_schedule").get()
  );
});

test("Chat messages enforce campusId query filter, sender integrity, and owner-only delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("chat_messages").doc("msg_1").set({
      campusId: "mec_kochi",
      channelId: "cse",
      senderId: ALICE_UID,
      senderName: "Aadinath",
      senderDepartment: "CSE",
      senderSemester: "S6",
      content: "Anyone has CST301 Module 3 notes?",
      tag: "Doubt",
      createdAt: nowTimestamp(),
    })
  );

  // Unfiltered query without campusId fails
  await assertFails(bobDb.collection("chat_messages").get());

  // Filtered query with campusId == mec_kochi succeeds
  await assertSucceeds(
    bobDb.collection("chat_messages").where("campusId", "==", "mec_kochi").get()
  );

  // Spoofed senderId fails
  await assertFails(
    bobDb.collection("chat_messages").doc("msg_2").set({
      campusId: "mec_kochi",
      channelId: "cse",
      senderId: ALICE_UID,
      senderName: "Bob",
      senderDepartment: "CSE",
      senderSemester: "S6",
      content: "Spoofed message",
      createdAt: nowTimestamp(),
    })
  );

  // Cross-user delete fails
  await assertFails(bobDb.collection("chat_messages").doc("msg_1").delete());

  // Owner delete succeeds
  await assertSucceeds(aliceDb.collection("chat_messages").doc("msg_1").delete());

  // Image-only message with valid HTTPS Firebase Storage URL succeeds
  await assertSucceeds(
    aliceDb.collection("chat_messages").doc("msg_img_1").set({
      campusId: "mec_kochi",
      channelId: "cse",
      senderId: ALICE_UID,
      senderName: "Aadinath",
      senderDepartment: "CSE",
      senderSemester: "S6",
      content: "",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/mec-app/o/chat_images%2Fcse%2Fphoto.jpg",
      createdAt: nowTimestamp(),
    })
  );

  // Empty message without imageUrl fails
  await assertFails(
    aliceDb.collection("chat_messages").doc("msg_empty").set({
      campusId: "mec_kochi",
      channelId: "cse",
      senderId: ALICE_UID,
      senderName: "Aadinath",
      senderDepartment: "CSE",
      senderSemester: "S6",
      content: "",
      createdAt: nowTimestamp(),
    })
  );

  // Non-HTTPS imageUrl fails
  await assertFails(
    aliceDb.collection("chat_messages").doc("msg_bad_url").set({
      campusId: "mec_kochi",
      channelId: "cse",
      senderId: ALICE_UID,
      senderName: "Aadinath",
      senderDepartment: "CSE",
      senderSemester: "S6",
      content: "Look at this",
      imageUrl: "http://insecure.example.com/photo.jpg",
      createdAt: nowTimestamp(),
    })
  );

  // Read receipts: Bob can record his own UID receipt on Alice's message (even without initial readReceipts field)
  await assertSucceeds(
    bobDb.collection("chat_messages").doc("msg_img_1").update({
      [`readReceipts.${BOB_UID}`]: "Abil Sajee",
    })
  );

  // Read receipts: Bob cannot forge Alice's UID or mutate message content while marking read
  await assertFails(
    bobDb.collection("chat_messages").doc("msg_img_1").update({
      [`readReceipts.${ALICE_UID}`]: "Spoofed",
    })
  );
  await assertFails(
    bobDb.collection("chat_messages").doc("msg_img_1").update({
      [`readReceipts.${BOB_UID}`]: "Abil Sajee",
      content: "Tampered text",
    })
  );
});

test("Chat typing indicators enforce owner-scoped document IDs and campusId queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection("chat_typing").where("campusId", "==", "mec_kochi").get());

  await assertSucceeds(
    aliceDb.collection("chat_typing").doc(`general_${ALICE_UID}`).set({
      campusId: "mec_kochi",
      channelId: "general",
      userId: ALICE_UID,
      displayName: "Aadinath",
      department: "CSE",
      isTyping: true,
      updatedAt: nowTimestamp(),
    })
  );

  // Bob can query mec_kochi typing indicators
  await assertSucceeds(
    bobDb.collection("chat_typing").where("campusId", "==", "mec_kochi").get()
  );

  // Spoofed doc ID or cross-user update fails
  await assertFails(
    bobDb.collection("chat_typing").doc(`general_${ALICE_UID}`).set({
      campusId: "mec_kochi",
      channelId: "general",
      userId: BOB_UID,
      displayName: "Abil",
      department: "CSE",
      isTyping: true,
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("chat_typing").doc(`general_${ALICE_UID}`).update({
      isTyping: false,
      updatedAt: nowTimestamp(),
    })
  );

  // Owner can clear typing status
  await assertSucceeds(
    aliceDb.collection("chat_typing").doc(`general_${ALICE_UID}`).update({
      isTyping: false,
      updatedAt: nowTimestamp(),
    })
  );
});

test("Campus events allow filtered listing, creator editing, and single-step RSVP updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("campus_events").doc("evt_excel").set({
      campusId: "mec_kochi",
      title: "Excel 2026 Hackathon",
      organizer: "Excel MEC",
      category: "Hackathon",
      venue: "MEC Main Auditorium",
      dateLabel: "Nov 22, 2026",
      timeLabel: "09:00 AM",
      description: "24-hour flagship coding hackathon at Model Engineering College Kochi.",
      department: "ALL",
      creatorId: ALICE_UID,
      rsvpCount: 1,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Bob can list with campusId filter and increment rsvpCount by 1
  await assertSucceeds(bobDb.collection("campus_events").where("campusId", "==", "mec_kochi").get());
  await assertSucceeds(
    bobDb.collection("campus_events").doc("evt_excel").update({
      rsvpCount: 2,
      updatedAt: nowTimestamp(),
    })
  );

  // Bob cannot jump rsvpCount by +50 or change event title
  await assertFails(
    bobDb.collection("campus_events").doc("evt_excel").update({
      rsvpCount: 52,
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("campus_events").doc("evt_excel").update({
      title: "Hijacked Event",
      updatedAt: nowTimestamp(),
    })
  );
});

test("Department resources and campus alerts enforce schema validation and query scoping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("department_resources").doc("res_1").set({
      campusId: "mec_kochi",
      title: "CST301 Automata Complete Notes",
      department: "CSE",
      semester: "S5",
      subjectCode: "CST301",
      resourceType: "Notes",
      description: "Handwritten module-wise KTU notes covering DFA, NFA, and Turing Machines.",
      linkOrReference: "MEC Digital Library • Shelf CS-5B",
      uploaderId: ALICE_UID,
      uploaderName: "Aadinath",
      upvotes: 0,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    bobDb.collection("department_resources").where("campusId", "==", "mec_kochi").get()
  );

  await assertSucceeds(
    bobDb.collection("department_resources").doc("res_pdf").set({
      campusId: "mec_kochi",
      title: "KTU S5 Syllabus Handbook PDF",
      department: "ECE",
      semester: "S5",
      subjectCode: "ECT301",
      resourceType: "PDF",
      description: "Official KTU 2019 scheme syllabus PDF.",
      linkOrReference: "https://ktu.edu.in/syllabus.pdf",
      uploaderId: BOB_UID,
      uploaderName: "Bob",
      upvotes: 0,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    bobDb.collection("department_resources").doc("res_link").set({
      campusId: "mec_kochi",
      title: "FOSSMEC GitHub Study Archive",
      department: "CSE",
      semester: "S5",
      subjectCode: "CST303",
      resourceType: "Link",
      description: "Curated links for Computer Networks lab and NPTEL lectures.",
      linkOrReference: "https://github.com/fossmec",
      uploaderId: BOB_UID,
      uploaderName: "Bob",
      upvotes: 0,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertFails(
    bobDb.collection("department_resources").doc("res_invalid").set({
      campusId: "mec_kochi",
      title: "Invalid Type Resource",
      department: "CSE",
      semester: "S5",
      subjectCode: "CST303",
      resourceType: "Executable",
      description: "Should fail rules.",
      linkOrReference: "https://example.com",
      uploaderId: BOB_UID,
      uploaderName: "Bob",
      upvotes: 0,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("campus_alerts").doc("alert_1").set({
      campusId: "mec_kochi",
      title: "KTU S5 Series Exam Timetable Released",
      message: "First series exams begin next Monday at 9:30 AM across Main Block halls.",
      category: "Exam",
      department: "ALL",
      senderId: ALICE_UID,
      senderName: "Academic Cell",
      createdAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    bobDb.collection("campus_alerts").where("campusId", "==", "mec_kochi").get()
  );
  await assertFails(
    bobDb.collection("campus_alerts").doc("alert_1").update({ title: "Edited" })
  );
});

test("FCM device tokens enforce owner isolation and schema bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("fcm_tokens").doc("tok_abc1234567").set({
      userId: ALICE_UID,
      token: "fcm_sample_token_1234567890_abcdef",
      department: "CSE",
      topics: ["mec_announcements", "mec_events", "mec_dept_cse"],
      enabled: true,
      platform: "android",
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Cross-user read & write denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("fcm_tokens").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("fcm_tokens").doc("tok_abc1234567").set({
      userId: ALICE_UID,
      token: "hijacked_token_1234567890",
      department: "CSE",
      topics: ["mec_announcements"],
      enabled: true,
      platform: "android",
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Owner update succeeds without mutating createdAt or userId
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("fcm_tokens").doc("tok_abc1234567").update({
      enabled: false,
      topics: [],
      updatedAt: nowTimestamp(),
    })
  );

  // Invalid platform or ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("fcm_tokens").doc("tok_bad").set({
      userId: ALICE_UID,
      token: "fcm_sample_token_1234567890_abcdef",
      department: "CSE",
      topics: ["mec_announcements"],
      enabled: true,
      platform: "ios",
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );
});
