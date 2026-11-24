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
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const STORY_TTL_MS = 24 * 60 * 60 * 1000;
const MESSAGE_CHANNEL = "campus_sync_messages";

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
