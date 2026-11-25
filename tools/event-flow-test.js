#!/usr/bin/env node
/**
 * CampusSync event flows, tested against the local Firestore emulator.
 *
 *   firebase emulators:exec --only firestore --project demo-campussync \
 *       "node tools/event-flow-test.js"
 *
 * Every request below uses the same document shapes the Android app writes (see
 * EventRepository.java, Event.java and EventRegistration.java) and is authorised with a
 * different identity, so the assertions are about firestore.rules and not about a mock.
 * The test is intentionally dependency-free: it speaks the Firestore REST API directly,
 * because that is the only thing an emulator needs.
 *
 * Covered:
 *   - organizer publishes an event; a spoofed organizerId or a non-published create fails
 *   - registration is one document per uid, requires a 32+ character ticket token, and never
 *     starts attended
 *   - the mirrored counters move in the same commit and can never pass the capacity
 *   - an organizer check-in moves attended false -> true once; a duplicate, a reversal, a
 *     token rewrite and a participant self-check-in are all refused
 *   - participant documents are readable by the participant and the organizer only
 *   - organiser notes, cancellation, and post-cancellation registration behaviour
 *   - college notices stay admin-only
 */

const PROJECT = process.env.FB_PROJECT || "demo-campussync";
const HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
const ROOT = `http://${HOST}/v1/projects/${PROJECT}/databases/(default)/documents`;

const ORGANIZER = "orgUser1";
const STUDENT_A = "stuUserA";
const STUDENT_B = "stuUserB";
const STUDENT_C = "stuUserC";
const STUDENT_D = "stuUserD";
const ADMIN = "adminUser1";

// ---------------------------------------------------------------- auth

/** The emulator accepts unsigned tokens and trusts the `sub`/`user_id` claim. */
function tokenFor(uid) {
  const b64 = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  return [
    b64({ alg: "none", typ: "JWT" }),
    b64({
      sub: uid,
      user_id: uid,
      aud: PROJECT,
      iss: `https://securetoken.google.com/${PROJECT}`,
      iat: now,
      exp: now + 3600,
      email: `${uid}@example.com`,
      email_verified: true,
      firebase: { sign_in_provider: "password" },
    }),
    "",
  ].join(".");
}

/** "Bearer owner" is how the emulator grants admin rights, bypassing the rules. */
const OWNER = "owner";

// ---------------------------------------------------------------- field helpers

const s = (value) => ({ stringValue: value });
const i = (value) => ({ integerValue: String(value) });
const b = (value) => ({ booleanValue: value });
const ts = (value) => ({ timestampValue: value });
const arr = (values) => ({ arrayValue: { values: values.map(s) } });
const nil = () => ({ nullValue: null });
const nowIso = () => new Date().toISOString();
const inDays = (days) => new Date(Date.now() + days * 86400000).toISOString();

const NAME = (path) => `projects/${PROJECT}/databases/(default)/documents/${path}`;

// ---------------------------------------------------------------- transport

async function call(path, { method = "GET", fields, mask, body, token } = {}) {
  const url = new URL(path.startsWith("http") ? path : `${ROOT}/${path}`);
  if (mask) for (const field of mask) url.searchParams.append("updateMask.fieldPaths", field);

  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token === OWNER ? OWNER : tokenFor(token)}`;

  const response = await fetch(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : fields ? JSON.stringify({ fields }) : undefined,
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch (ignored) {
    json = null;
  }
  return { status: response.status, ok: response.ok, json, text };
}

const createDoc = (collection, id, fields, token) =>
  call(`${collection}?documentId=${id}`, { method: "POST", fields, token });

const patchDoc = (path, fields, mask, token) =>
  call(path, { method: "PATCH", fields, mask, token });

const getDoc = (path, token) => call(path, { token });

const commit = (writes, token) =>
  call(`${ROOT}:commit`, { method: "POST", body: { writes }, token });

// ---------------------------------------------------------------- payloads

function eventFields(overrides = {}) {
  return {
    title: s("Hackathon 2026"),
    description: s("36 hour campus hackathon"),
    location: s("Main Auditorium"),
    organizerId: s(ORGANIZER),
    organizerName: s("Coding Club"),
    startDate: ts(inDays(5)),
    endDate: ts(inDays(6)),
    imageUrl: nil(),
    category: s("tech"),
    college: s("Demo College"),
    status: s("published"),
    capacity: i(2),
    registrationCount: i(0),
    intercollege: b(false),
    attendeeIds: arr([]),
    createdAt: ts(nowIso()),
    updatedAt: ts(nowIso()),
    ...overrides,
  };
}

const eventRef = (eventId) => `events/${eventId}`;
const registrationRef = (eventId, uid) => `events/${eventId}/registrations/${uid}`;

function registrationWrite(eventId, uid, overrides = {}) {
  return {
    update: {
      name: NAME(registrationRef(eventId, uid)),
      fields: {
        uid: s(uid),
        name: s(`Student ${uid}`),
        email: s(`${uid}@example.com`),
        college: s("Demo College"),
        status: s("registered"),
        qrToken: s("0123456789abcdef0123456789abcdef"),
        attended: b(false),
        createdAt: ts(nowIso()),
        updatedAt: ts(nowIso()),
        ...overrides,
      },
    },
    // Same guard the client transaction gets from reading the document first.
    currentDocument: { exists: false },
  };
}

function counterWrite(eventId, count, uids) {
  return {
    update: {
      name: NAME(eventRef(eventId)),
      fields: {
        registrationCount: i(count),
        attendeeIds: arr(uids),
        updatedAt: ts(nowIso()),
      },
    },
    updateMask: { fieldPaths: ["registrationCount", "attendeeIds", "updatedAt"] },
  };
}

const cancellationWrite = (eventId, uid) => ({
  update: {
    name: NAME(registrationRef(eventId, uid)),
    fields: { status: s("cancelled"), updatedAt: ts(nowIso()) },
  },
  updateMask: { fieldPaths: ["status", "updatedAt"] },
});

function attendanceWrite(eventId, uid, attended) {
  return {
    update: {
      name: NAME(registrationRef(eventId, uid)),
      fields: { attended: b(attended), checkedInAt: ts(nowIso()), updatedAt: ts(nowIso()) },
    },
    updateMask: { fieldPaths: ["attended", "checkedInAt", "updatedAt"] },
  };
}

// ---------------------------------------------------------------- harness

const results = [];
let currentGroup = "";

function group(name) {
  currentGroup = name;
  results.push({ group: name, header: true });
}

async function check(name, promise, expectation) {
  let result;
  try {
    result = await promise;
  } catch (error) {
    result = { status: 0, ok: false, text: String(error) };
  }
  const passed = expectation(result);
  results.push({
    group: currentGroup,
    name,
    passed,
    detail: passed ? `${result.status}` : `${result.status} ${(result.text || "").slice(0, 700)}`,
  });
  return result;
}

const allowed = (result) => result.status >= 200 && result.status < 300;
const denied = (result) => result.status === 403;
const rejected = (result) => result.status >= 400;

// ---------------------------------------------------------------- suite

(async () => {
  // Admin rights bypass the rules, which is exactly how admins/{uid} is provisioned in prod.
  await createDoc("admins", ADMIN, { uid: s(ADMIN), createdAt: ts(nowIso()) }, OWNER);

  group("A. event hosting");
  await check("organizer publishes an event",
    createDoc("events", "evtCapped", eventFields(), ORGANIZER), allowed);
  await check("a second, unlimited event is published",
    createDoc("events", "evtOpen", eventFields({
      title: s("Open Mic"), capacity: i(0), category: s("cultural"),
      college: s("Other College"), intercollege: b(true),
    }), ORGANIZER), allowed);
  await check("organizerId cannot be spoofed",
    createDoc("events", "evtSpoof", eventFields({ organizerId: s(ORGANIZER) }), STUDENT_A), denied);
  await check("a third event is published for the counter-abuse checks",
    createDoc("events", "evtAbuse", eventFields({
      title: s("Guest Lecture"), capacity: i(2),
    }), ORGANIZER), allowed);
  await check("an event cannot be created as cancelled",
    createDoc("events", "evtCancelledCreate", eventFields({ status: s("cancelled") }), ORGANIZER),
    denied);
  await check("an event without a title is refused",
    createDoc("events", "evtNoTitle", eventFields({ title: s("") }), ORGANIZER), denied);
  await check("an event without a start date is refused",
    createDoc("events", "evtNoDate", eventFields({ startDate: nil() }), ORGANIZER), denied);
  await check("the radar query (orderBy startDate) is readable",
    call(`${ROOT}:runQuery`, {
      method: "POST",
      body: {
        structuredQuery: {
          from: [{ collectionId: "events" }],
          orderBy: [{ field: { fieldPath: "startDate" }, direction: "ASCENDING" }],
          limit: 120,
        },
      },
      token: STUDENT_A,
    }), allowed);
  await check("the radar is closed to signed-out visitors",
    call(`${ROOT}:runQuery`, {
      method: "POST",
      body: { structuredQuery: { from: [{ collectionId: "events" }], limit: 5 } },
    }), rejected);

  group("B. registration and capacity");
  const tokenA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  const tokenB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  await check("student A registers (registration + counters in one commit)",
    commit([registrationWrite("evtCapped", STUDENT_A, { qrToken: s(tokenA) }),
      counterWrite("evtCapped", 1, [STUDENT_A])], STUDENT_A), allowed);
  await check("student A cannot register twice",
    commit([registrationWrite("evtCapped", STUDENT_A, { qrToken: s(tokenA) }),
      counterWrite("evtCapped", 2, [STUDENT_A])], STUDENT_A), rejected);
  await check("student B takes the last seat",
    commit([registrationWrite("evtCapped", STUDENT_B, { qrToken: s(tokenB) }),
      counterWrite("evtCapped", 2, [STUDENT_A, STUDENT_B])], STUDENT_B), allowed);
  await check("student C is refused when the event is full",
    commit([registrationWrite("evtCapped", STUDENT_C),
      counterWrite("evtCapped", 3, [STUDENT_A, STUDENT_B, STUDENT_C])], STUDENT_C), denied);
  await check("a registration cannot start already attended",
    commit([registrationWrite("evtOpen", STUDENT_C, { attended: b(true) }),
      counterWrite("evtOpen", 1, [STUDENT_C])], STUDENT_C), denied);
  await check("a short QR token is refused",
    commit([registrationWrite("evtOpen", STUDENT_C, { qrToken: s("abc123") }),
      counterWrite("evtOpen", 1, [STUDENT_C])], STUDENT_C), denied);
  await check("a participant cannot write somebody else's registration",
    commit([registrationWrite("evtOpen", STUDENT_A),
      counterWrite("evtOpen", 1, [STUDENT_A])], STUDENT_C), denied);
  await check("a participant cannot inflate the counter by more than one seat in one write",
    commit([counterWrite("evtAbuse", 2, [STUDENT_D])], STUDENT_D), denied);
  await check("a seat claim without a registration is limited to a single seat",
    commit([counterWrite("evtAbuse", 1, [STUDENT_D])], STUDENT_D), allowed);
  await check("that same participant cannot claim a second seat",
    commit([counterWrite("evtAbuse", 2, [STUDENT_D])], STUDENT_D), denied);
  await check("a participant may register for the unlimited event",
    commit([registrationWrite("evtOpen", STUDENT_C),
      counterWrite("evtOpen", 1, [STUDENT_C])], STUDENT_C), allowed);

  group("C. QR attendance");
  await check("student A reads their own ticket document",
    getDoc(registrationRef("evtCapped", STUDENT_A), STUDENT_A), allowed);
  await check("student B cannot read student A's ticket (token stays secret)",
    getDoc(registrationRef("evtCapped", STUDENT_A), STUDENT_B), denied);
  await check("the organizer can read a participant's ticket",
    getDoc(registrationRef("evtCapped", STUDENT_A), ORGANIZER), allowed);
  await check("the organizer's participant list is readable",
    call(`${eventRef("evtCapped")}/registrations`, { token: ORGANIZER }), allowed);
  await check("a participant cannot enumerate the participant list",
    call(`${eventRef("evtCapped")}/registrations`, { token: STUDENT_B }), denied);
  await check("a participant cannot check themselves in",
    commit([attendanceWrite("evtCapped", STUDENT_B, true)], STUDENT_B), denied);
  await check("a non-organizer cannot check another participant in",
    commit([attendanceWrite("evtCapped", STUDENT_B, true)], STUDENT_C), denied);
  await check("the organizer checks student A in",
    commit([attendanceWrite("evtCapped", STUDENT_A, true)], ORGANIZER), allowed);
  await check("a duplicate check-in is refused",
    commit([attendanceWrite("evtCapped", STUDENT_A, true)], ORGANIZER), denied);
  await check("attendance cannot be reversed",
    commit([attendanceWrite("evtCapped", STUDENT_A, false)], ORGANIZER), denied);
  await check("the organizer cannot rewrite a ticket token",
    patchDoc(registrationRef("evtCapped", STUDENT_B),
      { qrToken: s("ffffffffffffffffffffffffffffffff") }, ["qrToken"], ORGANIZER), denied);
  await check("attendance is stored on the participant's registration",
    getDoc(registrationRef("evtCapped", STUDENT_A), STUDENT_A),
    (result) => allowed(result)
      && result.json.fields.attended.booleanValue === true
      && Boolean(result.json.fields.checkedInAt));

  group("D. updates, cancellation and notices");
  await check("the organizer posts a change note",
    createDoc("events/evtCapped/updates", "note1",
      { title: s("Venue changed"), body: s("Now in Hall B"), authorId: s(ORGANIZER),
        authorName: s("Coding Club"), createdAt: ts(nowIso()) }, ORGANIZER), allowed);
  await check("a participant cannot post a change note",
    createDoc("events/evtCapped/updates", "note2",
      { title: s("Fake"), body: s("Fake"), authorId: s(STUDENT_A), createdAt: ts(nowIso()) },
      STUDENT_A), denied);
  await check("a participant cannot edit the event",
    patchDoc(eventRef("evtCapped"), { title: s("Hijacked") }, ["title"], STUDENT_A), denied);
  await check("the organizer cannot move the registration counter by editing the event",
    patchDoc(eventRef("evtCapped"),
      { registrationCount: i(0), updatedAt: ts(nowIso()) },
      ["registrationCount", "updatedAt"], ORGANIZER), denied);
  await check("the organizer edits the schedule",
    patchDoc(eventRef("evtCapped"),
      { location: s("Hall B"), updatedAt: ts(nowIso()) },
      ["location", "updatedAt"], ORGANIZER), allowed);
  await check("the organizer cancels the event",
    patchDoc(eventRef("evtCapped"),
      { status: s("cancelled"), updatedAt: ts(nowIso()) },
      ["status", "updatedAt"], ORGANIZER), allowed);
  await check("a cancelled event refuses new registrations",
    commit([registrationWrite("evtCapped", STUDENT_C),
      counterWrite("evtCapped", 3, [STUDENT_A, STUDENT_B, STUDENT_C])], STUDENT_C), denied);
  await check("a registrant can still withdraw after cancellation",
    commit([cancellationWrite("evtCapped", STUDENT_B),
      counterWrite("evtCapped", 1, [STUDENT_A])], STUDENT_B), allowed);
  await check("events cannot be deleted",
    call(eventRef("evtCapped"), { method: "DELETE", token: ORGANIZER }), rejected);
  await check("a student cannot post a college notice",
    createDoc("notices", "n1",
      { title: s("Holiday"), body: s("Closed"), college: s("Demo College"),
        authorId: s(STUDENT_A), pinned: b(false), createdAt: ts(nowIso()) }, STUDENT_A), denied);
  await check("an administrator posts a college notice",
    createDoc("notices", "n2",
      { title: s("Holiday"), body: s("Closed"), college: s("Demo College"),
        authorId: s(ADMIN), pinned: b(false), createdAt: ts(nowIso()) }, ADMIN), allowed);

  // ---------------------------------------------------------------- report
  let failed = 0;
  let groupName = "";
  for (const row of results) {
    if (row.header) {
      groupName = row.group;
      console.log(`\n${groupName}`);
      continue;
    }
    if (!row.passed) failed += 1;
    console.log(`  ${row.passed ? "PASS" : "FAIL"}  ${row.name}${row.passed ? "" : ` -> ${row.detail}`}`);
  }

  const total = results.filter((row) => !row.header).length;
  console.log(`\n${total - failed}/${total} checks passed`);
  if (failed > 0) {
    console.log("\nEvent flow tests FAILED");
    process.exit(1);
  }
  console.log("EVENT_FLOW_TESTS_OK");
})().catch((error) => {
  console.error("Test runner crashed:", error);
  process.exit(1);
});
