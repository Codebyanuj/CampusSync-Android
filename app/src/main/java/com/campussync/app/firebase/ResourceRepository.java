package com.campussync.app.firebase;

import android.net.Uri;
import android.util.Log;

import com.campussync.app.model.AcademicResource;
import com.campussync.app.model.ResourceRating;
import com.campussync.app.util.UploadValidator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore/Storage access for the academic knowledge base.
 *
 * <p>Trust model:
 * <ul>
 *   <li>Uploads are validated twice: {@link UploadValidator} before the transfer, then again by
 *       {@code storage.rules} (size and content type) and {@code firestore.rules} (metadata).
 *       The Storage object name is generated here, so a client cannot point at a path outside its
 *       own {@code academic_resources/{uid}/} folder.</li>
 *   <li>A rating is one document per user ({@code resources/{id}/ratings/{uid}}). The aggregate
 *       {@code ratingSum}/{@code ratingCount} moves in the same transaction, and rules accept only
 *       a counter-only update that stays inside 0..5 × count.</li>
 *   <li>Bookmarks are private documents under the user, and the mirrored {@code bookmarkCount}
 *       moves by one.</li>
 * </ul>
 */
public class ResourceRepository {

    private static final String TAG = "ResourceRepository";

    public static final String COLLECTION_RESOURCES = "resources";
    public static final String SUB_RATINGS = "ratings";
    public static final String SUB_BOOKMARKS = "resourceBookmarks";

    /** The library filters client-side, like the event radar, so no composite index is needed. */
    private static final int LIBRARY_LIMIT = 150;

    private static volatile ResourceRepository instance;

    private final FirebaseFirestore db;

    private ResourceRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static ResourceRepository getInstance() {
        if (instance == null) {
            synchronized (ResourceRepository.class) {
                if (instance == null) instance = new ResourceRepository();
            }
        }
        return instance;
    }

    public static String currentUidOrNull() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    private com.google.firebase.firestore.CollectionReference resources() {
        return db.collection(COLLECTION_RESOURCES);
    }

    private DocumentReference resourceRef(String resourceId) {
        return resources().document(resourceId);
    }

    // ------------------------------------------------------------------ library

    /** Live library feed, newest first; the screen applies type/subject/semester/text filters. */
    public ListenerRegistration listenToLibrary(final LibraryListener listener) {
        return resources()
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(LIBRARY_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToLibrary: failure", error);
                        if (listener != null) listener.onLibrary(null, error);
                        return;
                    }
                    List<AcademicResource> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            AcademicResource resource = toResource(document);
                            if (resource != null && !resource.isRemoved()) list.add(resource);
                        }
                    }
                    if (listener != null) listener.onLibrary(list, null);
                });
    }

    public ListenerRegistration listenToResource(String resourceId,
                                                 final ResourceListener listener) {
        if (resourceId == null) {
            if (listener != null) listener.onResource(null, null);
            return null;
        }
        return resourceRef(resourceId).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.w(TAG, "listenToResource: failure", error);
                if (listener != null) listener.onResource(null, error);
                return;
            }
            if (listener != null) {
                listener.onResource(snapshot != null && snapshot.exists()
                        ? toResource(snapshot) : null, null);
            }
        });
    }

    private AcademicResource toResource(DocumentSnapshot document) {
        AcademicResource resource = document.toObject(AcademicResource.class);
        if (resource == null) return null;
        resource.setId(document.getId());
        return resource;
    }

    /** The signed-in user's own rating for a resource, so the dialog can pre-fill it. */
    public ListenerRegistration listenToMyRating(String resourceId,
                                                 final RatingListener listener) {
        final String uid = currentUidOrNull();
        if (resourceId == null || uid == null) {
            if (listener != null) listener.onRating(null, null);
            return null;
        }
        return resourceRef(resourceId).collection(SUB_RATINGS).document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onRating(null, error);
                        return;
                    }
                    ResourceRating rating = null;
                    if (snapshot != null && snapshot.exists()) {
                        rating = snapshot.toObject(ResourceRating.class);
                        if (rating != null) rating.setUid(snapshot.getId());
                    }
                    if (listener != null) listener.onRating(rating, null);
                });
    }

    /** Whether the signed-in user bookmarked this resource. */
    public ListenerRegistration listenToBookmark(String resourceId,
                                                 final BookmarkListener listener) {
        final String uid = currentUidOrNull();
        if (resourceId == null || uid == null) {
            if (listener != null) listener.onBookmark(false, null);
            return null;
        }
        return db.collection(UserRepository.COLLECTION_USERS).document(uid)
                .collection(SUB_BOOKMARKS).document(resourceId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onBookmark(false, error);
                        return;
                    }
                    if (listener != null) {
                        listener.onBookmark(snapshot != null && snapshot.exists(), null);
                    }
                });
    }

    /** The ids the signed-in user saved, so the library can offer a "Saved" filter. */
    public ListenerRegistration listenToBookmarks(final BookmarksListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onBookmarks(new ArrayList<>(), null);
            return null;
        }
        return db.collection(UserRepository.COLLECTION_USERS).document(uid)
                .collection(SUB_BOOKMARKS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onBookmarks(null, error);
                        return;
                    }
                    List<String> ids = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            ids.add(document.getId());
                        }
                    }
                    if (listener != null) listener.onBookmarks(ids, null);
                });
    }

    // ------------------------------------------------------------------ upload

    /**
     * Uploads the picked file to {@code academic_resources/{uid}/…} and then writes the library
     * record. The record is only created after Storage accepted the object, so the library never
     * links to a file that was refused.
     */
    public void uploadResource(AcademicResource resource, Uri file, final UploadListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || resource == null || file == null) {
            if (listener != null) {
                listener.onComplete(null, new IllegalStateException("Not signed in"));
            }
            return;
        }
        String validationError = UploadValidator.validate(
                resource.getFileName(), resource.getMimeType(), resource.getFileSize());
        if (validationError != null) {
            if (listener != null) {
                listener.onComplete(null, new IllegalArgumentException(validationError));
            }
            return;
        }
        FirebaseStorage storage = FirestoreManager.getInstance().getStorageOrNull();
        if (storage == null) {
            if (listener != null) {
                listener.onComplete(null, new IllegalStateException(
                        "Document uploads need Firebase Storage, which this project does not have "
                                + "yet. Enable Storage on the Blaze plan and try again."));
            }
            return;
        }

        final String objectName = System.currentTimeMillis() + "_"
                + UploadValidator.safeFileName(resource.getFileName());
        final String path = "academic_resources/" + uid + "/" + objectName;
        StorageReference reference = storage.getReference().child(path);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(UploadValidator.mimeFor(
                        resource.getFileName(), resource.getMimeType()))
                .build();

        UploadTask task = reference.putFile(file, metadata);
        task.addOnProgressListener(snapshot -> {
            if (listener == null || snapshot.getTotalByteCount() <= 0) return;
            int percent = (int) (100L * snapshot.getBytesTransferred()
                    / snapshot.getTotalByteCount());
            listener.onProgress(percent);
        }).addOnSuccessListener(snapshot -> reference.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    resource.setFileUrl(uri.toString());
                    resource.setFilePath(path);
                    resource.setUploaderId(uid);
                    AuthManager auth = AuthManager.getInstance();
                    resource.setUploaderName(auth.getCurrentUser() != null
                            ? auth.getCurrentUser().getDisplayName() : null);
                    resource.setStatus(AcademicResource.STATUS_PUBLISHED);
                    resource.setDownloadCount(0);
                    resource.setBookmarkCount(0);
                    resource.setRatingSum(0);
                    resource.setRatingCount(0);
                    resource.setCreatedAt(new java.util.Date());
                    resource.setUpdatedAt(new java.util.Date());
                    resources().add(resource)
                            .addOnSuccessListener(documentReference -> {
                                if (listener != null) {
                                    listener.onComplete(documentReference.getId(), null);
                                }
                            })
                            .addOnFailureListener(error -> {
                                // Do not leave an orphaned, publicly readable file behind.
                                reference.delete();
                                Log.w(TAG, "uploadResource: record failed", error);
                                if (listener != null) listener.onComplete(null, error);
                            });
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "uploadResource: download url failed", error);
                    if (listener != null) listener.onComplete(null, error);
                }))
                .addOnFailureListener(error -> {
                    Log.w(TAG, "uploadResource: upload failed", error);
                    if (listener != null) {
                        listener.onComplete(null, new IllegalStateException(
                                "Upload failed. Check your connection and the Storage rules."));
                    }
                });
    }

    // ------------------------------------------------------------------ ratings & bookmarks

    /**
     * Creates or edits the signed-in user's rating and moves the resource's rating counters by
     * the same delta inside one transaction, so the average never drifts.
     */
    public void rate(AcademicResource resource, long stars, String feedback,
                     String raterName, final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || resource == null || resource.getId() == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        if (stars < 1 || stars > 5) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Choose between 1 and 5 stars"));
            }
            return;
        }
        final DocumentReference reference = resourceRef(resource.getId());
        final DocumentReference ratingRef = reference.collection(SUB_RATINGS).document(uid);

        db.runTransaction(transaction -> {
            DocumentSnapshot resourceSnapshot = transaction.get(reference);
            if (!resourceSnapshot.exists()) {
                throw new IllegalStateException("This resource is no longer available");
            }
            DocumentSnapshot ratingSnapshot = transaction.get(ratingRef);
            long previous = ratingSnapshot.exists() ? asLong(ratingSnapshot, "stars") : 0L;
            long sum = Math.max(0L, asLong(resourceSnapshot, "ratingSum") - previous) + stars;
            long count = ratingSnapshot.exists()
                    ? asLong(resourceSnapshot, "ratingCount")
                    : asLong(resourceSnapshot, "ratingCount") + 1;

            Map<String, Object> rating = new HashMap<>();
            rating.put("stars", stars);
            rating.put("feedback", feedback != null ? feedback : "");
            rating.put("raterName", raterName);
            rating.put("createdAt", ratingSnapshot.exists()
                    ? ratingSnapshot.get("createdAt") : FieldValue.serverTimestamp());
            rating.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(ratingRef, rating);

            Map<String, Object> update = new HashMap<>();
            update.put("ratingSum", sum);
            update.put("ratingCount", count);
            update.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(reference, update);
            return null;
        }).addOnSuccessListener(unused -> {
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(error -> {
            Log.w(TAG, "rate: failure", error);
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Bookmarks or un-bookmarks a resource and mirrors the count on the resource document. */
    public void toggleBookmark(AcademicResource resource, boolean bookmarked,
                               final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || resource == null || resource.getId() == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference reference = resourceRef(resource.getId());
        final DocumentReference bookmarkRef = db.collection(UserRepository.COLLECTION_USERS)
                .document(uid).collection(SUB_BOOKMARKS).document(resource.getId());

        db.runTransaction(transaction -> {
            DocumentSnapshot resourceSnapshot = transaction.get(reference);
            if (!resourceSnapshot.exists()) {
                throw new IllegalStateException("This resource is no longer available");
            }
            long count = asLong(resourceSnapshot, "bookmarkCount");
            if (bookmarked) {
                Map<String, Object> bookmark = new HashMap<>();
                bookmark.put("resourceId", resource.getId());
                bookmark.put("title", resource.getTitle());
                bookmark.put("subject", resource.getSubject());
                bookmark.put("fileUrl", resource.getFileUrl());
                bookmark.put("savedAt", FieldValue.serverTimestamp());
                transaction.set(bookmarkRef, bookmark);
                count += 1;
            } else {
                transaction.delete(bookmarkRef);
                count = Math.max(0L, count - 1);
            }
            Map<String, Object> update = new HashMap<>();
            update.put("bookmarkCount", count);
            update.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(reference, update);
            return null;
        }).addOnSuccessListener(unused -> {
            if (listener != null) listener.onComplete(null);
        }).addOnFailureListener(error -> {
            Log.w(TAG, "toggleBookmark: failure", error);
            if (listener != null) listener.onComplete(error);
        });
    }

    /** Counts one download. Rules accept at most a single step per write. */
    public void recordDownload(AcademicResource resource) {
        if (resource == null || resource.getId() == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put("downloadCount", resource.getDownloadCount() + 1);
        update.put("updatedAt", FieldValue.serverTimestamp());
        resourceRef(resource.getId()).update(update)
                .addOnFailureListener(error -> Log.w(TAG, "recordDownload: failure", error));
    }

    /** Owner edit of the descriptive fields (the uploaded file itself is immutable). */
    public void updateResource(String resourceId, Map<String, Object> fields,
                               final AuthManager.ActionListener listener) {
        if (resourceId == null) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Missing resource"));
            }
            return;
        }
        Map<String, Object> updates = new HashMap<>(fields);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        resourceRef(resourceId).update(updates)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "updateResource: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Deletes a resource the caller uploaded (or any resource, for an administrator). */
    public void deleteResource(AcademicResource resource,
                               final AuthManager.ActionListener listener) {
        if (resource == null || resource.getId() == null) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Missing resource"));
            }
            return;
        }
        final String path = resource.getFilePath();
        resourceRef(resource.getId()).delete()
                .addOnSuccessListener(unused -> {
                    FirebaseStorage storage = FirestoreManager.getInstance().getStorageOrNull();
                    if (storage != null && path != null) {
                        storage.getReference().child(path).delete();
                    }
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "deleteResource: failure", error);
                    if (listener != null) listener.onComplete(error);
                });
    }

    /** Every rating for a resource, newest first. */
    public ListenerRegistration listenToRatings(String resourceId,
                                                final RatingsListener listener) {
        if (resourceId == null) {
            if (listener != null) listener.onRatings(new ArrayList<>(), null);
            return null;
        }
        return resourceRef(resourceId).collection(SUB_RATINGS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onRatings(null, error);
                        return;
                    }
                    List<ResourceRating> ratings = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            ResourceRating rating = document.toObject(ResourceRating.class);
                            if (rating == null) continue;
                            rating.setUid(document.getId());
                            ratings.add(rating);
                        }
                    }
                    Collections.sort(ratings, new Comparator<ResourceRating>() {
                        @Override
                        public int compare(ResourceRating left, ResourceRating right) {
                            long a = left.getCreatedAt() != null
                                    ? left.getCreatedAt().getTime() : 0L;
                            long b = right.getCreatedAt() != null
                                    ? right.getCreatedAt().getTime() : 0L;
                            if (a == b) return 0;
                            return a > b ? -1 : 1;
                        }
                    });
                    if (listener != null) listener.onRatings(ratings, null);
                });
    }

    private static long asLong(DocumentSnapshot snapshot, String field) {
        Long value = snapshot.getLong(field);
        return value != null ? value : 0L;
    }

    // ------------------------------------------------------------------ callbacks

    public interface LibraryListener {
        void onLibrary(List<AcademicResource> resources, Exception error);
    }

    public interface ResourceListener {
        void onResource(AcademicResource resource, Exception error);
    }

    public interface RatingListener {
        void onRating(ResourceRating rating, Exception error);
    }

    public interface BookmarkListener {
        void onBookmark(boolean bookmarked, Exception error);
    }

    public interface BookmarksListener {
        void onBookmarks(List<String> resourceIds, Exception error);
    }

    public interface RatingsListener {
        void onRatings(List<ResourceRating> ratings, Exception error);
    }

    public interface UploadListener {
        void onProgress(int percent);

        void onComplete(String resourceId, Exception error);
    }
}
