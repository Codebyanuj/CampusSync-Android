package com.campussync.app.firebase;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.campussync.app.model.UserProfile;
import com.campussync.app.model.VerificationRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore/Storage access for user profiles and verification requests.
 *
 * This is the single place that writes the extended profile fields. Role, account status and
 * verification status are also written here, but only an administrator may change them: the
 * Firestore rules reject such writes from a normal owner (see {@code firestore.rules}), so the
 * client never decides its own privileges.
 */
public class UserRepository {

    private static final String TAG = "UserRepository";

    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_VERIFICATION_REQUESTS = "verificationRequests";
    public static final String COLLECTION_ADMINS = "admins";

    /** Longest edge (px) an uploaded avatar is downscaled to, keeping uploads small. */
    private static final int AVATAR_MAX_EDGE = 512;
    private static final int AVATAR_JPEG_QUALITY = 85;

    private static volatile UserRepository instance;

    private final FirebaseFirestore db;

    private UserRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static UserRepository getInstance() {
        if (instance == null) {
            synchronized (UserRepository.class) {
                if (instance == null) {
                    instance = new UserRepository();
                }
            }
        }
        return instance;
    }

    private static String currentUidOrNull() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    // ---------------- Profile reads / writes ----------------

    /** Live profile stream for {@code uid}; the listener is removed by the caller. */
    public ListenerRegistration listenToProfile(String uid, ProfileListener listener) {
        if (uid == null) {
            if (listener != null) listener.onProfile(null, null);
            return null;
        }
        return db.collection(COLLECTION_USERS).document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToProfile: failure", error);
                        if (listener != null) listener.onProfile(null, error);
                        return;
                    }
                    UserProfile profile = null;
                    if (snapshot != null && snapshot.exists()) {
                        profile = snapshot.toObject(UserProfile.class);
                        if (profile != null) profile.setId(snapshot.getId());
                    }
                    if (listener != null) listener.onProfile(profile, null);
                });
    }

    public void getProfile(String uid, ProfileListener listener) {
        if (uid == null) {
            if (listener != null) listener.onProfile(null, new IllegalStateException("Missing user id"));
            return;
        }
        db.collection(COLLECTION_USERS).document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    UserProfile profile = null;
                    if (snapshot != null && snapshot.exists()) {
                        profile = snapshot.toObject(UserProfile.class);
                        if (profile != null) profile.setId(snapshot.getId());
                    }
                    if (listener != null) listener.onProfile(profile, null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "getProfile: failure", error);
                    if (listener != null) listener.onProfile(null, error);
                });
    }

    /**
     * Writes editable profile fields for the signed-in user. Role, verification status and
     * account status are intentionally not writable from here.
     */
    public void saveProfile(Map<String, Object> fields, AuthManager.ActionListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        Map<String, Object> updates = new HashMap<>(fields);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_USERS).document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "saveProfile: success");
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "saveProfile: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    // ---------------- Profile photo ----------------

    /**
     * Downscales the picked image and uploads it to
     * {@code profile_photos/{uid}/avatar.jpg}, then stores the download URL on the profile.
     *
     * <p>Firebase Storage requires a provisioned bucket (Blaze plan on new projects). When no
     * bucket exists {@link #uploadProfilePhoto} reports a clear error instead of crashing.
     */
    public void uploadProfilePhoto(Uri source, PhotoListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(null, new IllegalStateException("Not signed in"));
            return;
        }
        if (source == null) {
            if (listener != null) listener.onComplete(null, new IllegalArgumentException("No image selected"));
            return;
        }
        final FirebaseStorage storage = FirestoreManager.getInstance().getStorageOrNull();
        if (storage == null) {
            if (listener != null) {
                listener.onComplete(null, new IllegalStateException(
                        "Image upload needs Firebase Storage, which this project does not have yet. "
                                + "Enable Storage (Blaze plan) or paste an image URL instead."));
            }
            return;
        }

        new Thread(() -> {
            byte[] bytes;
            try {
                bytes = compress(source);
            } catch (Exception e) {
                Log.w(TAG, "uploadProfilePhoto: could not read image", e);
                post(() -> {
                    if (listener != null) {
                        listener.onComplete(null, new IllegalStateException(
                                "That image could not be read. Try another one."));
                    }
                });
                return;
            }
            if (bytes == null) {
                post(() -> {
                    if (listener != null) {
                        listener.onComplete(null, new IllegalStateException(
                                "That image could not be decoded. Try another one."));
                    }
                });
                return;
            }

            StorageReference reference = storage.getReference()
                    .child("profile_photos").child(uid).child("avatar.jpg");
            UploadTask upload = reference.putBytes(bytes);
            upload.addOnSuccessListener(taskSnapshot -> reference.getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        Map<String, Object> fields = new HashMap<>();
                        fields.put("photoUrl", uri.toString());
                        saveProfile(fields, error -> {
                            if (error != null) {
                                if (listener != null) listener.onComplete(null, error);
                                return;
                            }
                            if (listener != null) listener.onComplete(uri.toString(), null);
                        });
                    })
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "uploadProfilePhoto: download url failed", error);
                        if (listener != null) listener.onComplete(null, error);
                    }))
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "uploadProfilePhoto: upload failed", error);
                        if (listener != null) {
                            listener.onComplete(null, new IllegalStateException(
                                    "Photo upload failed. Check your connection and Storage rules."));
                        }
                    });
        }).start();
    }

    private byte[] compress(Uri source) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = openStream(source)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        int sampleSize = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / sampleSize > AVATAR_MAX_EDGE * 2) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        Bitmap decoded;
        try (InputStream input = openStream(source)) {
            decoded = BitmapFactory.decodeStream(input, null, options);
        }
        if (decoded == null) return null;

        int edge = Math.max(decoded.getWidth(), decoded.getHeight());
        Bitmap scaled = decoded;
        if (edge > AVATAR_MAX_EDGE) {
            float ratio = (float) AVATAR_MAX_EDGE / edge;
            scaled = Bitmap.createScaledBitmap(decoded,
                    Math.max(1, Math.round(decoded.getWidth() * ratio)),
                    Math.max(1, Math.round(decoded.getHeight() * ratio)), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, out);
        if (scaled != decoded) scaled.recycle();
        decoded.recycle();
        return out.toByteArray();
    }

    private InputStream openStream(Uri uri) throws Exception {
        java.io.InputStream stream = com.google.firebase.FirebaseApp.getInstance()
                .getApplicationContext().getContentResolver().openInputStream(uri);
        if (stream == null) throw new java.io.IOException("Cannot open " + uri);
        return stream;
    }

    private static void post(Runnable runnable) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
    }

    // ---------------- Administrator check ----------------

    /**
     * Reads {@code admins/{uid}}. This is the server-side authority used by the security rules;
     * the client only uses the result to show or hide the review screen, never to authorise a
     * write on its own.
     */
    public void checkIsAdmin(AuthManager.ResultListener<Boolean> listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(false, null);
            return;
        }
        db.collection(COLLECTION_ADMINS).document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (listener != null) listener.onComplete(snapshot != null && snapshot.exists(), null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "checkIsAdmin: failure", error);
                    if (listener != null) listener.onComplete(false, error);
                });
    }

    // ---------------- Verification requests ----------------

    public void submitVerificationRequest(VerificationRequest request,
                                          AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        request.setUid(uid);
        request.setStatus(VerificationRequest.STATUS_PENDING);
        request.setSubmittedAt(new java.util.Date());
        db.collection(COLLECTION_VERIFICATION_REQUESTS).add(request)
                .addOnSuccessListener(reference -> {
                    // Mark the profile as pending so the request is visible without a second query.
                    Map<String, Object> fields = new HashMap<>();
                    fields.put("verificationStatus", UserProfile.VERIFICATION_PENDING);
                    saveProfile(fields, error -> {
                        if (listener != null) listener.onComplete(error);
                    });
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "submitVerificationRequest: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Latest request for this user, or {@code null} when none has been submitted. */
    public ListenerRegistration listenToMyVerificationRequest(String uid, RequestListener listener) {
        if (uid == null) {
            if (listener != null) listener.onRequest(null, null);
            return null;
        }
        return db.collection(COLLECTION_VERIFICATION_REQUESTS)
                .whereEqualTo("uid", uid)
                .orderBy("submittedAt", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToMyVerificationRequest: failure", error);
                        if (listener != null) listener.onRequest(null, error);
                        return;
                    }
                    VerificationRequest request = null;
                    if (snapshots != null && !snapshots.isEmpty()) {
                        DocumentSnapshot document = snapshots.getDocuments().get(0);
                        request = document.toObject(VerificationRequest.class);
                        if (request != null) request.setId(document.getId());
                    }
                    if (listener != null) listener.onRequest(request, null);
                });
    }

    /** Pending requests for the administrator review screen. */
    public ListenerRegistration listenToVerificationRequests(String status,
                                                             RequestsListener listener) {
        return db.collection(COLLECTION_VERIFICATION_REQUESTS)
                .whereEqualTo("status", status)
                .orderBy("submittedAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToVerificationRequests: failure", error);
                        if (listener != null) listener.onRequests(null, error);
                        return;
                    }
                    List<VerificationRequest> requests = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            VerificationRequest request =
                                    document.toObject(VerificationRequest.class);
                            if (request != null) {
                                request.setId(document.getId());
                                requests.add(request);
                            }
                        }
                    }
                    if (listener != null) listener.onRequests(requests, null);
                });
    }

    /**
     * Approves or rejects a request and, when approving, updates the applicant's profile
     * (verification status and verified role) in the same transaction.
     *
     * The rules only allow an administrator to write these fields, so the server decides
     * whether this succeeds.
     */
    public void reviewRequest(String requestId, boolean approve, String reviewNote,
                              AuthManager.ActionListener listener) {
        final String reviewer = currentUidOrNull();
        if (reviewer == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference requestRef =
                db.collection(COLLECTION_VERIFICATION_REQUESTS).document(requestId);

        db.runTransaction(transaction -> {
            DocumentSnapshot requestSnapshot = transaction.get(requestRef);
            if (!requestSnapshot.exists()) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                        "Request not found",
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND);
            }
            String applicantUid = requestSnapshot.getString("uid");
            String applicantRole = requestSnapshot.getString("role");

            Map<String, Object> requestUpdate = new HashMap<>();
            requestUpdate.put("status", approve
                    ? VerificationRequest.STATUS_APPROVED
                    : VerificationRequest.STATUS_REJECTED);
            requestUpdate.put("reviewedAt", FieldValue.serverTimestamp());
            requestUpdate.put("reviewedBy", reviewer);
            requestUpdate.put("reviewNote", reviewNote != null ? reviewNote : "");
            transaction.update(requestRef, requestUpdate);

            if (applicantUid != null) {
                Map<String, Object> profileUpdate = new HashMap<>();
                profileUpdate.put("verificationStatus", approve
                        ? UserProfile.VERIFICATION_VERIFIED
                        : UserProfile.VERIFICATION_REJECTED);
                if (approve) {
                    profileUpdate.put("verifiedRole", applicantRole);
                    profileUpdate.put("verifiedAt", FieldValue.serverTimestamp());
                    profileUpdate.put("verifiedBy", reviewer);
                }
                transaction.set(db.collection(COLLECTION_USERS).document(applicantUid),
                        profileUpdate, com.google.firebase.firestore.SetOptions.merge());
            }
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "reviewRequest: success");
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(error -> {
            Log.w(TAG, "reviewRequest: failure", error);
            if (listener != null) listener.onComplete(error);
        });
    }

    // ---------------- Callbacks ----------------

    public interface ProfileListener {
        void onProfile(UserProfile profile, Exception error);
    }

    public interface PhotoListener {
        void onComplete(String photoUrl, Exception error);
    }

    public interface RequestListener {
        void onRequest(VerificationRequest request, Exception error);
    }

    public interface RequestsListener {
        void onRequests(List<VerificationRequest> requests, Exception error);
    }
}
