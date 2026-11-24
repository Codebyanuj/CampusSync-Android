package com.campussync.app.firebase;

import android.net.Uri;
import android.util.Log;

import com.campussync.app.model.Conversation;
import com.campussync.app.model.ConversationMember;
import com.campussync.app.model.ContentReport;
import com.campussync.app.model.Message;
import com.campussync.app.model.UserProfile;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore / Storage access for private and group messaging.
 *
 * <p>Security model (enforced by {@code firestore.rules}, never by this class alone):
 * <ul>
 *   <li>every read and write on a thread requires a
 *       {@code conversations/{id}/members/{uid}} document for the caller;</li>
 *   <li>group membership changes, renaming and the group photo require an admin member role;</li>
 *   <li>a direct message cannot be created while either participant has blocked the other;</li>
 *   <li>attachments are readable only by members of the conversation they were posted to.</li>
 * </ul>
 */
public class MessagingRepository {

    private static final String TAG = "MessagingRepository";

    public static final String COLLECTION_CONVERSATIONS = "conversations";
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_REPORTS = "reports";

    public static final String SUB_MEMBERS = "members";
    public static final String SUB_MESSAGES = "messages";
    public static final String SUB_TYPING = "typing";
    public static final String SUB_BLOCKED = "blocked";

    public static final int MESSAGE_PAGE_SIZE = 25;
    private static final int CONVERSATION_LIMIT = 60;
    private static final long MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024;
    /** A typing marker older than this is ignored by the UI. */
    public static final long TYPING_WINDOW_MS = 6000L;

    private static volatile MessagingRepository instance;

    private final FirebaseFirestore db;

    private MessagingRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static MessagingRepository getInstance() {
        if (instance == null) {
            synchronized (MessagingRepository.class) {
                if (instance == null) instance = new MessagingRepository();
            }
        }
        return instance;
    }

    public static String currentUidOrNull() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    private com.google.firebase.firestore.CollectionReference conversations() {
        return db.collection(COLLECTION_CONVERSATIONS);
    }

    private com.google.firebase.firestore.CollectionReference messages(String conversationId) {
        return conversations().document(conversationId).collection(SUB_MESSAGES);
    }

    // ------------------------------------------------------------------ conversations

    /**
     * Live list of the signed-in user's threads. The query only filters on membership; ordering
     * and decoration happen on the client so no composite index is required.
     */
    public ListenerRegistration listenToConversations(final ConversationsListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onConversations(new ArrayList<>(), null);
            return null;
        }
        return conversations()
                .whereArrayContains("memberIds", uid)
                .limit(CONVERSATION_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToConversations: failure", error);
                        if (listener != null) listener.onConversations(null, error);
                        return;
                    }
                    List<Conversation> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Conversation conversation = toConversation(document, uid);
                            if (conversation != null) list.add(conversation);
                        }
                    }
                    Collections.sort(list, new Comparator<Conversation>() {
                        @Override
                        public int compare(Conversation left, Conversation right) {
                            Date l = left.getActivityAt();
                            Date r = right.getActivityAt();
                            if (l == null && r == null) return 0;
                            if (l == null) return 1;
                            if (r == null) return -1;
                            return r.compareTo(l);
                        }
                    });
                    if (listener != null) listener.onConversations(list, null);
                });
    }

    /** Total unread messages across every thread, for the bottom-bar badge. */
    public ListenerRegistration listenToUnreadTotal(final UnreadListener listener) {
        return listenToConversations((conversations, error) -> {
            if (listener == null) return;
            if (error != null) {
                listener.onUnread(0);
                return;
            }
            long total = 0;
            if (conversations != null) {
                for (Conversation conversation : conversations) {
                    total += Math.max(0, conversation.getUnreadCount());
                }
            }
            listener.onUnread(total);
        });
    }

    /** Live single conversation, used by the chat header and the group info screen. */
    public ListenerRegistration listenToConversation(String conversationId,
                                                     final ConversationListener listener) {
        final String uid = currentUidOrNull();
        if (conversationId == null || uid == null) {
            if (listener != null) listener.onConversation(null, null);
            return null;
        }
        return conversations().document(conversationId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToConversation: failure", error);
                        if (listener != null) listener.onConversation(null, error);
                        return;
                    }
                    Conversation conversation = snapshot != null && snapshot.exists()
                            ? toConversation(snapshot, uid) : null;
                    if (listener != null) listener.onConversation(conversation, null);
                });
    }

    /** Opens (or creates) the one-to-one thread with another user. */
    public void getOrCreateDirectConversation(final UserProfile other,
                                              final ConversationListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || other == null || other.getId() == null) {
            if (listener != null) {
                listener.onConversation(null, new IllegalStateException("Missing conversation partner"));
            }
            return;
        }
        if (uid.equals(other.getId())) {
            if (listener != null) {
                listener.onConversation(null, new IllegalArgumentException("You cannot message yourself"));
            }
            return;
        }
        final String directKey = uid.compareTo(other.getId()) < 0
                ? uid + "_" + other.getId()
                : other.getId() + "_" + uid;

        conversations().whereEqualTo("directKey", directKey).limit(1).get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots != null && !snapshots.isEmpty()) {
                        Conversation existing = toConversation(snapshots.getDocuments().get(0), uid);
                        if (listener != null) listener.onConversation(existing, null);
                        return;
                    }
                    UserRepository.getInstance().getProfile(uid, (me, profileError) -> {
                        if (profileError != null || me == null) {
                            if (listener != null) listener.onConversation(null, profileError);
                            return;
                        }
                        createDirect(uid, me, other, directKey, listener);
                    });
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "getOrCreateDirectConversation: lookup failed", error);
                    if (listener != null) listener.onConversation(null, error);
                });
    }

    private void createDirect(final String uid, UserProfile me, UserProfile other,
                              String directKey, final ConversationListener listener) {
        DocumentReference reference = conversations().document();
        Map<String, String> names = new HashMap<>();
        names.put(uid, displayName(me));
        names.put(other.getId(), displayName(other));
        Map<String, String> photos = new HashMap<>();
        if (me.getPhotoUrl() != null) photos.put(uid, me.getPhotoUrl());
        if (other.getPhotoUrl() != null) photos.put(other.getId(), other.getPhotoUrl());

        Map<String, Object> fields = new HashMap<>();
        fields.put("type", Conversation.TYPE_DIRECT);
        fields.put("memberIds", java.util.Arrays.asList(uid, other.getId()));
        fields.put("adminIds", new ArrayList<String>());
        fields.put("memberNames", names);
        fields.put("memberPhotos", photos);
        fields.put("unreadCounts", new HashMap<String, Long>());
        fields.put("ownerId", uid);
        fields.put("directKey", directKey);
        fields.put("directA", uid.compareTo(other.getId()) < 0 ? uid : other.getId());
        fields.put("directB", uid.compareTo(other.getId()) < 0 ? other.getId() : uid);
        fields.put("memberCount", 2);
        fields.put("createdAt", FieldValue.serverTimestamp());
        fields.put("updatedAt", FieldValue.serverTimestamp());

        reference.set(fields)
                .addOnSuccessListener(unused -> {
                    // The creator's own member row first: every later write needs it to exist.
                    createMember(reference, uid, displayName(me), me.getPhotoUrl(),
                            Conversation.ROLE_MEMBER)
                            .addOnSuccessListener(done -> createMember(reference, other.getId(),
                                    displayName(other), other.getPhotoUrl(),
                                    Conversation.ROLE_MEMBER)
                                    .addOnSuccessListener(second -> {
                                        Conversation conversation = new Conversation();
                                        conversation.setId(reference.getId());
                                        conversation.setType(Conversation.TYPE_DIRECT);
                                        conversation.setMemberIds(
                                                java.util.Arrays.asList(uid, other.getId()));
                                        conversation.setOtherUserId(other.getId());
                                        conversation.setDisplayTitle(displayName(other));
                                        conversation.setDisplayPhotoUrl(other.getPhotoUrl());
                                        if (listener != null) {
                                            listener.onConversation(conversation, null);
                                        }
                                    })
                                    .addOnFailureListener(error -> {
                                        Log.w(TAG, "createDirect: member write failed", error);
                                        if (listener != null) listener.onConversation(null, error);
                                    }))
                            .addOnFailureListener(error -> {
                                Log.w(TAG, "createDirect: member write failed", error);
                                if (listener != null) listener.onConversation(null, error);
                            });
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "createDirect: create failed", error);
                    if (listener != null) listener.onConversation(null, error);
                });
    }

    /** Creates a group chat owned and administered by the signed-in user. */
    public void createGroup(final String title, final String photoUrl,
                            final List<UserProfile> members, final ConversationListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onConversation(null, new IllegalStateException("Not signed in"));
            return;
        }
        if (members == null || members.isEmpty()) {
            if (listener != null) {
                listener.onConversation(null, new IllegalArgumentException("Add at least one member"));
            }
            return;
        }
        UserRepository.getInstance().getProfile(uid, (me, profileError) -> {
            if (profileError != null || me == null) {
                if (listener != null) listener.onConversation(null, profileError);
                return;
            }
            DocumentReference reference = conversations().document();
            List<String> memberIds = new ArrayList<>();
            memberIds.add(uid);
            Map<String, String> names = new HashMap<>();
            Map<String, String> photos = new HashMap<>();
            names.put(uid, displayName(me));
            if (me.getPhotoUrl() != null) photos.put(uid, me.getPhotoUrl());
            for (UserProfile member : members) {
                if (member.getId() == null || memberIds.contains(member.getId())) continue;
                memberIds.add(member.getId());
                names.put(member.getId(), displayName(member));
                if (member.getPhotoUrl() != null) photos.put(member.getId(), member.getPhotoUrl());
            }
            if (memberIds.size() < 2) {
                if (listener != null) {
                    listener.onConversation(null, new IllegalArgumentException("Add at least one member"));
                }
                return;
            }

            Map<String, Object> fields = new HashMap<>();
            fields.put("type", Conversation.TYPE_GROUP);
            fields.put("title", title != null ? title.trim() : "");
            fields.put("photoUrl", photoUrl);
            fields.put("memberIds", memberIds);
            fields.put("adminIds", Collections.singletonList(uid));
            fields.put("memberNames", names);
            fields.put("memberPhotos", photos);
            fields.put("unreadCounts", new HashMap<String, Long>());
            fields.put("ownerId", uid);
            fields.put("memberCount", (long) memberIds.size());
            fields.put("createdAt", FieldValue.serverTimestamp());
            fields.put("updatedAt", FieldValue.serverTimestamp());

            reference.set(fields)
                    .addOnSuccessListener(unused -> createMember(reference, uid,
                            displayName(me), me.getPhotoUrl(), Conversation.ROLE_ADMIN)
                            .addOnSuccessListener(done -> {
                                WriteBatch batch = db.batch();
                                for (UserProfile member : members) {
                                    if (member.getId() == null || uid.equals(member.getId())) continue;
                                    batch.set(reference.collection(SUB_MEMBERS)
                                                    .document(member.getId()),
                                            memberFields(member.getId(), displayName(member),
                                                    member.getPhotoUrl(), Conversation.ROLE_MEMBER));
                                }
                                batch.commit()
                                        .addOnSuccessListener(committed -> {
                                            Conversation conversation = new Conversation();
                                            conversation.setId(reference.getId());
                                            conversation.setType(Conversation.TYPE_GROUP);
                                            conversation.setTitle(title);
                                            conversation.setPhotoUrl(photoUrl);
                                            conversation.setMemberIds(memberIds);
                                            conversation.setDisplayTitle(title);
                                            conversation.setDisplayPhotoUrl(photoUrl);
                                            if (listener != null) {
                                                listener.onConversation(conversation, null);
                                            }
                                        })
                                        .addOnFailureListener(error -> {
                                            Log.w(TAG, "createGroup: members failed", error);
                                            if (listener != null) {
                                                listener.onConversation(null, error);
                                            }
                                        });
                            })
                            .addOnFailureListener(error -> {
                                Log.w(TAG, "createGroup: member write failed", error);
                                if (listener != null) listener.onConversation(null, error);
                            }))
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "createGroup: create failed", error);
                        if (listener != null) listener.onConversation(null, error);
                    });
        });
    }

    private Task<Void> createMember(DocumentReference conversation, String uid, String name,
                                    String photoUrl, String role) {
        return conversation.collection(SUB_MEMBERS).document(uid)
                .set(memberFields(uid, name, photoUrl, role));
    }

    private Map<String, Object> memberFields(String uid, String name, String photoUrl, String role) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        fields.put("photoUrl", photoUrl);
        fields.put("role", role);
        fields.put("muted", false);
        fields.put("unreadCount", 0L);
        fields.put("joinedAt", FieldValue.serverTimestamp());
        return fields;
    }

    // ------------------------------------------------------------------ group administration

    /** Adds members to a group. Only a group admin may do this (enforced by the rules). */
    public void addMembers(final String conversationId, final List<UserProfile> users,
                           final AuthManager.ActionListener listener) {
        if (conversationId == null || users == null || users.isEmpty()) {
            if (listener != null) listener.onComplete(null);
            return;
        }
        final DocumentReference reference = conversations().document(conversationId);
        reference.get().addOnSuccessListener(snapshot -> {
            if (snapshot == null || !snapshot.exists()) {
                if (listener != null) listener.onComplete(new IllegalStateException("Group not found"));
                return;
            }
            List<String> memberIds = asStringList(snapshot.get("memberIds"));
            Map<String, String> names = asStringMap(snapshot.get("memberNames"));
            Map<String, String> photos = asStringMap(snapshot.get("memberPhotos"));

            List<String> added = new ArrayList<>();
            for (UserProfile user : users) {
                if (user.getId() == null || memberIds.contains(user.getId())) continue;
                memberIds.add(user.getId());
                added.add(user.getId());
                names.put(user.getId(), displayName(user));
                if (user.getPhotoUrl() != null) photos.put(user.getId(), user.getPhotoUrl());
            }
            if (added.isEmpty()) {
                if (listener != null) listener.onComplete(null);
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("memberIds", memberIds);
            updates.put("memberNames", names);
            updates.put("memberPhotos", photos);
            updates.put("memberCount", (long) memberIds.size());
            updates.put("updatedAt", FieldValue.serverTimestamp());

            reference.update(updates)
                    .addOnSuccessListener(unused -> {
                        WriteBatch batch = db.batch();
                        for (UserProfile user : users) {
                            if (user.getId() == null || !added.contains(user.getId())) continue;
                            batch.set(reference.collection(SUB_MEMBERS).document(user.getId()),
                                    memberFields(user.getId(), displayName(user),
                                            user.getPhotoUrl(), Conversation.ROLE_MEMBER));
                        }
                        batch.commit()
                                .addOnSuccessListener(done -> {
                                    if (listener != null) listener.onComplete(null);
                                })
                                .addOnFailureListener(error -> {
                                    Log.w(TAG, "addMembers: member docs failed", error);
                                    if (listener != null) listener.onComplete(error);
                                });
                    })
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "addMembers: update failed", error);
                        if (listener != null) listener.onComplete(error);
                    });
        }).addOnFailureListener(error -> {
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Removes a member from a group; only an admin may do this. */
    public void removeMember(final String conversationId, final String memberId,
                             final AuthManager.ActionListener listener) {
        DocumentReference reference = conversations().document(conversationId);
        reference.get().addOnSuccessListener(snapshot -> {
            if (snapshot == null || !snapshot.exists()) {
                if (listener != null) listener.onComplete(new IllegalStateException("Group not found"));
                return;
            }
            List<String> memberIds = asStringList(snapshot.get("memberIds"));
            List<String> adminIds = asStringList(snapshot.get("adminIds"));
            Map<String, String> names = asStringMap(snapshot.get("memberNames"));
            Map<String, String> photos = asStringMap(snapshot.get("memberPhotos"));
            memberIds.remove(memberId);
            adminIds.remove(memberId);
            names.remove(memberId);
            photos.remove(memberId);

            Map<String, Object> updates = new HashMap<>();
            updates.put("memberIds", memberIds);
            updates.put("adminIds", adminIds);
            updates.put("memberNames", names);
            updates.put("memberPhotos", photos);
            updates.put("memberCount", (long) memberIds.size());
            updates.put("updatedAt", FieldValue.serverTimestamp());

            reference.update(updates)
                    .addOnSuccessListener(unused -> reference.collection(SUB_MEMBERS)
                            .document(memberId).delete()
                            .addOnSuccessListener(done -> {
                                if (listener != null) listener.onComplete(null);
                            })
                            .addOnFailureListener(error -> {
                                if (listener != null) listener.onComplete(error);
                            }))
                    .addOnFailureListener(error -> {
                        if (listener != null) listener.onComplete(error);
                    });
        }).addOnFailureListener(error -> {
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Promotes or demotes a group member. */
    public void setMemberAdmin(String conversationId, String memberId, boolean admin,
                               AuthManager.ActionListener listener) {
        DocumentReference reference = conversations().document(conversationId);
        reference.get().addOnSuccessListener(snapshot -> {
            if (snapshot == null || !snapshot.exists()) {
                if (listener != null) listener.onComplete(new IllegalStateException("Group not found"));
                return;
            }
            List<String> adminIds = asStringList(snapshot.get("adminIds"));
            if (admin) {
                if (!adminIds.contains(memberId)) adminIds.add(memberId);
            } else {
                adminIds.remove(memberId);
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("adminIds", adminIds);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            reference.update(updates)
                    .addOnSuccessListener(unused -> reference.collection(SUB_MEMBERS)
                            .document(memberId)
                            .set(Collections.<String, Object>singletonMap("role", admin
                                    ? Conversation.ROLE_ADMIN : Conversation.ROLE_MEMBER),
                                    SetOptions.merge())
                            .addOnSuccessListener(done -> {
                                if (listener != null) listener.onComplete(null);
                            })
                            .addOnFailureListener(error -> {
                                if (listener != null) listener.onComplete(error);
                            }))
                    .addOnFailureListener(error -> {
                        if (listener != null) listener.onComplete(error);
                    });
        }).addOnFailureListener(error -> {
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Leaves a group: the member row goes first, then the membership arrays. */
    public void leaveGroup(final String conversationId, final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference reference = conversations().document(conversationId);
        reference.get().addOnSuccessListener(snapshot -> {
            if (snapshot == null || !snapshot.exists()) {
                if (listener != null) listener.onComplete(null);
                return;
            }
            List<String> memberIds = asStringList(snapshot.get("memberIds"));
            List<String> adminIds = asStringList(snapshot.get("adminIds"));
            Map<String, String> names = asStringMap(snapshot.get("memberNames"));
            Map<String, String> photos = asStringMap(snapshot.get("memberPhotos"));
            memberIds.remove(uid);
            adminIds.remove(uid);
            names.remove(uid);
            photos.remove(uid);

            Map<String, Object> updates = new HashMap<>();
            updates.put("memberIds", memberIds);
            updates.put("adminIds", adminIds);
            updates.put("memberNames", names);
            updates.put("memberPhotos", photos);
            updates.put("memberCount", (long) memberIds.size());
            updates.put("updatedAt", FieldValue.serverTimestamp());

            reference.update(updates)
                    .addOnSuccessListener(unused -> reference.collection(SUB_MEMBERS)
                            .document(uid).delete()
                            .addOnSuccessListener(done -> {
                                if (listener != null) listener.onComplete(null);
                            })
                            .addOnFailureListener(error -> {
                                if (listener != null) listener.onComplete(error);
                            }))
                    .addOnFailureListener(error -> {
                        if (listener != null) listener.onComplete(error);
                    });
        }).addOnFailureListener(error -> {
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Renames a group and optionally replaces its photo. */
    public void updateGroupInfo(String conversationId, String title, String photoUrl,
                                AuthManager.ActionListener listener) {
        Map<String, Object> updates = new HashMap<>();
        if (title != null) updates.put("title", title.trim());
        if (photoUrl != null) updates.put("photoUrl", photoUrl);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        conversations().document(conversationId).update(updates)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "updateGroupInfo: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Members of a group, ordered by role and name. */
    public ListenerRegistration listenToMembers(String conversationId,
                                                final MembersListener listener) {
        if (conversationId == null) {
            if (listener != null) listener.onMembers(new ArrayList<>(), null);
            return null;
        }
        return conversations().document(conversationId).collection(SUB_MEMBERS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToMembers: failure", error);
                        if (listener != null) listener.onMembers(null, error);
                        return;
                    }
                    List<ConversationMember> members = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            ConversationMember member =
                                    document.toObject(ConversationMember.class);
                            if (member != null) {
                                member.setUid(document.getId());
                                members.add(member);
                            }
                        }
                    }
                    Collections.sort(members, new Comparator<ConversationMember>() {
                        @Override
                        public int compare(ConversationMember left, ConversationMember right) {
                            boolean leftAdmin = left.isAdmin();
                            boolean rightAdmin = right.isAdmin();
                            if (leftAdmin != rightAdmin) return leftAdmin ? -1 : 1;
                            String ln = left.getName() != null ? left.getName() : "";
                            String rn = right.getName() != null ? right.getName() : "";
                            return ln.compareToIgnoreCase(rn);
                        }
                    });
                    if (listener != null) listener.onMembers(members, null);
                });
    }

    // ------------------------------------------------------------------ messages

    /** Live latest page of a thread, oldest first. */
    public ListenerRegistration listenToMessages(String conversationId,
                                                 final MessagesListener listener) {
        if (conversationId == null) {
            if (listener != null) listener.onMessages(new ArrayList<>(), false, null);
            return null;
        }
        return messages(conversationId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(MESSAGE_PAGE_SIZE)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToMessages: failure", error);
                        if (listener != null) listener.onMessages(null, false, error);
                        return;
                    }
                    List<Message> page = toMessages(snapshots);
                    boolean more = snapshots != null && snapshots.size() == MESSAGE_PAGE_SIZE;
                    if (listener != null) listener.onMessages(page, more, null);
                });
    }

    /** One older page of history, inserted above the loaded messages. */
    public void loadOlderMessages(String conversationId, Date before,
                                  final MessagesListener listener) {
        if (conversationId == null || before == null) {
            if (listener != null) listener.onMessages(new ArrayList<>(), false, null);
            return;
        }
        messages(conversationId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .whereLessThan("createdAt", new Timestamp(before))
                .limit(MESSAGE_PAGE_SIZE)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (listener != null) {
                        listener.onMessages(toMessages(snapshots),
                                snapshots != null && snapshots.size() == MESSAGE_PAGE_SIZE, null);
                    }
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "loadOlderMessages: failure", error);
                    if (listener != null) listener.onMessages(null, false, error);
                });
    }

    private List<Message> toMessages(com.google.firebase.firestore.QuerySnapshot snapshots) {
        List<Message> list = new ArrayList<>();
        if (snapshots != null) {
            for (DocumentSnapshot document : snapshots.getDocuments()) {
                Message message = document.toObject(Message.class);
                if (message != null) {
                    message.setId(document.getId());
                    list.add(message);
                }
            }
        }
        Collections.reverse(list);
        return list;
    }

    /**
     * Writes a message, its preview on the thread and the other members' unread counters in one
     * transaction. The rules only allow the fields this method touches.
     */
    public void sendMessage(final String conversationId, final String text, final String type,
                            final String attachmentUrl, final String attachmentName,
                            final long attachmentSize, final String mimeType,
                            final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference conversationRef = conversations().document(conversationId);
        final DocumentReference messageRef = conversationRef.collection(SUB_MESSAGES).document();
        final DocumentReference memberRef = conversationRef.collection(SUB_MEMBERS).document(uid);

        db.runTransaction(transaction -> {
            DocumentSnapshot conversation = transaction.get(conversationRef);
            if (!conversation.exists()) {
                throw new IllegalStateException("This conversation is no longer available");
            }
            DocumentSnapshot member = transaction.get(memberRef);
            List<String> memberIds = asStringList(conversation.get("memberIds"));
            if (!memberIds.contains(uid)) {
                throw new IllegalStateException("You are not a member of this conversation");
            }

            Map<String, Long> unread = new HashMap<>();
            Object raw = conversation.get("unreadCounts");
            if (raw instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                    if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                        unread.put((String) entry.getKey(),
                                ((Number) entry.getValue()).longValue());
                    }
                }
            }
            for (String memberId : memberIds) {
                if (memberId.equals(uid)) continue;
                Long previous = unread.get(memberId);
                unread.put(memberId, (previous != null ? previous : 0L) + 1L);
            }

            Map<String, Object> message = new HashMap<>();
            message.put("conversationId", conversationId);
            message.put("senderId", uid);
            message.put("senderName", member.exists() ? member.getString("name") : null);
            message.put("senderPhotoUrl", member.exists()
                    ? member.getString("photoUrl") : null);
            message.put("type", type != null ? type : Message.TYPE_TEXT);
            message.put("text", text != null ? text : "");
            message.put("createdAt", FieldValue.serverTimestamp());
            message.put("readBy", Collections.singletonList(uid));
            if (attachmentUrl != null) {
                message.put("attachmentUrl", attachmentUrl);
                message.put("attachmentName", attachmentName);
                message.put("attachmentSize", attachmentSize);
                message.put("mimeType", mimeType);
            }
            transaction.set(messageRef, message);

            Map<String, Object> update = new HashMap<>();
            update.put("lastMessage", previewFor(type, text, attachmentName));
            update.put("lastMessageAt", FieldValue.serverTimestamp());
            update.put("lastSenderId", uid);
            update.put("updatedAt", FieldValue.serverTimestamp());
            update.put("unreadCounts", unread);
            transaction.update(conversationRef, update);
            return null;
        }).addOnSuccessListener(unused -> {
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(error -> {
            Log.w(TAG, "sendMessage: failure", error);
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Clears the unread counter and marks the visible messages as read by the caller. */
    public void markConversationRead(final String conversationId, final List<Message> messages) {
        final String uid = currentUidOrNull();
        if (conversationId == null || uid == null) return;

        WriteBatch batch = db.batch();
        batch.update(conversations().document(conversationId), "unreadCounts." + uid, 0L);
        batch.set(conversations().document(conversationId).collection(SUB_MEMBERS).document(uid),
                Collections.<String, Object>singletonMap("lastReadAt",
                        FieldValue.serverTimestamp()), SetOptions.merge());

        int marked = 0;
        if (messages != null) {
            for (Message message : messages) {
                if (message == null || message.getId() == null) continue;
                if (uid.equals(message.getSenderId())) continue;
                if (message.isReadBy(uid)) continue;
                batch.update(messages(conversationId).document(message.getId()),
                        "readBy", FieldValue.arrayUnion(uid));
                if (++marked >= MESSAGE_PAGE_SIZE) break;
            }
        }
        batch.commit().addOnFailureListener(error ->
                Log.w(TAG, "markConversationRead: failure", error));
    }

    /** Publishes a typing marker for the signed-in user (or clears it). */
    public void setTyping(String conversationId, boolean typing, String displayName) {
        final String uid = currentUidOrNull();
        if (conversationId == null || uid == null) return;
        DocumentReference reference =
                conversations().document(conversationId).collection(SUB_TYPING).document(uid);
        if (!typing) {
            reference.delete().addOnFailureListener(error ->
                    Log.w(TAG, "setTyping: clear failed", error));
            return;
        }
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", displayName);
        fields.put("typingAt", FieldValue.serverTimestamp());
        reference.set(fields).addOnFailureListener(error ->
                Log.w(TAG, "setTyping: write failed", error));
    }

    /** Live typing state of the other members; stale markers are dropped by the UI. */
    public ListenerRegistration listenToTyping(String conversationId,
                                               final TypingListener listener) {
        final String uid = currentUidOrNull();
        if (conversationId == null) {
            if (listener != null) listener.onTyping(null, null);
            return null;
        }
        return conversations().document(conversationId).collection(SUB_TYPING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onTyping(null, error);
                        return;
                    }
                    String name = null;
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            if (uid != null && uid.equals(document.getId())) continue;
                            Date typingAt = document.getDate("typingAt");
                            boolean fresh = typingAt == null
                                    || System.currentTimeMillis() - typingAt.getTime()
                                       < TYPING_WINDOW_MS;
                            if (fresh) {
                                name = document.getString("name");
                                if (name != null) break;
                            }
                        }
                    }
                    if (listener != null) listener.onTyping(name, null);
                });
    }

    // ------------------------------------------------------------------ mute / block / report

    /** Mutes or unmutes the thread for the signed-in user only. */
    public void setMuted(String conversationId, boolean muted, AuthManager.ActionListener listener) {
        String uid = currentUidOrNull();
        if (conversationId == null || uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        conversations().document(conversationId).collection(SUB_MEMBERS).document(uid)
                .set(Collections.<String, Object>singletonMap("muted", muted), SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "setMuted: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Blocks a user: their direct thread disappears and neither side can send. */
    public void blockUser(String targetUid, String displayName, AuthManager.ActionListener listener) {
        String uid = currentUidOrNull();
        if (uid == null || targetUid == null || uid.equals(targetUid)) {
            if (listener != null) listener.onComplete(new IllegalArgumentException("Invalid user"));
            return;
        }
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", displayName);
        fields.put("blockedAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_USERS).document(uid).collection(SUB_BLOCKED).document(targetUid)
                .set(fields)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "blockUser: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    public void unblockUser(String targetUid, AuthManager.ActionListener listener) {
        String uid = currentUidOrNull();
        if (uid == null || targetUid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        db.collection(COLLECTION_USERS).document(uid).collection(SUB_BLOCKED).document(targetUid)
                .delete()
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Blocked users of the signed-in account, newest first. */
    public ListenerRegistration listenToBlockedUsers(final BlockedListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onBlocked(new ArrayList<>(), null);
            return null;
        }
        return db.collection(COLLECTION_USERS).document(uid).collection(SUB_BLOCKED)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onBlocked(null, error);
                        return;
                    }
                    List<BlockedUser> blocked = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            String name = document.getString("name");
                            Date when = document.getDate("blockedAt");
                            blocked.add(new BlockedUser(document.getId(),
                                    name != null ? name : "CampusSync user", when));
                        }
                    }
                    if (listener != null) listener.onBlocked(blocked, null);
                });
    }

    /** One-shot block check used before opening a direct thread. */
    public void isBlocked(String targetUid, AuthManager.ResultListener<Boolean> listener) {
        String uid = currentUidOrNull();
        if (uid == null || targetUid == null) {
            if (listener != null) listener.onComplete(false, null);
            return;
        }
        db.collection(COLLECTION_USERS).document(uid).collection(SUB_BLOCKED).document(targetUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (listener != null) {
                        listener.onComplete(snapshot != null && snapshot.exists(), null);
                    }
                })
                .addOnFailureListener(error -> {
                    if (listener != null) listener.onComplete(false, error);
                });
    }

    /** Files a moderation report for a user or a message using the shared reports collection. */
    public void report(ContentReport report, AuthManager.ActionListener listener) {
        SocialRepository.getInstance().report(report, listener);
    }

    /** Deletes a message the signed-in user sent (rules allow sender or group admin). */
    public void deleteMessage(String conversationId, String messageId,
                              AuthManager.ActionListener listener) {
        if (conversationId == null || messageId == null) {
            if (listener != null) listener.onComplete(new IllegalArgumentException("Missing message"));
            return;
        }
        messages(conversationId).document(messageId).delete()
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "deleteMessage: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /**
     * Prefix search over display names, used by the person and member pickers.
     * An empty query returns the first page of accounts so the list is never blank.
     */
    public void searchUsers(String query, final AuthManager.ResultListener<List<UserProfile>>
            listener) {
        final String uid = currentUidOrNull();
        Query reference;
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            reference = db.collection(COLLECTION_USERS).limit(30);
        } else {
            reference = db.collection(COLLECTION_USERS)
                    .orderBy("displayName")
                    .whereGreaterThanOrEqualTo("displayName", trimmed)
                    .whereLessThanOrEqualTo("displayName", trimmed + "\uf8ff")
                    .limit(30);
        }
        reference.get()
                .addOnSuccessListener(snapshots -> {
                    List<UserProfile> users = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            UserProfile profile = document.toObject(UserProfile.class);
                            if (profile == null) continue;
                            profile.setId(document.getId());
                            if (uid != null && uid.equals(profile.getId())) continue;
                            users.add(profile);
                        }
                    }
                    if (listener != null) listener.onComplete(users, null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "searchUsers: failure", error);
                    if (listener != null) listener.onComplete(null, error);
                });
    }

    // ------------------------------------------------------------------ attachments

    /** Uploads a chat attachment to {@code chat_attachments/{uid}/{conversationId}/…}. */
    public void uploadAttachment(Uri source, String conversationId, String fileNameHint,
                                 final AttachmentListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || source == null) {
            if (listener != null) {
                listener.onComplete(null, null, 0, null, new IllegalStateException("Nothing to upload"));
            }
            return;
        }
        FirebaseStorage storage = FirestoreManager.getInstance().getStorageOrNull();
        if (storage == null) {
            if (listener != null) {
                listener.onComplete(null, null, 0, null, new IllegalStateException(
                        "File upload needs Firebase Storage, which this project does not have yet."));
            }
            return;
        }
        android.content.Context context = com.google.firebase.FirebaseApp.getInstance()
                .getApplicationContext();
        String mimeType = context.getContentResolver().getType(source);
        long size = fileSize(context, source);
        if (size > MAX_ATTACHMENT_BYTES) {
            if (listener != null) {
                listener.onComplete(null, null, 0, null,
                        new IllegalArgumentException("Attachments must be smaller than 25 MB"));
            }
            return;
        }
        String fileName = sanitize(fileNameHint);
        StorageReference reference = storage.getReference()
                .child("chat_attachments").child(uid).child(conversationId)
                .child(System.currentTimeMillis() + "_" + fileName);

        try {
            InputStream input = context.getContentResolver().openInputStream(source);
            if (input == null) throw new java.io.IOException("Cannot open the selected file");
            com.google.firebase.storage.UploadTask task = reference.putStream(input);
            final long reportedSize = size >= 0 ? size : 0;
            task.addOnSuccessListener(taskSnapshot -> reference.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                if (listener != null) {
                                    listener.onComplete(uri.toString(), fileName, reportedSize,
                                            mimeType, null);
                                }
                            })
                            .addOnFailureListener(error -> {
                                if (listener != null) {
                                    listener.onComplete(null, null, 0, null, error);
                                }
                            }))
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "uploadAttachment: failure", error);
                        if (listener != null) {
                            listener.onComplete(null, null, 0, null,
                                    new IllegalStateException(
                                            "Upload failed. Check your connection and Storage rules."));
                        }
                    });
        } catch (Exception error) {
            Log.w(TAG, "uploadAttachment: unreadable", error);
            if (listener != null) {
                listener.onComplete(null, null, 0, null,
                        new IllegalStateException("That file could not be read. Try another one."));
            }
        }
    }

    /** Uploads a group photo and returns its download URL. */
    public void uploadGroupPhoto(Uri source, final AuthManager.ResultListener<String> listener) {
        final String uid = currentUidOrNull();
        FirebaseStorage storage = FirestoreManager.getInstance().getStorageOrNull();
        if (source == null || uid == null || storage == null) {
            if (listener != null) {
                listener.onComplete(null, new IllegalStateException("Photo upload is unavailable"));
            }
            return;
        }
        android.content.Context context = com.google.firebase.FirebaseApp.getInstance()
                .getApplicationContext();
        try {
            InputStream input = context.getContentResolver().openInputStream(source);
            if (input == null) throw new java.io.IOException("Cannot open the photo");
            StorageReference reference = storage.getReference()
                    .child("group_photos").child(uid)
                    .child(System.currentTimeMillis() + ".jpg");
            reference.putStream(input)
                    .addOnSuccessListener(taskSnapshot -> reference.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                if (listener != null) listener.onComplete(uri.toString(), null);
                            })
                            .addOnFailureListener(error -> {
                                if (listener != null) listener.onComplete(null, error);
                            }))
                    .addOnFailureListener(error -> {
                        if (listener != null) listener.onComplete(null, error);
                    });
        } catch (Exception error) {
            if (listener != null) listener.onComplete(null, error);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Conversation toConversation(DocumentSnapshot document, String uid) {
        if (document == null || !document.exists()) return null;
        Conversation conversation = document.toObject(Conversation.class);
        if (conversation == null) return null;
        conversation.setId(document.getId());
        conversation.setUnreadCount(conversation.unreadFor(uid));
        if (conversation.isGroup()) {
            String title = conversation.getTitle();
            conversation.setDisplayTitle(title != null && !title.trim().isEmpty()
                    ? title : "Group chat");
            conversation.setDisplayPhotoUrl(conversation.getPhotoUrl());
        } else {
            String otherId = conversation.otherMemberId(uid);
            conversation.setOtherUserId(otherId);
            String name = conversation.nameOf(otherId);
            conversation.setDisplayTitle(name != null && !name.trim().isEmpty()
                    ? name : "CampusSync user");
            conversation.setDisplayPhotoUrl(conversation.photoOf(otherId));
        }
        return conversation;
    }

    private static String previewFor(String type, String text, String attachmentName) {
        if (Message.TYPE_IMAGE.equals(type)) return "📷 Photo";
        if (Message.TYPE_DOCUMENT.equals(type)) {
            return "📎 " + (attachmentName != null ? attachmentName : "Document");
        }
        if (text == null) return "";
        String trimmed = text.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "…" : trimmed;
    }

    private static String displayName(UserProfile profile) {
        if (profile == null) return "CampusSync user";
        String name = profile.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            name = profile.getEmail();
        }
        return name != null && !name.trim().isEmpty() ? name.trim() : "CampusSync user";
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) return "attachment";
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 80 ? cleaned.substring(cleaned.length() - 80) : cleaned;
    }

    private static long fileSize(android.content.Context context, Uri uri) {
        try (android.content.res.AssetFileDescriptor descriptor =
                     context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            return descriptor != null ? descriptor.getLength() : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object raw) {
        List<String> list = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<Object>) raw) {
                if (item instanceof String) list.add((String) item);
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> asStringMap(Object raw) {
        Map<String, String> map = new HashMap<>();
        if (raw instanceof Map) {
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) raw).entrySet()) {
                if (entry.getKey() instanceof String) {
                    map.put((String) entry.getKey(),
                            entry.getValue() != null ? String.valueOf(entry.getValue()) : null);
                }
            }
        }
        return map;
    }

    /** A blocked account shown on the blocked-users screen. */
    public static class BlockedUser {
        public final String uid;
        public final String name;
        public final Date blockedAt;

        public BlockedUser(String uid, String name, Date blockedAt) {
            this.uid = uid;
            this.name = name;
            this.blockedAt = blockedAt;
        }
    }

    // ------------------------------------------------------------------ callbacks

    public interface ConversationsListener {
        void onConversations(List<Conversation> conversations, Exception error);
    }

    public interface ConversationListener {
        void onConversation(Conversation conversation, Exception error);
    }

    public interface MessagesListener {
        void onMessages(List<Message> messages, boolean hasMore, Exception error);
    }

    public interface MembersListener {
        void onMembers(List<ConversationMember> members, Exception error);
    }

    public interface TypingListener {
        void onTyping(String nameOrNull, Exception error);
    }

    public interface UnreadListener {
        void onUnread(long total);
    }

    public interface BlockedListener {
        void onBlocked(List<BlockedUser> blocked, Exception error);
    }

    public interface AttachmentListener {
        void onComplete(String url, String fileName, long size, String mimeType, Exception error);
    }
}
