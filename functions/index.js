/**
 * CampusSync trusted backend.
 *
 *   cd functions && npm install
 *   firebase deploy --only functions
 *
 * 1. expireStories        – hourly cleanup of stories older than 24 hours.
 * 2. notifyNewMessage     – pushes a new chat message to every other member's devices, skipping
 *                           muted threads and members who turned message notifications off.
 * 3. notifyGroupInvite    – pushes an invitation to members newly added to a group.
 *
 * Authorisation is decided before anything is sent: the trigger reads the conversation's member
 * rows, so a message that somehow exists outside a member's access is never broadcast to them.
 */
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const {
  answerAcceptedAward,
  resourceHelpfulAward,
  tutorReviewAward,
} = require("./karma");

initializeApp();

const STORY_TTL_MS = 24 * 60 * 60 * 1000;

/**
 * Writes one award to the read-only karma ledger. The document id is derived from the
 * contribution, so a rerun (or a duplicated trigger) finds the document already there and does
 * nothing: one contribution can only ever be rewarded once.
 */
async function writeKarma(db, award) {
  if (!award) return false;
  const reference = db.collection("karmaAwards").doc(award.awardId);
  try {
    return await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (snapshot.exists) return false;
      transaction.set(reference, {
        recipientId: award.recipientId,
        sourceType: award.sourceType,
        sourceId: award.sourceId,
        amount: award.amount,
        note: award.note,
        createdAt: FieldValue.serverTimestamp(),
      });
      return true;
    });
  } catch (error) {
    console.error("Karma award failed", award.awardId, error);
    return false;
  }
}
const MESSAGE_CHANNEL = "campus_sync_messages";
const EVENT_CHANNEL = "campus_sync_events";

exports.expireStories = onSchedule("every 60 minutes", async () => {
  const db = getFirestore();
  const cutoff = new Date(Date.now() - STORY_TTL_MS);

  // Loop so a backlog larger than one page is cleaned up in the same run.
  for (;;) {
    const expired = await db
      .collection("stories")
      .where("createdAt", "<", cutoff)
      .limit(200)
      .get();

    if (expired.empty) break;

    for (const document of expired.docs) {
      // recursiveDelete removes views/ and replies/ with the story document.
      await db.recursiveDelete(document.ref);
    }
  }

  console.log(`Story cleanup finished (cutoff ${cutoff.toISOString()}).`);
});

/** Sends one payload to every token, in the 500-recipient batches FCM allows. */
async function sendInChunks(tokens, payload) {
  for (let index = 0; index < tokens.length; index += 500) {
    const chunk = tokens.slice(index, index + 500);
    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens: chunk,
        ...payload,
      });
      console.log(`Sent ${response.successCount}/${chunk.length} notifications.`);
    } catch (error) {
      console.error("Notification batch failed", error);
    }
  }
}

function messageBody(message) {
  if (message.type === "image") return "📷 Photo";
  if (message.type === "document") return `📎 ${message.attachmentName || "Document"}`;
  return message.text || "New message";
}

/**
 * Karma for an accepted answer. The answer is read from Firestore rather than trusted from the
 * doubt document, and the asker's own answer is never rewarded.
 */
exports.awardAnswerKarma = onDocumentUpdated("doubts/{doubtId}", async (event) => {
  const after = event.data.after.data() || {};
  const before = event.data.before.data() || {};
  if (!after.solved || !after.acceptedAnswerId) return;
  if (before.acceptedAnswerId === after.acceptedAnswerId) return;

  const db = getFirestore();
  const answer = await event.data.after.ref
    .collection("answers")
    .doc(after.acceptedAnswerId)
    .get();
  if (!answer.exists) return;

  const award = answerAcceptedAward({
    doubtId: event.params.doubtId,
    answerId: after.acceptedAnswerId,
    answerAuthorId: answer.get("authorId"),
    doubtAuthorId: after.authorId,
    doubtTitle: after.title,
  });
  if (await writeKarma(db, award)) {
    console.log(`Karma awarded for accepted answer ${after.acceptedAnswerId}.`);
  }
});

/** Karma for a resource that reached the helpfulness threshold. */
exports.awardResourceKarma = onDocumentUpdated("resources/{resourceId}", async (event) => {
  const after = event.data.after.data() || {};
  const award = resourceHelpfulAward({
    resourceId: event.params.resourceId,
    uploaderId: after.uploaderId,
    ratingSum: after.ratingSum,
    ratingCount: after.ratingCount,
    title: after.title,
  });
  if (await writeKarma(getFirestore(), award)) {
    console.log(`Karma awarded for resource ${event.params.resourceId}.`);
  }
});

/** Karma for a completed tutoring session that was reviewed well. */
exports.awardTutorReviewKarma = onDocumentUpdated(
  "tutoringRequests/{requestId}",
  async (event) => {
    const after = event.data.after.data() || {};
    const award = tutorReviewAward({
      requestId: event.params.requestId,
      tutorId: after.tutorId,
      studentId: after.studentId,
      status: after.status,
      reviewRating: after.reviewRating,
    });
    if (await writeKarma(getFirestore(), award)) {
      console.log(`Karma awarded for tutoring request ${event.params.requestId}.`);
    }
  }
);

exports.notifyNewMessage = onDocumentCreated(
  "conversations/{conversationId}/messages/{messageId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const message = snapshot.data() || {};
    if (!message.senderId) return;

    const db = getFirestore();
    const conversationRef = db.collection("conversations").doc(event.params.conversationId);
    const [conversation, members] = await Promise.all([
      conversationRef.get(),
      conversationRef.collection("members").get(),
    ]);
    if (!conversation.exists) return;

    const isGroup = conversation.get("type") === "group";
    const tokens = [];
    for (const memberDoc of members.docs) {
      if (memberDoc.id === message.senderId) continue;
      const member = memberDoc.data() || {};
      if (member.muted === true) continue;
      const user = await db.collection("users").doc(memberDoc.id).get();
      if (!user.exists) continue;
      if (user.get("notifyMessages") === false) continue;
      for (const token of user.get("fcmTokens") || []) {
        if (token) tokens.push(token);
      }
    }
    if (tokens.length === 0) return;

    const title = isGroup
      ? conversation.get("title") || "Group chat"
      : message.senderName || "New message";

    await sendInChunks(tokens, {
      notification: { title, body: messageBody(message) },
      data: {
        category: "message",
        screen: "chat",
        conversationId: event.params.conversationId,
      },
      android: {
        priority: "high",
        notification: { channelId: MESSAGE_CHANNEL },
      },
    });
  }
);

exports.notifyGroupInvite = onDocumentUpdated(
  "conversations/{conversationId}",
  async (event) => {
    const before = (event.data.before.data() || {});
    const after = (event.data.after.data() || {});
    if (after.type !== "group") return;

    const beforeIds = before.memberIds || [];
    const added = (after.memberIds || []).filter((uid) => !beforeIds.includes(uid));
    if (added.length === 0) return;

    const db = getFirestore();
    const title = after.title || "a group";
    for (const uid of added) {
      const user = await db.collection("users").doc(uid).get();
      if (!user.exists || user.get("notifyGroups") === false) continue;
      const tokens = (user.get("fcmTokens") || []).filter(Boolean);
      if (tokens.length === 0) continue;
      await sendInChunks(tokens, {
        notification: {
          title: "Group invitation",
          body: `You were added to ${title}`,
        },
        data: {
          category: "groupInvite",
          screen: "chat",
          conversationId: event.params.conversationId,
        },
        android: {
          priority: "high",
          notification: { channelId: MESSAGE_CHANNEL },
        },
      });
    }
  }
);

// ---------------------------------------------------------------- events

/** Pushes one payload to every device of a user, honouring their notification preference. */
async function pushToUser(db, uid, payload, preferenceKey) {
  if (!uid) return;
  const user = await db.collection("users").doc(uid).get();
  if (!user.exists) return;
  if (preferenceKey && user.get(preferenceKey) === false) return;
  const tokens = (user.get("fcmTokens") || []).filter(Boolean);
  if (tokens.length === 0) return;
  await sendInChunks(tokens, payload);
}

/** Uids holding a live (not cancelled) registration for the event. */
async function registeredUids(db, eventId) {
  const snapshot = await db
    .collection("events")
    .doc(eventId)
    .collection("registrations")
    .get();
  return snapshot.docs
    .filter((document) => document.get("status") !== "cancelled")
    .map((document) => document.id);
}

function eventWhen(eventDocument) {
  const start = eventDocument.get("startDate");
  if (!start || typeof start.toDate !== "function") return "";
  return start.toDate().toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
  });
}

function eventPayload(eventId, title, body) {
  return {
    notification: { title, body },
    data: { category: "event", screen: "event", eventId },
    android: {
      priority: "high",
      notification: { channelId: EVENT_CHANNEL },
    },
  };
}

/** Registration confirmation for the participant and an organizer notification for the host. */
exports.notifyEventRegistration = onDocumentCreated(
  "events/{eventId}/registrations/{uid}",
  async (event) => {
    const registration = event.data ? event.data.data() || {} : {};
    if (registration.status === "cancelled") return;
    const db = getFirestore();
    const eventDocument = await db.collection("events").doc(event.params.eventId).get();
    if (!eventDocument.exists) return;

    const title = eventDocument.get("title") || "Event";
    const when = eventWhen(eventDocument);
    const start = eventDocument.get("startDate");
    const isUpcoming = start && typeof start.toMillis === "function"
      ? start.toMillis() > Date.now()
      : false;

    await pushToUser(
      db,
      event.params.uid,
      eventPayload(
        event.params.eventId,
        "Registration confirmed",
        isUpcoming && when ? `${title} · ${when}` : title
      ),
      "notifyEvents"
    );

    const organizerId = eventDocument.get("organizerId");
    if (organizerId && organizerId !== event.params.uid) {
      await pushToUser(
        db,
        organizerId,
        eventPayload(
          event.params.eventId,
          "New registration",
          `${registration.name || "A student"} registered for ${title}`
        ),
        "notifyEvents"
      );
    }
  }
);

/** Schedule, venue, title or cancellation changes are pushed to every registrant. */
exports.notifyEventChanged = onDocumentUpdated(
  "events/{eventId}",
  async (event) => {
    const before = event.data.before.data() || {};
    const after = event.data.after.data() || {};
    const changedFields = ["title", "startDate", "endDate", "location", "status"].filter(
      (field) => String(before[field]) !== String(after[field])
    );
    if (changedFields.length === 0) return;

    const db = getFirestore();
    const cancelled = after.status === "cancelled" && before.status !== "cancelled";
    const title = after.title || "Event";
    const uids = await registeredUids(db, event.params.eventId);
    if (uids.length === 0) return;

    for (const uid of uids) {
      await pushToUser(
        db,
        uid,
        eventPayload(
          event.params.eventId,
          cancelled ? "Event cancelled" : "Event updated",
          cancelled ? `${title} was cancelled` : `${title} was updated — check the details`
        ),
        "notifyEvents"
      );
    }
  }
);

/** A note posted by the organizer notifies every registrant. */
exports.notifyEventUpdate = onDocumentCreated(
  "events/{eventId}/updates/{updateId}",
  async (event) => {
    const update = event.data ? event.data.data() || {} : {};
    const db = getFirestore();
    const eventDocument = await db.collection("events").doc(event.params.eventId).get();
    if (!eventDocument.exists) return;
    const eventTitle = eventDocument.get("title") || "Event";
    const uids = await registeredUids(db, event.params.eventId);

    for (const uid of uids) {
      await pushToUser(
        db,
        uid,
        eventPayload(
          event.params.eventId,
          `${eventTitle} update`,
          update.title || update.body || "The organizer posted an update"
        ),
        "notifyEvents"
      );
    }
  }
);

/** Hourly reminder for events starting in the next 24 hours; `reminderSentAt` keeps it once. */
exports.sendEventReminders = onSchedule("every 60 minutes", async () => {
  const db = getFirestore();
  const now = new Date();
  const soon = new Date(now.getTime() + 24 * 60 * 60 * 1000);
  const upcoming = await db
    .collection("events")
    .where("startDate", ">", now)
    .where("startDate", "<", soon)
    .limit(200)
    .get();

  for (const eventDocument of upcoming.docs) {
    const data = eventDocument.data() || {};
    if (data.status === "cancelled") continue;
    if (data.reminderSentAt) continue;

    const uids = await registeredUids(db, eventDocument.id);
    for (const uid of uids) {
      await pushToUser(
        db,
        uid,
        eventPayload(
          eventDocument.id,
          "Event reminder",
          `${data.title || "Your event"} starts ${eventWhen(eventDocument)}`
        ),
        "notifyEvents"
      );
    }
    await eventDocument.ref.update({ reminderSentAt: FieldValue.serverTimestamp() });
  }
});
