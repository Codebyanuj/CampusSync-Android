package com.campussync.app.firebase;

import android.util.Log;

import com.campussync.app.model.Doubt;
import com.campussync.app.model.DoubtAnswer;
import com.campussync.app.model.TutorProfile;
import com.campussync.app.model.TutoringRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore access for peer learning: doubts and answers, tutor listings, tutoring requests and
 * session reviews.
 *
 * <p>Trust model:
 * <ul>
 *   <li>Doubt threads are readable by signed-in users; only the asker may mark a doubt solved or
 *       accept an answer, and only the answer's author may edit the answer text.</li>
 *   <li>One answer increments {@code answerCount} by exactly one in the same commit, so the
 *       counter cannot be inflated on its own.</li>
 *   <li>A tutoring request is readable by its two participants only. The tutor accepts or
 *       declines; the student cancels or completes. The review lives on the request, so a session
 *       can be rated exactly once, and the tutor's aggregate rating moves in the same
 *       transaction.</li>
 * </ul>
 */
public class TutoringRepository {

    private static final String TAG = "TutoringRepository";

    public static final String COLLECTION_DOUBTS = "doubts";
    public static final String COLLECTION_TUTORS = "tutors";
    public static final String COLLECTION_REQUESTS = "tutoringRequests";
    public static final String SUB_ANSWERS = "answers";

    private static final int DOUBT_LIMIT = 100;
    private static final int TUTOR_LIMIT = 100;

    private static volatile TutoringRepository instance;

    private final FirebaseFirestore db;

    private TutoringRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static TutoringRepository getInstance() {
        if (instance == null) {
            synchronized (TutoringRepository.class) {
                if (instance == null) instance = new TutoringRepository();
            }
        }
        return instance;
    }

    public static String currentUidOrNull() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    private com.google.firebase.firestore.CollectionReference doubts() {
        return db.collection(COLLECTION_DOUBTS);
    }

    private DocumentReference doubtRef(String doubtId) {
        return doubts().document(doubtId);
    }

    // ------------------------------------------------------------------ doubts

    /** Live doubt board, newest first. Subject/solved/text filters run on the client. */
    public ListenerRegistration listenToDoubts(final DoubtsListener listener) {
        return doubts()
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(DOUBT_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToDoubts: failure", error);
                        if (listener != null) listener.onDoubts(null, error);
                        return;
                    }
                    List<Doubt> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Doubt doubt = document.toObject(Doubt.class);
                            if (doubt == null) continue;
                            doubt.setId(document.getId());
                            list.add(doubt);
                        }
                    }
                    if (listener != null) listener.onDoubts(list, null);
                });
    }

    public ListenerRegistration listenToDoubt(String doubtId, final DoubtListener listener) {
        if (doubtId == null) {
            if (listener != null) listener.onDoubt(null, null);
            return null;
        }
        return doubtRef(doubtId).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                if (listener != null) listener.onDoubt(null, error);
                return;
            }
            Doubt doubt = null;
            if (snapshot != null && snapshot.exists()) {
                doubt = snapshot.toObject(Doubt.class);
                if (doubt != null) doubt.setId(snapshot.getId());
            }
            if (listener != null) listener.onDoubt(doubt, null);
        });
    }

    /** Posts a doubt owned by the signed-in user. */
    public void askDoubt(Doubt doubt, final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || doubt == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        doubt.setAuthorId(uid);
        doubt.setAnswerCount(0);
        doubt.setSolved(false);
        doubt.setCreatedAt(new java.util.Date());
        doubt.setLastActivityAt(new java.util.Date());
        doubts().add(doubt)
                .addOnSuccessListener(reference -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "askDoubt: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Answers on a doubt, oldest first. */
    public ListenerRegistration listenToAnswers(String doubtId,
                                                final AnswersListener listener) {
        if (doubtId == null) {
            if (listener != null) listener.onAnswers(new ArrayList<>(), null);
            return null;
        }
        return doubtRef(doubtId).collection(SUB_ANSWERS)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToAnswers: failure", error);
                        if (listener != null) listener.onAnswers(null, error);
                        return;
                    }
                    List<DoubtAnswer> answers = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            DoubtAnswer answer = document.toObject(DoubtAnswer.class);
                            if (answer == null) continue;
                            answer.setId(document.getId());
                            answers.add(answer);
                        }
                    }
                    if (listener != null) listener.onAnswers(answers, null);
                });
    }

    /**
     * Adds an answer and bumps the doubt's {@code answerCount} by one in a single transaction.
     * The rules refuse either write on its own if the invariant is broken.
     */
    public void answerDoubt(String doubtId, DoubtAnswer answer,
                            final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || doubtId == null || answer == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        if (answer.getBody() == null || answer.getBody().trim().isEmpty()) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Write your answer first"));
            }
            return;
        }
        final DocumentReference reference = doubtRef(doubtId);
        final DocumentReference answerRef = reference.collection(SUB_ANSWERS).document();

        db.runTransaction(transaction -> {
            DocumentSnapshot doubtSnapshot = transaction.get(reference);
            if (!doubtSnapshot.exists()) {
                throw new IllegalStateException("This doubt no longer exists");
            }
            long count = doubtSnapshot.getLong("answerCount") != null
                    ? doubtSnapshot.getLong("answerCount") : 0L;

            Map<String, Object> fields = new HashMap<>();
            fields.put("body", answer.getBody().trim());
            fields.put("authorId", uid);
            fields.put("authorName", answer.getAuthorName());
            fields.put("authorPhotoUrl", answer.getAuthorPhotoUrl());
            fields.put("accepted", false);
            fields.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(answerRef, fields);

            Map<String, Object> update = new HashMap<>();
            update.put("answerCount", count + 1);
            update.put("lastActivityAt", FieldValue.serverTimestamp());
            transaction.update(reference, update);
            return null;
        }).addOnSuccessListener(unused -> {
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(error -> {
            Log.w(TAG, "answerDoubt: failure", error);
            if (listener != null) listener.onComplete(error);
        });
    }

    /**
     * Marks a doubt solved and its answer accepted. Only the asker can do this (the rules check
     * it too); the accepted answer is what earns the {@code answer_accepted} karma award.
     */
    public void acceptAnswer(Doubt doubt, DoubtAnswer answer,
                             final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || doubt == null || doubt.getId() == null || answer == null
                || answer.getId() == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Missing answer"));
            return;
        }
        if (!uid.equals(doubt.getAuthorId())) {
            if (listener != null) {
                listener.onComplete(new IllegalStateException(
                        "Only the student who asked can accept an answer"));
            }
            return;
        }
        final DocumentReference reference = doubtRef(doubt.getId());
        final DocumentReference answerRef =
                reference.collection(SUB_ANSWERS).document(answer.getId());

        db.runTransaction(transaction -> {
            DocumentSnapshot doubtSnapshot = transaction.get(reference);
            if (!doubtSnapshot.exists()) {
                throw new IllegalStateException("This doubt no longer exists");
            }
            DocumentSnapshot answerSnapshot = transaction.get(answerRef);
            if (!answerSnapshot.exists()) {
                throw new IllegalStateException("That answer no longer exists");
            }
            boolean alreadyAccepted = Boolean.TRUE.equals(answerSnapshot.getBoolean("accepted"));

            Map<String, Object> doubtUpdate = new HashMap<>();
            doubtUpdate.put("solved", true);
            doubtUpdate.put("acceptedAnswerId", answer.getId());
            doubtUpdate.put("lastActivityAt", FieldValue.serverTimestamp());
            transaction.update(reference, doubtUpdate);

            if (!alreadyAccepted) {
                transaction.update(answerRef, "accepted", true);
            }
            return null;
        }).addOnSuccessListener(unused -> {
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(error -> {
            Log.w(TAG, "acceptAnswer: failure", error);
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Lets the asker close a doubt that no longer needs an answer. */
    public void markSolved(Doubt doubt, final AuthManager.ActionListener listener) {
        if (doubt == null || doubt.getId() == null) {
            if (listener != null) listener.onComplete(new IllegalArgumentException("Missing doubt"));
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("solved", true);
        update.put("lastActivityAt", FieldValue.serverTimestamp());
        doubtRef(doubt.getId()).update(update)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "markSolved: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    // ------------------------------------------------------------------ tutors

    /** Live tutor directory. Subject/text filters are applied by the screen. */
    public ListenerRegistration listenToTutors(final TutorsListener listener) {
        return db.collection(COLLECTION_TUTORS)
                .limit(TUTOR_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToTutors: failure", error);
                        if (listener != null) listener.onTutors(null, error);
                        return;
                    }
                    List<TutorProfile> tutors = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            TutorProfile tutor = document.toObject(TutorProfile.class);
                            if (tutor == null) continue;
                            tutor.setId(document.getId());
                            tutors.add(tutor);
                        }
                    }
                    Collections.sort(tutors, new Comparator<TutorProfile>() {
                        @Override
                        public int compare(TutorProfile left, TutorProfile right) {
                            float a = left.getRatingAverage();
                            float b = right.getRatingAverage();
                            if (a == b) {
                                String ln = left.getDisplayName() != null
                                        ? left.getDisplayName() : "";
                                String rn = right.getDisplayName() != null
                                        ? right.getDisplayName() : "";
                                return ln.compareToIgnoreCase(rn);
                            }
                            return a > b ? -1 : 1;
                        }
                    });
                    if (listener != null) listener.onTutors(tutors, null);
                });
    }

    public ListenerRegistration listenToTutor(String tutorId, final TutorListener listener) {
        if (tutorId == null) {
            if (listener != null) listener.onTutor(null, null);
            return null;
        }
        return db.collection(COLLECTION_TUTORS).document(tutorId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onTutor(null, error);
                        return;
                    }
                    TutorProfile tutor = null;
                    if (snapshot != null && snapshot.exists()) {
                        tutor = snapshot.toObject(TutorProfile.class);
                        if (tutor != null) tutor.setId(snapshot.getId());
                    }
                    if (listener != null) listener.onTutor(tutor, null);
                });
    }

    /** Creates or updates the signed-in user's tutor listing. */
    public void saveTutorProfile(TutorProfile tutor, final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || tutor == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        if (tutor.getSubjects() == null || tutor.getSubjects().isEmpty()) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Add at least one subject"));
            }
            return;
        }
        Map<String, Object> fields = new HashMap<>();
        fields.put("displayName", tutor.getDisplayName());
        fields.put("photoUrl", tutor.getPhotoUrl());
        fields.put("college", tutor.getCollege());
        fields.put("department", tutor.getDepartment());
        fields.put("semester", tutor.getSemester());
        fields.put("subjects", tutor.getSubjects());
        fields.put("bio", tutor.getBio());
        fields.put("updatedAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION_TUTORS).document(uid)
                .set(fields, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "saveTutorProfile: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    // ------------------------------------------------------------------ requests

    /** Requests the signed-in user sent plus the ones addressed to them, newest first. */
    public ListenerRegistration listenToRequests(final RequestsListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onRequests(new ArrayList<>(), null);
            return null;
        }
        // Per-screen row caches: the two live queries are merged, never shared across screens.
        final Map<String, TutoringRequest> tutorRows = new LinkedHashMap<>();
        final Map<String, TutoringRequest> studentRows = new LinkedHashMap<>();

        final ListenerRegistration tutorRegistration = db.collection(COLLECTION_REQUESTS)
                .whereEqualTo("tutorId", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToRequests(tutor): failure", error);
                        if (listener != null) listener.onRequests(null, error);
                        return;
                    }
                    fillRows(tutorRows, snapshots);
                    emitRequests(tutorRows, studentRows, listener);
                });
        final ListenerRegistration studentRegistration = db.collection(COLLECTION_REQUESTS)
                .whereEqualTo("studentId", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToRequests(student): failure", error);
                        if (listener != null) listener.onRequests(null, error);
                        return;
                    }
                    fillRows(studentRows, snapshots);
                    emitRequests(tutorRows, studentRows, listener);
                });

        return () -> {
            tutorRegistration.remove();
            studentRegistration.remove();
        };
    }

    private static void fillRows(Map<String, TutoringRequest> rows, QuerySnapshot snapshots) {
        rows.clear();
        if (snapshots == null) return;
        for (DocumentSnapshot document : snapshots.getDocuments()) {
            TutoringRequest request = document.toObject(TutoringRequest.class);
            if (request == null) continue;
            request.setId(document.getId());
            rows.put(document.getId(), request);
        }
    }

    private static void emitRequests(Map<String, TutoringRequest> tutorRows,
                                     Map<String, TutoringRequest> studentRows,
                                     RequestsListener listener) {
        Map<String, TutoringRequest> combined = new LinkedHashMap<>(tutorRows);
        combined.putAll(studentRows);
        List<TutoringRequest> list = new ArrayList<>(combined.values());
        Collections.sort(list, new Comparator<TutoringRequest>() {
            @Override
            public int compare(TutoringRequest left, TutoringRequest right) {
                long a = left.getCreatedAt() != null ? left.getCreatedAt().getTime() : 0L;
                long b = right.getCreatedAt() != null ? right.getCreatedAt().getTime() : 0L;
                if (a == b) return 0;
                return a > b ? -1 : 1;
            }
        });
        if (listener != null) listener.onRequests(list, null);
    }

    /** Student books a session with a tutor. */
    public void createRequest(TutoringRequest request, final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || request == null || request.getTutorId() == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Missing tutor"));
            return;
        }
        if (uid.equals(request.getTutorId())) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("You cannot request yourself"));
            }
            return;
        }
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Pick a subject"));
            }
            return;
        }
        request.setStudentId(uid);
        request.setStatus(TutoringRequest.STATUS_PENDING);
        request.setReviewRating(0);
        request.setReviewComment("");
        request.setCreatedAt(new java.util.Date());
        request.setUpdatedAt(new java.util.Date());
        db.collection(COLLECTION_REQUESTS).add(request)
                .addOnSuccessListener(reference -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "createRequest: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Tutor accepts or declines a pending request. */
    public void respondToRequest(TutoringRequest request, boolean accept,
                                 final AuthManager.ActionListener listener) {
        if (request == null || request.getId() == null) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Missing request"));
            }
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("status", accept
                ? TutoringRequest.STATUS_ACCEPTED : TutoringRequest.STATUS_DECLINED);
        update.put("reviewedAt", FieldValue.serverTimestamp());
        update.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_REQUESTS).document(request.getId()).update(update)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "respondToRequest: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Student cancels a request that has not happened yet. */
    public void cancelRequest(TutoringRequest request, final AuthManager.ActionListener listener) {
        if (request == null || request.getId() == null) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Missing request"));
            }
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("status", TutoringRequest.STATUS_CANCELLED);
        update.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_REQUESTS).document(request.getId()).update(update)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "cancelRequest: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /**
     * Student completes the session and leaves the one review the request can hold. The tutor's
     * aggregate rating and completed-session count move in the same transaction.
     */
    public void completeAndReview(TutoringRequest request, long rating, String comment,
                                  final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || request == null || request.getId() == null) {
            if (listener != null) {
                listener.onComplete(new IllegalStateException("Missing request"));
            }
            return;
        }
        if (rating < 1 || rating > 5) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Choose between 1 and 5 stars"));
            }
            return;
        }
        final DocumentReference requestRef =
                db.collection(COLLECTION_REQUESTS).document(request.getId());
        final DocumentReference tutorRef =
                db.collection(COLLECTION_TUTORS).document(request.getTutorId());

        db.runTransaction(transaction -> {
            DocumentSnapshot requestSnapshot = transaction.get(requestRef);
            if (!requestSnapshot.exists()) {
                throw new IllegalStateException("This request no longer exists");
            }
            if (!uid.equals(requestSnapshot.getString("studentId"))) {
                throw new IllegalStateException("Only the student can review this session");
            }
            Long previous = requestSnapshot.getLong("reviewRating");
            if (previous != null && previous > 0) {
                throw new IllegalStateException("You already reviewed this session");
            }

            Map<String, Object> requestUpdate = new HashMap<>();
            requestUpdate.put("status", TutoringRequest.STATUS_COMPLETED);
            requestUpdate.put("reviewRating", rating);
            requestUpdate.put("reviewComment", comment != null ? comment : "");
            requestUpdate.put("completedAt", FieldValue.serverTimestamp());
            requestUpdate.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(requestRef, requestUpdate);

            DocumentSnapshot tutorSnapshot = transaction.get(tutorRef);
            if (tutorSnapshot.exists()) {
                long count = tutorSnapshot.getLong("ratingCount") != null
                        ? tutorSnapshot.getLong("ratingCount") : 0L;
                long sum = tutorSnapshot.getLong("ratingSum") != null
                        ? tutorSnapshot.getLong("ratingSum") : 0L;
                long sessions = tutorSnapshot.getLong("completedSessions") != null
                        ? tutorSnapshot.getLong("completedSessions") : 0L;
                Map<String, Object> tutorUpdate = new HashMap<>();
                tutorUpdate.put("ratingCount", count + 1);
                tutorUpdate.put("ratingSum", sum + rating);
                tutorUpdate.put("completedSessions", sessions + 1);
                tutorUpdate.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(tutorRef, tutorUpdate);
            }
            return null;
        }).addOnSuccessListener(unused -> {
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(error -> {
            Log.w(TAG, "completeAndReview: failure", error);
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Feedback left for a tutor, newest first, from the requests that carry a review. */
    public ListenerRegistration listenToTutorReviews(String tutorId,
                                                     final ReviewsListener listener) {
        if (tutorId == null) {
            if (listener != null) listener.onReviews(new ArrayList<>(), null);
            return null;
        }
        return db.collection(COLLECTION_REQUESTS).whereEqualTo("tutorId", tutorId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onReviews(null, error);
                        return;
                    }
                    List<TutoringRequest> reviews = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            TutoringRequest request = document.toObject(TutoringRequest.class);
                            if (request == null) continue;
                            request.setId(document.getId());
                            if (request.getReviewRating() > 0) reviews.add(request);
                        }
                    }
                    Collections.sort(reviews, new Comparator<TutoringRequest>() {
                        @Override
                        public int compare(TutoringRequest left, TutoringRequest right) {
                            long a = left.getCompletedAt() != null
                                    ? left.getCompletedAt().getTime() : 0L;
                            long b = right.getCompletedAt() != null
                                    ? right.getCompletedAt().getTime() : 0L;
                            if (a == b) return 0;
                            return a > b ? -1 : 1;
                        }
                    });
                    if (listener != null) listener.onReviews(reviews, null);
                });
    }

    // ------------------------------------------------------------------ callbacks

    public interface DoubtsListener {
        void onDoubts(List<Doubt> doubts, Exception error);
    }

    public interface DoubtListener {
        void onDoubt(Doubt doubt, Exception error);
    }

    public interface AnswersListener {
        void onAnswers(List<DoubtAnswer> answers, Exception error);
    }

    public interface TutorsListener {
        void onTutors(List<TutorProfile> tutors, Exception error);
    }

    public interface TutorListener {
        void onTutor(TutorProfile tutor, Exception error);
    }

    public interface RequestsListener {
        void onRequests(List<TutoringRequest> requests, Exception error);
    }

    public interface ReviewsListener {
        void onReviews(List<TutoringRequest> reviews, Exception error);
    }
}
