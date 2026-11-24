package com.campussync.app.firebase;

import android.util.Log;

import com.campussync.app.model.CollegeNotice;
import com.campussync.app.model.Event;
import com.campussync.app.model.EventRegistration;
import com.campussync.app.model.EventUpdate;
import com.campussync.app.model.UserProfile;
import com.campussync.app.util.QrCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore access for the event radar, registrations, QR attendance and college notices.
 *
 * <p>Trust model:
 * <ul>
 *   <li>Registration is a transaction: the one-registration-per-user rule comes from the
 *       document id being the uid, capacity is enforced against the mirrored counter, and
 *       {@code firestore.rules} refuses any counter move beyond the capacity.</li>
 *   <li>Attendance is only writable by the organizer (or an admin) and only once — the rule
 *       requires {@code attended} to move from false to true, so a duplicate check-in fails.</li>
 *   <li>A check-in is accepted only when the scanned ticket token equals the secret stored on
 *       the participant's registration document.</li>
 * </ul>
 */
public class EventRepository {

    private static final String TAG = "EventRepository";

    public static final String COLLECTION_EVENTS = "events";
    public static final String COLLECTION_NOTICES = "notices";
    public static final String SUB_REGISTRATIONS = "registrations";
    public static final String SUB_UPDATES = "updates";

    /** Upper bound for the radar window; filters are applied client-side so no index is needed. */
    private static final int RADAR_LIMIT = 120;
    private static final int NOTICE_LIMIT = 50;

    private static volatile EventRepository instance;

    private final FirebaseFirestore db;

    private EventRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static EventRepository getInstance() {
        if (instance == null) {
            synchronized (EventRepository.class) {
                if (instance == null) instance = new EventRepository();
            }
        }
        return instance;
    }

    public static String currentUidOrNull() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    private com.google.firebase.firestore.CollectionReference events() {
        return db.collection(COLLECTION_EVENTS);
    }

    private DocumentReference eventRef(String eventId) {
        return events().document(eventId);
    }

    // ------------------------------------------------------------------ radar

    /**
     * Live radar feed ordered by start date. College, category, date and text filters are applied
     * by the caller so the query never needs a composite index.
     */
    public ListenerRegistration listenToRadar(final RadarListener listener) {
        return events()
                .orderBy("startDate", Query.Direction.ASCENDING)
                .limit(RADAR_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToRadar: failure", error);
                        if (listener != null) listener.onEvents(null, error);
                        return;
                    }
                    List<Event> events = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Event event = toEvent(document);
                            if (event != null) events.add(event);
                        }
                    }
                    if (listener != null) listener.onEvents(events, null);
                });
    }

    /** Live single event, used by the detail screen. */
    public ListenerRegistration listenToEvent(String eventId, final EventListener listener) {
        if (eventId == null) {
            if (listener != null) listener.onEvent(null, null);
            return null;
        }
        return eventRef(eventId).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.w(TAG, "listenToEvent: failure", error);
                if (listener != null) listener.onEvent(null, error);
                return;
            }
            if (listener != null) {
                listener.onEvent(snapshot != null && snapshot.exists()
                        ? toEvent(snapshot) : null, null);
            }
        });
    }

    private Event toEvent(DocumentSnapshot document) {
        Event event = document.toObject(Event.class);
        if (event == null) return null;
        event.setId(document.getId());
        return event;
    }

    // ------------------------------------------------------------------ registration

    /** The signed-in user's registration for one event, if any. */
    public ListenerRegistration listenToMyRegistration(String eventId,
                                                       final RegistrationListener listener) {
        String uid = currentUidOrNull();
        if (eventId == null || uid == null) {
            if (listener != null) listener.onRegistration(null, null);
            return null;
        }
        return eventRef(eventId).collection(SUB_REGISTRATIONS).document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToMyRegistration: failure", error);
                        if (listener != null) listener.onRegistration(null, error);
                        return;
                    }
                    EventRegistration registration = null;
                    if (snapshot != null && snapshot.exists()) {
                        registration = snapshot.toObject(EventRegistration.class);
                        if (registration != null) registration.setUid(snapshot.getId());
                    }
                    if (listener != null) listener.onRegistration(registration, null);
                });
    }

    /**
     * Registers the signed-in user. Idempotent when already registered, refuses a full or
     * cancelled event, and keeps the event's mirrored counters in step in the same transaction.
     */
    public void register(Event event, UserProfile profile, final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || event == null || event.getId() == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference reference = eventRef(event.getId());
        final DocumentReference registrationRef =
                reference.collection(SUB_REGISTRATIONS).document(uid);

        db.runTransaction(transaction -> {
            DocumentSnapshot eventSnapshot = transaction.get(reference);
            if (!eventSnapshot.exists()) {
                throw new IllegalStateException("This event no longer exists");
            }
            if (Event.STATUS_CANCELLED.equals(eventSnapshot.getString("status"))) {
                throw new IllegalStateException("This event has been cancelled");
            }
            DocumentSnapshot registrationSnapshot = transaction.get(registrationRef);
            boolean alreadyRegistered = registrationSnapshot.exists()
                    && EventRegistration.STATUS_REGISTERED.equals(
                            registrationSnapshot.getString("status"));
            if (alreadyRegistered) return null;

            long capacity = eventSnapshot.getLong("capacity") != null
                    ? eventSnapshot.getLong("capacity") : 0L;
            long count = asLong(eventSnapshot, "registrationCount");
            if (capacity > 0 && count >= capacity) {
                throw new IllegalStateException("This event is full");
            }

            String existingToken = registrationSnapshot.exists()
                    ? registrationSnapshot.getString("qrToken") : null;
            boolean attended = registrationSnapshot.exists()
                    && Boolean.TRUE.equals(registrationSnapshot.getBoolean("attended"));

            Map<String, Object> registration = new HashMap<>();
            registration.put("uid", uid);
            registration.put("name", profile != null ? profile.getDisplayName() : null);
            registration.put("email", profile != null ? profile.getEmail() : null);
            registration.put("college", profile != null ? profile.getCollege() : null);
            registration.put("status", EventRegistration.STATUS_REGISTERED);
            registration.put("qrToken", existingToken != null ? existingToken : QrCode.newToken());
            registration.put("attended", attended);
            registration.put("createdAt", registrationSnapshot.exists()
                    ? registrationSnapshot.get("createdAt") : FieldValue.serverTimestamp());
            registration.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(registrationRef, registration);

            Map<String, Object> update = new HashMap<>();
            update.put("registrationCount", count + 1);
            update.put("attendeeIds", FieldValue.arrayUnion(uid));
            update.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(reference, update);
            return null;
        }).addOnSuccessListener(unused -> {
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(error -> {
            Log.w(TAG, "register: failure", error);
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Cancels the signed-in user's registration and frees the seat. */
    public void cancelRegistration(Event event, final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || event == null || event.getId() == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference reference = eventRef(event.getId());
        final DocumentReference registrationRef =
                reference.collection(SUB_REGISTRATIONS).document(uid);

        db.runTransaction(transaction -> {
            DocumentSnapshot registrationSnapshot = transaction.get(registrationRef);
            if (!registrationSnapshot.exists()) return null;
            if (!EventRegistration.STATUS_REGISTERED.equals(
                    registrationSnapshot.getString("status"))) {
                return null;
            }
            DocumentSnapshot eventSnapshot = transaction.get(reference);
            long count = Math.max(0, asLong(eventSnapshot, "registrationCount") - 1);

            transaction.update(registrationRef,
                    "status", EventRegistration.STATUS_CANCELLED,
                    "updatedAt", FieldValue.serverTimestamp());

            Map<String, Object> update = new HashMap<>();
            update.put("registrationCount", count);
            update.put("attendeeIds", FieldValue.arrayRemove(uid));
            update.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(reference, update);
            return null;
        }).addOnSuccessListener(unused -> {
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(error -> {
            Log.w(TAG, "cancelRegistration: failure", error);
            if (listener != null) listener.onComplete(error);
        });
    }

    // ------------------------------------------------------------------ QR attendance

    /**
     * Verifies a scanned ticket and marks attendance. The token must match the registration
     * secret and the registration must not already be checked in; both are re-checked inside the
     * transaction (and again by the security rules on the write).
     */
    public void checkIn(Event event, QrCode.Ticket ticket, final CheckInListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || event == null || event.getId() == null || ticket == null) {
            if (listener != null) {
                listener.onCheckedIn(false, "That code is not a CampusSync ticket");
            }
            return;
        }
        if (!event.getId().equals(ticket.eventId)) {
            if (listener != null) {
                listener.onCheckedIn(false, "This ticket belongs to a different event");
            }
            return;
        }
        if (!uid.equals(event.getOrganizerId())) {
            if (listener != null) {
                listener.onCheckedIn(false, "Only the organizer can check people in");
            }
            return;
        }
        final DocumentReference registrationRef = eventRef(event.getId())
                .collection(SUB_REGISTRATIONS).document(ticket.uid);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(registrationRef);
            if (!snapshot.exists()) {
                throw new IllegalStateException("No registration found for this ticket");
            }
            if (EventRegistration.STATUS_CANCELLED.equals(snapshot.getString("status"))) {
                throw new IllegalStateException("This registration was cancelled");
            }
            String storedToken = snapshot.getString("qrToken");
            if (storedToken == null || !storedToken.equals(ticket.token)) {
                throw new IllegalStateException("This ticket is not valid for the event");
            }
            if (Boolean.TRUE.equals(snapshot.getBoolean("attended"))) {
                throw new IllegalStateException("Already checked in");
            }
            Map<String, Object> update = new HashMap<>();
            update.put("attended", true);
            update.put("checkedInAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(registrationRef, update);
            return snapshot.getString("name");
        }).addOnSuccessListener(name -> {
            if (listener != null) {
                listener.onCheckedIn(true, name != null ? name : "Participant");
            }
        }).addOnFailureListener(error -> {
            Log.w(TAG, "checkIn: failure", error);
            if (listener != null) {
                listener.onCheckedIn(false, error.getMessage() != null
                        ? error.getMessage() : "Could not check this ticket in");
            }
        });
    }

    /** Participant list for the organizer / an administrator. */
    public ListenerRegistration listenToRegistrations(String eventId,
                                                      final RegistrationsListener listener) {
        if (eventId == null) {
            if (listener != null) listener.onRegistrations(new ArrayList<>(), null);
            return null;
        }
        return eventRef(eventId).collection(SUB_REGISTRATIONS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToRegistrations: failure", error);
                        if (listener != null) listener.onRegistrations(null, error);
                        return;
                    }
                    List<EventRegistration> registrations = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            EventRegistration registration =
                                    document.toObject(EventRegistration.class);
                            if (registration == null) continue;
                            registration.setUid(document.getId());
                            registrations.add(registration);
                        }
                    }
                    Collections.sort(registrations, new Comparator<EventRegistration>() {
                        @Override
                        public int compare(EventRegistration left, EventRegistration right) {
                            if (left.isAttended() != right.isAttended()) {
                                return left.isAttended() ? 1 : -1;
                            }
                            String ln = left.getName() != null ? left.getName() : "";
                            String rn = right.getName() != null ? right.getName() : "";
                            return ln.compareToIgnoreCase(rn);
                        }
                    });
                    if (listener != null) listener.onRegistrations(registrations, null);
                });
    }

    // ------------------------------------------------------------------ organizer

    /** Creates an event owned by the signed-in organizer. */
    public void createEvent(Event event, final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || event == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        event.setOrganizerId(uid);
        event.setStatus(Event.STATUS_PUBLISHED);
        event.setRegistrationCount(0);
        event.setAttendeeIds(new ArrayList<String>());
        event.setCreatedAt(new java.util.Date());
        event.setUpdatedAt(new java.util.Date());
        events().add(event)
                .addOnSuccessListener(reference -> {
                    Log.d(TAG, "createEvent: success");
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "createEvent: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Edits organizer-owned fields (title, description, schedule, capacity, category). */
    public void updateEvent(String eventId, Map<String, Object> fields,
                            final AuthManager.ActionListener listener) {
        if (eventId == null) {
            if (listener != null) listener.onComplete(new IllegalArgumentException("Missing event"));
            return;
        }
        Map<String, Object> updates = new HashMap<>(fields);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        eventRef(eventId).update(updates)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "updateEvent: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Cancels an event; registrants are notified by the Cloud Function trigger. */
    public void cancelEvent(String eventId, final AuthManager.ActionListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Event.STATUS_CANCELLED);
        updateEvent(eventId, updates, listener);
    }

    /** Events the signed-in user organizes, plus the ones they registered for. */
    public ListenerRegistration listenToMyEvents(final MyEventsListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onMyEvents(new ArrayList<>(), new ArrayList<>(), null);
            return null;
        }
        final List<Event> hosted = new ArrayList<>();
        final List<Event> registered = new ArrayList<>();
        final ListenerRegistration[] inner = new ListenerRegistration[2];
        ListenerRegistration outer = events().whereEqualTo("organizerId", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onMyEvents(null, null, error);
                        return;
                    }
                    hosted.clear();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Event event = toEvent(document);
                            if (event != null) hosted.add(event);
                        }
                    }
                    if (listener != null) listener.onMyEvents(
                            new ArrayList<>(hosted), new ArrayList<>(registered), null);
                });
        inner[0] = outer;
        inner[1] = events().whereArrayContains("attendeeIds", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) return;
                    registered.clear();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Event event = toEvent(document);
                            if (event != null) registered.add(event);
                        }
                    }
                    if (listener != null) listener.onMyEvents(
                            new ArrayList<>(hosted), new ArrayList<>(registered), null);
                });
        return () -> {
            if (inner[0] != null) inner[0].remove();
            if (inner[1] != null) inner[1].remove();
        };
    }

    // ------------------------------------------------------------------ updates

    /** Change notes posted by the organizer. */
    public ListenerRegistration listenToUpdates(String eventId, final UpdatesListener listener) {
        if (eventId == null) {
            if (listener != null) listener.onUpdates(new ArrayList<>(), null);
            return null;
        }
        return eventRef(eventId).collection(SUB_UPDATES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToUpdates: failure", error);
                        if (listener != null) listener.onUpdates(null, error);
                        return;
                    }
                    List<EventUpdate> updates = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            EventUpdate update = document.toObject(EventUpdate.class);
                            if (update != null) {
                                update.setId(document.getId());
                                updates.add(update);
                            }
                        }
                    }
                    if (listener != null) listener.onUpdates(updates, null);
                });
    }

    /** Posts an organizer update; the Cloud Function notifies every registrant. */
    public void addUpdate(String eventId, String title, String body, String authorName,
                          final AuthManager.ActionListener listener) {
        String uid = currentUidOrNull();
        if (eventId == null || uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("title", title);
        update.put("body", body);
        update.put("authorId", uid);
        update.put("authorName", authorName);
        update.put("createdAt", FieldValue.serverTimestamp());
        eventRef(eventId).collection(SUB_UPDATES).add(update)
                .addOnSuccessListener(reference -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "addUpdate: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    // ------------------------------------------------------------------ notices

    public ListenerRegistration listenToNotices(final NoticesListener listener) {
        return db.collection(COLLECTION_NOTICES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(NOTICE_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToNotices: failure", error);
                        if (listener != null) listener.onNotices(null, error);
                        return;
                    }
                    List<CollegeNotice> notices = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            CollegeNotice notice = document.toObject(CollegeNotice.class);
                            if (notice != null) {
                                notice.setId(document.getId());
                                notices.add(notice);
                            }
                        }
                    }
                    if (listener != null) listener.onNotices(notices, null);
                });
    }

    /** Posts a college notice; only administrators may do this (enforced by the rules). */
    public void postNotice(String title, String body, String college, String authorName,
                           final AuthManager.ActionListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        Map<String, Object> notice = new HashMap<>();
        notice.put("title", title);
        notice.put("body", body);
        notice.put("college", college);
        notice.put("authorId", uid);
        notice.put("authorName", authorName);
        notice.put("pinned", false);
        notice.put("createdAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_NOTICES).add(notice)
                .addOnSuccessListener(reference -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "postNotice: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    private static long asLong(DocumentSnapshot snapshot, String field) {
        Long value = snapshot.getLong(field);
        return value != null ? value : 0L;
    }

    // ------------------------------------------------------------------ callbacks

    public interface RadarListener {
        void onEvents(List<Event> events, Exception error);
    }

    public interface EventListener {
        void onEvent(Event event, Exception error);
    }

    public interface RegistrationListener {
        void onRegistration(EventRegistration registration, Exception error);
    }

    public interface RegistrationsListener {
        void onRegistrations(List<EventRegistration> registrations, Exception error);
    }

    public interface CheckInListener {
        void onCheckedIn(boolean success, String messageOrName);
    }

    public interface UpdatesListener {
        void onUpdates(List<EventUpdate> updates, Exception error);
    }

    public interface NoticesListener {
        void onNotices(List<CollegeNotice> notices, Exception error);
    }

    public interface MyEventsListener {
        void onMyEvents(List<Event> hosted, List<Event> registered, Exception error);
    }
}
