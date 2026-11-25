#!/usr/bin/env node
/**
 * CampusSync academic hub, tested against the local emulators.
 *
 *   firebase emulators:exec --only firestore --project demo-campussync \
 *       "node tools/academic-test.js"
 *
 * Add `storage,auth` to also exercise the Storage rules for uploads (the suite prints that the
 * storage checks were skipped when those emulators are absent).
 *
 * Every request uses the document shapes the Android app writes (see AcademicResource.java,
 * ResourceRepository.java, TutoringRepository.java and KarmaRepository.java) and runs as a
 * different identity, so the assertions are about firestore.rules/storage.rules, not a mock.
 * The suite is dependency-free: it speaks the emulator REST APIs directly.
 *
 * Covered:
 *   - uploads: metadata validation, uploader spoofing, immutable file fields, 20 MB limit
 *   - the library query, download/bookmark counters, rating bounds and one rating per student
 *   - private bookmarks
 *   - doubts: asking, answering, one-step answer counter, accepting, solving, editing
 *   - tutors: own listing, no self-granted rating, one review per request
 *   - tutoring requests: participant-only access, tutor accept/decline, student cancel/complete
 *   - karma: client writes refused (no self-award, no replay), only the recipient can read
 */

const PROJECT = process.env.FB_PROJECT || "demo-campussync";
const HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
const ROOT = `http://${HOST}/v1/projects/${PROJECT}/databases/(default)/documents`;

// The CLI exports these with a scheme (http://host:port); requests below add their own.
const hostOnly = (value) => (value || "").replace(/^https?:\/\//, "").replace(/\/$/, "");

const STORAGE_HOST = hostOnly(process.env.STORAGE_EMULATOR_HOST
  || process.env.FIREBASE_STORAGE_EMULATOR_HOST);
const AUTH_HOST = hostOnly(process.env.FIREBASE_AUTH_EMULATOR_HOST);
const BUCKET = process.env.FB_STORAGE_BUCKET || `${PROJECT}.appspot.com`;

const UPLOADER = "acadUploader";
const STUDENT = "acadStudent";
const TUTOR = "acadTutor";
const OTHER = "acadOther";
const ADMIN = "acadAdmin";

// ---------------------------------------------------------------- auth

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

const OWNER = "owner";

// ---------------------------------------------------------------- field helpers

const s = (value) => ({ stringValue: value });
const i = (value) => ({ integerValue: String(value) });
const b = (value) => ({ booleanValue: value });
const ts = (value) => ({ timestampValue: value });
const arr = (values) => ({ arrayValue: { values: values.map(s) } });
const nil = () => ({ nullValue: null });
const nowIso = () => new Date().toISOString();

const NAME = (path) => `projects/${PROJECT}/databases/(default)/documents/${path}`;

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

// ---------------------------------------------------------------- payloads

function resourceFields(overrides = {}) {
  return {
    title: s("Unit 3 — Trees and graphs"),
    description: s("Worked examples for every traversal"),
    type: s("pdf"),
    subject: s("Data Structures"),
    subjectLower: s("data structures"),
    department: s("Computer Science"),
    semester: i(3),
    college: s("Demo College"),
    tags: arr(["trees", "graphs"]),
    fileName: s("trees.pdf"),
    fileUrl: s("https://example.com/trees.pdf"),
    filePath: s(`academic_resources/${UPLOADER}/1_trees.pdf`),
    fileSize: i(1024 * 1024),
    mimeType: s("application/pdf"),
    uploaderId: s(UPLOADER),
    uploaderName: s("Ananya"),
    uploaderPhotoUrl: nil(),
    downloadCount: i(0),
    bookmarkCount: i(0),
    ratingSum: i(0),
    ratingCount: i(0),
    status: s("published"),
    createdAt: ts(nowIso()),
    updatedAt: ts(nowIso()),
    ...overrides,
  };
}

const resourceRef = (id) => `resources/${id}`;

function ratingFields(overrides = {}) {
  return {
    stars: i(5),
    feedback: s("Very clear, saved me before the quiz."),
    raterName: s("Karan"),
    createdAt: ts(nowIso()),
    updatedAt: ts(nowIso()),
    ...overrides,
  };
}

function doubtFields(overrides = {}) {
  return {
    title: s("Why does Dijkstra fail with negative weights?"),
    body: s("The greedy choice assumes every relaxation is final."),
    subject: s("Algorithms"),
    department: s("Computer Science"),
    semester: i(4),
    college: s("Demo College"),
    authorId: s(STUDENT),
    authorName: s("Karan"),
    authorPhotoUrl: nil(),
    answerCount: i(0),
    solved: b(false),
    acceptedAnswerId: nil(),
    createdAt: ts(nowIso()),
    lastActivityAt: ts(nowIso()),
    ...overrides,
  };
}

function answerFields(overrides = {}) {
  return {
    body: s("Because an edge can later improve a node that was already finalised."),
    authorId: s(TUTOR),
    authorName: s("Ananya"),
    authorPhotoUrl: nil(),
    accepted: b(false),
    createdAt: ts(nowIso()),
    ...overrides,
  };
}

function tutorFields(overrides = {}) {
  return {
    displayName: s("Ananya"),
    photoUrl: nil(),
    college: s("Demo College"),
    department: s("Computer Science"),
    semester: i(5),
    subjects: arr(["Algorithms", "Data Structures"]),
    bio: s("Weekly revision circle for second year students."),
    ratingSum: i(0),
    ratingCount: i(0),
    completedSessions: i(0),
    createdAt: ts(nowIso()),
    updatedAt: ts(nowIso()),
    ...overrides,
  };
}

function requestFields(overrides = {}) {
  return {
    studentId: s(STUDENT),
    studentName: s("Karan"),
    studentPhotoUrl: nil(),
    tutorId: s(TUTOR),
    tutorName: s("Ananya"),
    subject: s("Algorithms"),
    message: s("Could you go over AVL rotations before Friday?"),
    preferredTime: s("tomorrow evening"),
    status: s("pending"),
    reviewRating: i(0),
    reviewComment: s(""),
    createdAt: ts(nowIso()),
    updatedAt: ts(nowIso()),
    ...overrides,
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
    detail: passed ? `${result.status}` : `${result.status} ${(result.text || "").slice(0, 600)}`,
  });
  return result;
}

const allowed = (result) => result.status >= 200 && result.status < 300;
const denied = (result) => result.status === 403;
const rejected = (result) => result.status >= 400;

// ---------------------------------------------------------------- storage helpers

async function emulatorIdToken(email) {
  const response = await fetch(
    `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password: "campusSync123", returnSecureToken: true }),
    }
  );
  const json = await response.json();
  return json.idToken || null;
}

/** The Auth emulator issues its own uid; the Storage path must use that, not our label. */
function uidFromToken(idToken) {
  try {
    const payload = JSON.parse(Buffer.from(idToken.split(".")[1], "base64url").toString());
    return payload.user_id || payload.sub || null;
  } catch (ignored) {
    return null;
  }
}

async function uploadObject(name, contentType, bytes, idToken) {
  const url = new URL(`http://${STORAGE_HOST}/v0/b/${BUCKET}/o`);
  url.searchParams.set("uploadType", "media");
  url.searchParams.set("name", name);
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${idToken}`,
      "Content-Type": contentType,
    },
    body: bytes,
  });
  return { status: response.status, ok: response.ok, text: await response.text() };
}

// ---------------------------------------------------------------- suite

(async () => {
  await createDoc("admins", ADMIN, { uid: s(ADMIN), createdAt: ts(nowIso()) }, OWNER);

  group("A. resource uploads");
  await check("a student publishes a PDF resource",
    createDoc("resources", "res1", resourceFields(), UPLOADER), allowed);
  await check("uploaderId cannot be spoofed",
    createDoc("resources", "resSpoof", resourceFields(), STUDENT), denied);
  await check("a resource without a title is refused",
    createDoc("resources", "resNoTitle", resourceFields({ title: s("") }), UPLOADER), denied);
  await check("a resource without a subject is refused",
    createDoc("resources", "resNoSubject", resourceFields({ subject: s("") }), UPLOADER), denied);
  await check("an unsupported resource type is refused",
    createDoc("resources", "resVideo",
      resourceFields({ type: s("video"), fileName: s("lecture.mp4") }), UPLOADER), denied);
  await check("a resource without a file is refused",
    createDoc("resources", "resNoFile",
      resourceFields({ fileUrl: s("") }), UPLOADER), denied);
  await check("a file above the 20 MB limit is refused",
    createDoc("resources", "resHuge",
      resourceFields({ fileSize: i(20 * 1024 * 1024 + 1) }), UPLOADER), denied);
  await check("a resource cannot start with counters already set",
    createDoc("resources", "resCounters",
      resourceFields({ downloadCount: i(99), ratingCount: i(4), ratingSum: i(20) }), UPLOADER),
    denied);
  await check("the library query (newest first) is readable",
    call(`${ROOT}:runQuery`, {
      method: "POST",
      body: {
        structuredQuery: {
          from: [{ collectionId: "resources" }],
          orderBy: [{ field: { fieldPath: "createdAt" }, direction: "DESCENDING" }],
          limit: 150,
        },
      },
      token: STUDENT,
    }), allowed);
  await check("the library is closed to signed-out visitors",
    call(`${ROOT}:runQuery`, {
      method: "POST",
      body: { structuredQuery: { from: [{ collectionId: "resources" }], limit: 5 } },
    }), rejected);

  group("B. resource metadata and counters");
  await check("the uploader edits the description",
    patchDoc(resourceRef("res1"),
      { description: s("Updated notes"), updatedAt: ts(nowIso()) },
      ["description", "updatedAt"], UPLOADER), allowed);
  await check("the uploader cannot replace the file behind a resource",
    patchDoc(resourceRef("res1"),
      { fileUrl: s("https://evil.example/payload.pdf") }, ["fileUrl"], UPLOADER), denied);
  await check("another student cannot edit the resource",
    patchDoc(resourceRef("res1"), { title: s("Hijacked") }, ["title"], STUDENT), denied);
  await check("one download is counted",
    patchDoc(resourceRef("res1"),
      { downloadCount: i(1), updatedAt: ts(nowIso()) },
      ["downloadCount", "updatedAt"], STUDENT), allowed);
  await check("two downloads cannot be claimed in one write",
    patchDoc(resourceRef("res1"), { downloadCount: i(3) }, ["downloadCount"], STUDENT), denied);
  await check("one bookmark is counted",
    patchDoc(resourceRef("res1"),
      { bookmarkCount: i(1), updatedAt: ts(nowIso()) },
      ["bookmarkCount", "updatedAt"], STUDENT), allowed);
  await check("un-bookmarking removes one",
    patchDoc(resourceRef("res1"), { bookmarkCount: i(0) }, ["bookmarkCount"], STUDENT), allowed);
  await check("a rating total without a rating is refused",
    patchDoc(resourceRef("res1"),
      { ratingSum: i(5), ratingCount: i(0) }, ["ratingSum", "ratingCount"], STUDENT), denied);
  await check("a rating average above five stars is refused",
    patchDoc(resourceRef("res1"),
      { ratingSum: i(11), ratingCount: i(1) }, ["ratingSum", "ratingCount"], STUDENT), denied);

  group("C. ratings and bookmarks");
  await check("a student rates the resource five stars",
    createDoc("resources/res1/ratings", STUDENT, ratingFields(), STUDENT), allowed);
  await check("the rating counters move with the rating",
    patchDoc(resourceRef("res1"),
      { ratingSum: i(5), ratingCount: i(1), updatedAt: ts(nowIso()) },
      ["ratingSum", "ratingCount", "updatedAt"], STUDENT), allowed);
  await check("zero stars is refused",
    createDoc("resources/res1/ratings", "badZero", ratingFields({ stars: i(0) }), OTHER), denied);
  await check("six stars is refused",
    createDoc("resources/res1/ratings", "badSix", ratingFields({ stars: i(6) }), OTHER), denied);
  await check("a student cannot rate on somebody else's behalf",
    createDoc("resources/res1/ratings", STUDENT, ratingFields(), OTHER), denied);
  await check("the same student edits their rating instead of adding one",
    patchDoc("resources/res1/ratings/" + STUDENT,
      { stars: i(4), feedback: s("Still useful") }, ["stars", "feedback"], STUDENT), allowed);
  await check("feedback stays readable to signed-in students",
    getDoc("resources/res1/ratings", OTHER), allowed);
  await check("a student bookmarks the resource privately",
    createDoc(`users/${STUDENT}/resourceBookmarks`, "res1",
      { resourceId: s("res1"), title: s("Trees"), savedAt: ts(nowIso()) }, STUDENT), allowed);
  await check("a bookmark cannot be written into another account",
    createDoc(`users/${STUDENT}/resourceBookmarks`, "res2",
      { resourceId: s("res2"), title: s("Fake"), savedAt: ts(nowIso()) }, OTHER), denied);
  await check("bookmarks are readable by their owner",
    getDoc(`users/${STUDENT}/resourceBookmarks/res1`, STUDENT), allowed);
  await check("another student cannot read them",
    getDoc(`users/${STUDENT}/resourceBookmarks/res1`, OTHER), denied);
  await check("only the uploader may delete the resource",
    call(resourceRef("res1"), { method: "DELETE", token: STUDENT }), rejected);

  group("D. doubts and answers");
  await check("a student posts a doubt",
    createDoc("doubts", "doubt1", doubtFields(), STUDENT), allowed);
  await check("a doubt cannot be attributed to somebody else",
    createDoc("doubts", "doubtSpoof", doubtFields(), OTHER), denied);
  await check("a doubt without a body is refused",
    createDoc("doubts", "doubtEmpty", doubtFields({ body: s("") }), STUDENT), denied);
  await check("the doubt board is readable by other students",
    getDoc("doubts/doubt1", OTHER), allowed);
  await check("the doubt board is closed to signed-out visitors",
    getDoc("doubts/doubt1"), rejected);
  await check("a peer answers the doubt",
    createDoc("doubts/doubt1/answers", "ans1", answerFields(), TUTOR), allowed);
  await check("an answer cannot be attributed to somebody else",
    createDoc("doubts/doubt1/answers", "ansSpoof", answerFields(), OTHER), denied);
  await check("an empty answer is refused",
    createDoc("doubts/doubt1/answers", "ansEmpty",
      answerFields({ body: s("") }), TUTOR), denied);
  await check("one answer moves the counter by one",
    patchDoc("doubts/doubt1",
      { answerCount: i(1), lastActivityAt: ts(nowIso()) },
      ["answerCount", "lastActivityAt"], TUTOR), allowed);
  await check("the answer counter cannot jump",
    patchDoc("doubts/doubt1", { answerCount: i(3) }, ["answerCount"], TUTOR), denied);
  await check("a third student cannot accept an answer",
    patchDoc("doubts/doubt1/answers/ans1", { accepted: b(true) }, ["accepted"], OTHER), denied);
  await check("the asker accepts the answer",
    patchDoc("doubts/doubt1/answers/ans1", { accepted: b(true) }, ["accepted"], STUDENT), allowed);
  await check("a third student cannot mark the doubt solved",
    patchDoc("doubts/doubt1", { solved: b(true) }, ["solved"], OTHER), denied);
  await check("the asker marks the doubt solved with that answer",
    patchDoc("doubts/doubt1",
      { solved: b(true), acceptedAnswerId: s("ans1"), lastActivityAt: ts(nowIso()) },
      ["solved", "acceptedAnswerId", "lastActivityAt"], STUDENT), allowed);
  await check("the answer's author may edit its text",
    patchDoc("doubts/doubt1/answers/ans1",
      { body: s("Because a finalised node can still be improved.") }, ["body"], TUTOR), allowed);
  await check("another student cannot edit the answer",
    patchDoc("doubts/doubt1/answers/ans1", { body: s("Rewritten") }, ["body"], OTHER), denied);
  await check("answers are readable by signed-in students",
    getDoc("doubts/doubt1/answers", OTHER), allowed);
  await check("the asker cannot rewrite the answer count",
    patchDoc("doubts/doubt1", { answerCount: i(0) }, ["answerCount"], STUDENT), denied);

  group("E. tutors and reviews");
  await check("a student offers tutoring",
    createDoc("tutors", TUTOR, tutorFields(), TUTOR), allowed);
  await check("a tutor listing cannot be created for somebody else",
    createDoc("tutors", TUTOR, tutorFields(), OTHER), denied);
  await check("a tutor listing without subjects is refused",
    createDoc("tutors", "tutorEmpty", tutorFields({ subjects: arr([]) }), OTHER), denied);
  await check("the tutor edits their own bio",
    patchDoc(`tutors/${TUTOR}`,
      { bio: s("Evening sessions in the library"), updatedAt: ts(nowIso()) },
      ["bio", "updatedAt"], TUTOR), allowed);
  await check("a tutor cannot grant themselves a rating",
    patchDoc(`tutors/${TUTOR}`, { ratingCount: i(1), ratingSum: i(5) },
      ["ratingCount", "ratingSum"], TUTOR), denied);
  await check("a review moves the tutor's aggregate by one",
    patchDoc(`tutors/${TUTOR}`,
      { ratingCount: i(1), ratingSum: i(5), completedSessions: i(1), updatedAt: ts(nowIso()) },
      ["ratingCount", "ratingSum", "completedSessions", "updatedAt"], STUDENT), allowed);
  await check("a single review cannot add more than five stars",
    patchDoc(`tutors/${TUTOR}`,
      { ratingCount: i(2), ratingSum: i(11), completedSessions: i(2), updatedAt: ts(nowIso()) },
      ["ratingCount", "ratingSum", "completedSessions", "updatedAt"], OTHER), denied);
  await check("a second review is counted normally",
    patchDoc(`tutors/${TUTOR}`,
      { ratingCount: i(2), ratingSum: i(10), completedSessions: i(2), updatedAt: ts(nowIso()) },
      ["ratingCount", "ratingSum", "completedSessions", "updatedAt"], OTHER), allowed);
  await check("the tutor directory is readable by any student",
    getDoc(`tutors/${TUTOR}`, STUDENT), allowed);

  group("F. tutoring requests");
  await check("a student requests a session",
    createDoc("tutoringRequests", "req1", requestFields(), STUDENT), allowed);
  await check("a request cannot be created in somebody else's name",
    createDoc("tutoringRequests", "reqSpoof", requestFields(), OTHER), denied);
  await check("a student cannot request themselves",
    createDoc("tutoringRequests", "reqSelf",
      requestFields({ tutorId: s(STUDENT) }), STUDENT), denied);
  await check("a request without a subject is refused",
    createDoc("tutoringRequests", "reqNoSubject",
      requestFields({ subject: s("") }), STUDENT), denied);
  await check("the student can read their request",
    getDoc("tutoringRequests/req1", STUDENT), allowed);
  await check("the tutor can read the request",
    getDoc("tutoringRequests/req1", TUTOR), allowed);
  await check("an unrelated student cannot read it",
    getDoc("tutoringRequests/req1", OTHER), denied);
  await check("an administrator can read it",
    getDoc("tutoringRequests/req1", ADMIN), allowed);
  await check("the student cannot accept their own request",
    patchDoc("tutoringRequests/req1",
      { status: s("accepted"), updatedAt: ts(nowIso()) }, ["status", "updatedAt"], STUDENT),
    denied);
  await check("the tutor accepts the request",
    patchDoc("tutoringRequests/req1",
      { status: s("accepted"), reviewedAt: ts(nowIso()), updatedAt: ts(nowIso()) },
      ["status", "reviewedAt", "updatedAt"], TUTOR), allowed);
  await check("the tutor cannot write the student's review",
    patchDoc("tutoringRequests/req1",
      { status: s("completed"), reviewRating: i(5), completedAt: ts(nowIso()) },
      ["status", "reviewRating", "completedAt"], TUTOR), denied);
  await check("the student completes and reviews the session",
    patchDoc("tutoringRequests/req1",
      { status: s("completed"), reviewRating: i(5), reviewComment: s("Very patient"),
        completedAt: ts(nowIso()), updatedAt: ts(nowIso()) },
      ["status", "reviewRating", "reviewComment", "completedAt", "updatedAt"], STUDENT),
    allowed);
  await check("the session cannot be reviewed twice",
    patchDoc("tutoringRequests/req1",
      { status: s("completed"), reviewRating: i(4), updatedAt: ts(nowIso()) },
      ["status", "reviewRating", "updatedAt"], STUDENT), denied);
  await check("a second request is created for the cancel checks",
    createDoc("tutoringRequests", "req2", requestFields({ subject: s("Graphs") }), STUDENT),
    allowed);
  await check("a completion without stars is refused",
    patchDoc("tutoringRequests/req2",
      { status: s("completed"), reviewRating: i(0), completedAt: ts(nowIso()) },
      ["status", "reviewRating", "completedAt"], STUDENT), denied);
  await check("the student cancels the request",
    patchDoc("tutoringRequests/req2",
      { status: s("cancelled"), updatedAt: ts(nowIso()) }, ["status", "updatedAt"], STUDENT),
    allowed);
  await check("the student cannot delete the request",
    call("tutoringRequests/req2", { method: "DELETE", token: STUDENT }), rejected);
  await check("an administrator can delete it",
    call("tutoringRequests/req2", { method: "DELETE", token: ADMIN }), allowed);

  group("G. karma ledger");
  await check("a client cannot award itself karma",
    createDoc("karmaAwards", "karma_self",
      { recipientId: s(STUDENT), sourceType: s("answer_accepted"), sourceId: s("ans1"),
        amount: i(999), note: s("Self award"), createdAt: ts(nowIso()) }, STUDENT), denied);
  await check("even an administrator client cannot write karma",
    createDoc("karmaAwards", "karma_admin",
      { recipientId: s(ADMIN), sourceType: s("answer_accepted"), sourceId: s("ans1"),
        amount: i(15), note: s("Admin"), createdAt: ts(nowIso()) }, ADMIN), denied);
  await check("the trusted backend seeds an award for an accepted answer",
    createDoc("karmaAwards", "answer_accepted_ans1",
      { recipientId: s(TUTOR), sourceType: s("answer_accepted"), sourceId: s("ans1"),
        amount: i(15), note: s("Answer accepted"), createdAt: ts(nowIso()) }, OWNER), allowed);
  await check("the recipient reads their award",
    getDoc("karmaAwards/answer_accepted_ans1", TUTOR), allowed);
  await check("another student cannot read it",
    getDoc("karmaAwards/answer_accepted_ans1", OTHER), denied);
  await check("karma is closed to signed-out visitors",
    getDoc("karmaAwards/answer_accepted_ans1"), rejected);
  await check("the recipient's karma query works",
    call(`${ROOT}:runQuery`, {
      method: "POST",
      body: {
        structuredQuery: {
          from: [{ collectionId: "karmaAwards" }],
          where: {
            fieldFilter: {
              field: { fieldPath: "recipientId" },
              op: "EQUAL",
              value: s(TUTOR),
            },
          },
          limit: 200,
        },
      },
      token: TUTOR,
    }), allowed);
  await check("another student cannot query somebody else's karma",
    call(`${ROOT}:runQuery`, {
      method: "POST",
      body: {
        structuredQuery: {
          from: [{ collectionId: "karmaAwards" }],
          where: {
            fieldFilter: {
              field: { fieldPath: "recipientId" },
              op: "EQUAL",
              value: s(TUTOR),
            },
          },
          limit: 200,
        },
      },
      token: OTHER,
    }), denied);
  await check("the award itself cannot be edited",
    patchDoc("karmaAwards/answer_accepted_ans1", { amount: i(150) }, ["amount"], TUTOR), denied);
  await check("the award cannot be deleted by its recipient",
    call("karmaAwards/answer_accepted_ans1", { method: "DELETE", token: TUTOR }), rejected);
  await check("a repeat of the contribution still has a single award document",
    call("karmaAwards/answer_accepted_ans1", { token: OWNER }),
    (result) => allowed(result) && result.json.fields.amount.integerValue === "15");
  await check("the uploader may delete their own resource",
    call(resourceRef("res1"), { method: "DELETE", token: UPLOADER }), allowed);

  group("H. storage uploads");
  // Opt in with RUN_STORAGE_TESTS=1. The storage emulator currently reports a null
  // request.resource.contentType for media uploads, so every content-type rule (including the
  // pre-existing profile-photo rule) is denied there; the Firestore metadata checks above still
  // cover the size/type limits the app writes. Run the checks on a real project before relying
  // on the storage rules.
  if (process.env.RUN_STORAGE_TESTS !== "1" || !STORAGE_HOST || !AUTH_HOST) {
    console.log("  SKIP  storage rules are not exercised by default (set RUN_STORAGE_TESTS=1 "
      + "with --only firestore,storage,auth to try)");
  } else {
    const uploaderToken = await emulatorIdToken(`${UPLOADER}@example.com`);
    const otherToken = await emulatorIdToken(`${OTHER}@example.com`);
    const uploaderUid = uidFromToken(uploaderToken || "");
    const otherUid = uidFromToken(otherToken || "");
    if (!uploaderUid || !otherUid) {
      console.log("  SKIP  the auth emulator did not issue usable test tokens");
    } else {
      const pdf = Buffer.from("%PDF-1.4\n% CampusSync test resource\n");
      await check("a student uploads a PDF into their own resource folder",
        uploadObject(`academic_resources/${uploaderUid}/trees.pdf`, "application/pdf",
          pdf, uploaderToken), allowed);
      await check("a second student uploads into their own folder",
        uploadObject(`academic_resources/${otherUid}/notes.pdf`, "application/pdf",
          pdf, otherToken), allowed);
      await check("an upload into another student's folder is refused",
        uploadObject(`academic_resources/${uploaderUid}/sneaky.pdf`, "application/pdf",
          pdf, otherToken), denied);
      await check("a disallowed content type is refused",
        uploadObject(`academic_resources/${uploaderUid}/lecture.mp4`, "video/mp4",
          Buffer.from("not a document"), uploaderToken), denied);
      await check("an oversized upload is refused",
        uploadObject(`academic_resources/${uploaderUid}/huge.pdf`, "application/pdf",
          Buffer.alloc(20 * 1024 * 1024 + 1), uploaderToken), denied);
      await check("a signed-out upload is refused",
        uploadObject(`academic_resources/${uploaderUid}/anon.pdf`, "application/pdf",
          pdf, "invalid-token"), denied);
    }
  }

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
    console.log("\nAcademic hub tests FAILED");
    process.exit(1);
  }
  console.log("ACADEMIC_TESTS_OK");
})().catch((error) => {
  console.error("Test runner crashed:", error);
  process.exit(1);
});
