package com.campussync.app.firebase;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Online / offline presence in {@code presence/{uid}}.
 *
 * <p>Firestore has no disconnect hook without the Realtime Database, so the app writes
 * {@code online} while it is in the foreground and refreshes {@code lastSeen} on a heartbeat.
 * A contact counts as online when they are marked online and their last heartbeat is recent;
 * otherwise the UI shows when they were last seen.
 */
public class PresenceRepository {

    private static final String TAG = "PresenceRepository";

    public static final String COLLECTION_PRESENCE = "presence";
    public static final String STATE_ONLINE = "online";
    public static final String STATE_OFFLINE = "offline";

    /** A heartbeat older than this is treated as offline. */
    private static final long ONLINE_WINDOW_MS = 2 * 60 * 1000L;

    private static volatile PresenceRepository instance;

    private final FirebaseFirestore db;

    private PresenceRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static PresenceRepository getInstance() {
        if (instance == null) {
            synchronized (PresenceRepository.class) {
                if (instance == null) instance = new PresenceRepository();
            }
        }
        return instance;
    }

    private static String currentUidOrNull() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    private DocumentReference myPresence() {
        String uid = currentUidOrNull();
        return uid != null ? db.collection(COLLECTION_PRESENCE).document(uid) : null;
    }

    /** Marks the signed-in user online; safe to call repeatedly (heartbeat). */
    public void setOnline() {
        write(STATE_ONLINE);
    }

    /** Marks the signed-in user offline, keeping the timestamp for "last seen". */
    public void setOffline() {
        write(STATE_OFFLINE);
    }

    private void write(String state) {
        DocumentReference reference = myPresence();
        if (reference == null) return;
        Map<String, Object> fields = new HashMap<>();
        fields.put("state", state);
        fields.put("lastSeen", FieldValue.serverTimestamp());
        reference.set(fields, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(error -> Log.w(TAG, "presence write failed", error));
    }

    /** Live presence for one contact. The caller removes the returned registration. */
    public ListenerRegistration listenToPresence(String uid, PresenceListener listener) {
        if (uid == null) {
            if (listener != null) listener.onPresence(STATE_OFFLINE, null, null);
            return null;
        }
        return db.collection(COLLECTION_PRESENCE).document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToPresence: failure", error);
                        if (listener != null) listener.onPresence(STATE_OFFLINE, null, error);
                        return;
                    }
                    String state = STATE_OFFLINE;
                    Date lastSeen = null;
                    if (snapshot != null && snapshot.exists()) {
                        String raw = snapshot.getString("state");
                        if (raw != null) state = raw;
                        lastSeen = snapshot.getDate("lastSeen");
                    }
                    if (listener != null) listener.onPresence(state, lastSeen, null);
                });
    }

    /** True when the presence pair means "currently online". */
    public static boolean isOnline(String state, Date lastSeen) {
        if (!STATE_ONLINE.equals(state)) return false;
        if (lastSeen == null) return true;
        return System.currentTimeMillis() - lastSeen.getTime() < ONLINE_WINDOW_MS;
    }

    public interface PresenceListener {
        void onPresence(String state, Date lastSeen, Exception error);
    }
}
