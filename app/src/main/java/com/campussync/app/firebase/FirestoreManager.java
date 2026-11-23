package com.campussync.app.firebase;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralised Firestore access layer.
 *
 * All reads/writes for user profiles, events and groups live here so the ViewModels stay
 * free of Firebase specifics and the same queries are reused across screens.
 */
public class FirestoreManager {

    private static final String TAG = "FirestoreManager";
    private static volatile FirestoreManager instance;

    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_EVENTS = "events";
    public static final String COLLECTION_GROUPS = "groups";

    public static final String FIELD_ATTENDEES = "attendeeIds";
    public static final String FIELD_MEMBERS = "memberIds";

    private final FirebaseFirestore db;
    private final FirebaseStorage storage;

    private FirestoreManager() {
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
    }

    public static FirestoreManager getInstance() {
        if (instance == null) {
            synchronized (FirestoreManager.class) {
                if (instance == null) {
                    instance = new FirestoreManager();
                }
            }
        }
        return instance;
    }

    public FirebaseFirestore getDb() {
        return db;
    }

    public FirebaseStorage getStorage() {
        return storage;
    }

    private static String currentUidOrNull() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    // ---------------- User profiles ----------------

    public void createUserProfile(String uid, Map<String, Object> profile, AuthManager.ActionListener listener) {
        db.collection(COLLECTION_USERS).document(uid)
                .set(profile, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "createUserProfile: success");
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "createUserProfile: failure", e);
                    if (listener != null) listener.onComplete(e);
                });
    }

    public void updateUserFields(Map<String, Object> fields, AuthManager.ActionListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        db.collection(COLLECTION_USERS).document(uid)
                .set(fields, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "updateUserFields: success");
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "updateUserFields: failure", e);
                    if (listener != null) listener.onComplete(e);
                });
    }

    public void getUserProfile(String uid, ProfileCallback callback) {
        db.collection(COLLECTION_USERS).document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (callback != null) {
                        callback.onSuccess(document.exists() ? document : null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getUserProfile: failure", e);
                    if (callback != null) callback.onError(e);
                });
    }

    /**
     * Resolves display names for the given user ids (used by attendee / member lists).
     * Missing documents are skipped. Firestore "in" queries are limited to 30 values.
     */
    public void getDisplayNames(List<String> userIds, DisplayNamesCallback callback) {
        if (userIds == null || userIds.isEmpty()) {
            if (callback != null) callback.onSuccess(new HashMap<>());
            return;
        }
        Map<String, String> result = new HashMap<>();
        List<String> ids = new ArrayList<>(userIds);
        if (ids.size() > 30) {
            ids = ids.subList(0, 30);
        }
        final List<String> queryIds = ids;
        db.collection(COLLECTION_USERS)
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), queryIds)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String name = doc.getString("displayName");
                        if (name == null) name = doc.getString("email");
                        result.put(doc.getId(), name != null ? name : "Member");
                    }
                    if (callback != null) callback.onSuccess(result);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getDisplayNames: failure", e);
                    if (callback != null) callback.onError(e);
                });
    }

    // ---------------- Events ----------------

    /**
     * Live listener over events ordered by start date.
     * The returned registration can be removed when the screen is destroyed.
     */
    public ListenerRegistration listenToEvents(EventsLoaded callback) {
        return db.collection(COLLECTION_EVENTS)
                .orderBy("startDate", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "listenToEvents: failed", e);
                        if (callback != null) callback.onEvents(null, e);
                        return;
                    }
                    List<com.campussync.app.model.Event> events = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            com.campussync.app.model.Event event =
                                    doc.toObject(com.campussync.app.model.Event.class);
                            if (event != null) {
                                event.setId(doc.getId());
                                events.add(event);
                            }
                        }
                    }
                    if (callback != null) callback.onEvents(events, null);
                });
    }

    public void getEvent(String eventId, EventCallback callback) {
        db.collection(COLLECTION_EVENTS).document(eventId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        if (callback != null) callback.onError(new IllegalStateException("Event not found"));
                        return;
                    }
                    com.campussync.app.model.Event event =
                            document.toObject(com.campussync.app.model.Event.class);
                    if (event != null) event.setId(document.getId());
                    if (callback != null) callback.onSuccess(event);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getEvent: failure", e);
                    if (callback != null) callback.onError(e);
                });
    }

    public void joinEvent(String eventId, AuthManager.ActionListener listener) {
        setMembership(COLLECTION_EVENTS, FIELD_ATTENDEES, eventId, true, listener);
    }

    public void leaveEvent(String eventId, AuthManager.ActionListener listener) {
        setMembership(COLLECTION_EVENTS, FIELD_ATTENDEES, eventId, false, listener);
    }

    // ---------------- Groups ----------------

    public ListenerRegistration listenToGroups(GroupsLoaded callback) {
        return db.collection(COLLECTION_GROUPS)
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "listenToGroups: failed", e);
                        if (callback != null) callback.onGroups(null, e);
                        return;
                    }
                    List<com.campussync.app.model.Group> groups = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            com.campussync.app.model.Group group =
                                    doc.toObject(com.campussync.app.model.Group.class);
                            if (group != null) {
                                group.setId(doc.getId());
                                groups.add(group);
                            }
                        }
                    }
                    if (callback != null) callback.onGroups(groups, null);
                });
    }

    public void getGroup(String groupId, GroupCallback callback) {
        db.collection(COLLECTION_GROUPS).document(groupId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        if (callback != null) callback.onError(new IllegalStateException("Group not found"));
                        return;
                    }
                    com.campussync.app.model.Group group =
                            document.toObject(com.campussync.app.model.Group.class);
                    if (group != null) group.setId(document.getId());
                    if (callback != null) callback.onSuccess(group);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getGroup: failure", e);
                    if (callback != null) callback.onError(e);
                });
    }

    public void joinGroup(String groupId, AuthManager.ActionListener listener) {
        setMembership(COLLECTION_GROUPS, FIELD_MEMBERS, groupId, true, listener);
    }

    public void leaveGroup(String groupId, AuthManager.ActionListener listener) {
        setMembership(COLLECTION_GROUPS, FIELD_MEMBERS, groupId, false, listener);
    }

    // ---------------- Shared transaction helper ----------------

    /**
     * Adds or removes the current user from a membership array inside a transaction, so two
     * devices joining at the same time cannot overwrite each other's membership.
     */
    private void setMembership(String collection, String field, String documentId,
                               boolean join, AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference ref = db.collection(collection).document(documentId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                throw new FirebaseFirestoreException("Document not found",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            List<String> members = (List<String>) snapshot.get(field);
            List<String> updated = members != null ? new ArrayList<>(members) : new ArrayList<>();
            if (join) {
                if (updated.contains(uid)) {
                    throw new FirebaseFirestoreException("Already a member",
                            FirebaseFirestoreException.Code.ALREADY_EXISTS);
                }
                updated.add(uid);
            } else {
                if (!updated.remove(uid)) {
                    throw new FirebaseFirestoreException("Not a member",
                            FirebaseFirestoreException.Code.ALREADY_EXISTS);
                }
            }
            transaction.update(ref, field, updated);
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, (join ? "join" : "leave") + " " + collection + "/" + documentId + ": success");
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(e -> {
            Log.w(TAG, "setMembership: failure", e);
            if (listener != null) listener.onComplete(e);
        });
    }

    // ---------------- Storage / cache ----------------

    public StorageReference getStorageReference(String path) {
        return storage.getReference().child(path);
    }

    /**
     * Clears the on-disk Firestore cache. Server data is untouched; documents are re-fetched
     * on the next read.
     */
    public void clearLocalCache() {
        db.clearPersistence()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Local cache cleared"))
                .addOnFailureListener(e -> Log.w(TAG, "clearLocalCache: failure", e));
    }

    // ---------------- Development seed data ----------------

    /**
     * Creates one demo event and one demo group if the collections are empty.
     * Kept in the data layer so every screen sees real Firestore documents during development.
     */
    public void ensureDemoData(AuthManager.ActionListener listener) {
        db.collection(COLLECTION_EVENTS).limit(1).get().addOnCompleteListener(eventTask -> {
            if (!eventTask.isSuccessful()) {
                if (listener != null) listener.onComplete(eventTask.getException());
                return;
            }
            if (eventTask.getResult() != null && !eventTask.getResult().isEmpty()) {
                if (listener != null) listener.onComplete(null);
                return;
            }
            Map<String, Object> event = new HashMap<>();
            event.put("title", "Campus Sync Launch Meetup");
            event.put("description", "Meet the Campus Sync team, get a walkthrough of the app and "
                    + "find out what is coming next this semester.");
            event.put("location", "Main Campus Hall");
            event.put("startDate", FieldValue.serverTimestamp());
            event.put("attendeeIds", new ArrayList<String>());

            db.collection(COLLECTION_EVENTS).add(event).addOnCompleteListener(addTask -> {
                if (!addTask.isSuccessful()) {
                    if (listener != null) listener.onComplete(addTask.getException());
                    return;
                }
                Map<String, Object> group = new HashMap<>();
                group.put("name", "Campus Sync Beta Users");
                group.put("description", "Early contributors testing new Campus Sync features.");
                group.put("memberIds", new ArrayList<String>());
                db.collection(COLLECTION_GROUPS).add(group)
                        .addOnCompleteListener(groupTask -> {
                            if (listener != null) {
                                listener.onComplete(groupTask.isSuccessful()
                                        ? null : groupTask.getException());
                            }
                        });
            });
        });
    }

    // ---------------- Callbacks ----------------

    public interface EventsLoaded {
        void onEvents(List<com.campussync.app.model.Event> events, Exception error);
    }

    public interface GroupsLoaded {
        void onGroups(List<com.campussync.app.model.Group> groups, Exception error);
    }

    public interface EventCallback {
        void onSuccess(com.campussync.app.model.Event event);
        void onError(Exception error);
    }

    public interface GroupCallback {
        void onSuccess(com.campussync.app.model.Group group);
        void onError(Exception error);
    }

    public interface ProfileCallback {
        void onSuccess(DocumentSnapshot snapshot);
        void onError(Exception error);
    }

    public interface DisplayNamesCallback {
        void onSuccess(Map<String, String> namesById);
        void onError(Exception error);
    }
}
