package com.campussync.app.firebase;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.campussync.app.model.Story;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore / Storage access for 24-hour stories.
 *
 * <p>Expiry uses the trusted server timestamp in {@code createdAt}: the query only asks for
 * stories newer than the 24 hour window, the client hides anything older, and expired stories of
 * the signed-in user are deleted opportunistically. A scheduled Cloud Function can prune the rest
 * (documented in the README).
 */
public class StoryRepository {

    private static final String TAG = "StoryRepository";

    public static final String COLLECTION_STORIES = "stories";
    public static final String SUB_VIEWS = "views";
    public static final String SUB_REPLIES = "replies";

    /** Largest accepted video upload. */
    private static final long MAX_VIDEO_BYTES = 30L * 1024L * 1024L;

    private static volatile StoryRepository instance;

    private final FirebaseFirestore db;

    private StoryRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static StoryRepository getInstance() {
        if (instance == null) {
            synchronized (StoryRepository.class) {
                if (instance == null) instance = new StoryRepository();
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ reads

    /** Active (last 24 hours) stories, newest first. Expired own stories are pruned best-effort. */
    public ListenerRegistration listenToActiveStories(final StoriesListener listener) {
        final Date cutoff = new Date(System.currentTimeMillis() - Story.TTL_MS);
        return db.collection(COLLECTION_STORIES)
                .whereGreaterThan("createdAt", cutoff)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToActiveStories: failure", error);
                        if (listener != null) listener.onStories(null, error);
                        return;
                    }
                    final String uid = SocialRepository.currentUidOrNull();
                    List<Story> stories = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Story story = doc.toObject(Story.class);
                            if (story == null) continue;
                            story.setId(doc.getId());
                            if (story.isExpired()) {
                                // Own expired story: clean it up. Someone else's is filtered out.
                                if (uid != null && uid.equals(story.getAuthorId())) {
                                    doc.getReference().delete();
                                }
                                continue;
                            }
                            story.setOwnedByMe(uid != null && uid.equals(story.getAuthorId()));
                            stories.add(story);
                        }
                    }
                    if (listener != null) listener.onStories(stories, null);
                });
    }

    // ------------------------------------------------------------------ writes

    /** Uploads the picked image or video and publishes the story document. */
    public void uploadStory(final Uri source, final String mediaType,
                            final UploadListener listener) {
        final String uid = SocialRepository.currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onUploaded(null, new IllegalStateException("Not signed in"));
            return;
        }
        if (source == null) {
            if (listener != null) listener.onUploaded(null, new IllegalArgumentException("No media selected"));
            return;
        }
        FirebaseStorage storage = FirestoreManager.getInstance().getStorageOrNull();
        if (storage == null) {
            if (listener != null) listener.onUploaded(null, new IllegalStateException(
                    "Story upload needs Firebase Storage, which this project does not have configured."));
            return;
        }
        final boolean video = Story.TYPE_VIDEO.equals(mediaType);
        if (video) {
            long size = sizeOf(source);
            if (size > MAX_VIDEO_BYTES) {
                if (listener != null) listener.onUploaded(null, new IllegalArgumentException(
                        "That video is larger than 30 MB. Pick a shorter clip."));
                return;
            }
        }
        final String extension = video ? "mp4" : "jpg";
        final String contentType = video ? "video/mp4" : "image/jpeg";
        final DocumentReference storyRef = db.collection(COLLECTION_STORIES).document();
        final StorageReference mediaRef = storage.getReference()
                .child("stories").child(uid).child(storyRef.getId() + "." + extension);

        UserRepository.getInstance().getProfile(uid, (profile, profileError) -> {
            StorageMetadata metadata = new StorageMetadata.Builder()
                    .setContentType(contentType)
                    .build();
            mediaRef.putFile(source, metadata)
                    .addOnSuccessListener(taskSnapshot -> mediaRef.getDownloadUrl()
                            .addOnSuccessListener(downloadUri -> {
                                Map<String, Object> data = new HashMap<>();
                                data.put("authorId", uid);
                                data.put("authorName", profile != null && profile.getDisplayName() != null
                                        ? profile.getDisplayName() : "Campus member");
                                data.put("authorPhotoUrl", profile != null ? profile.getPhotoUrl() : null);
                                data.put("mediaUrl", downloadUri.toString());
                                data.put("mediaType", video ? Story.TYPE_VIDEO : Story.TYPE_IMAGE);
                                data.put("createdAt", FieldValue.serverTimestamp());
                                data.put("viewCount", 0);
                                storyRef.set(data).addOnCompleteListener(task -> {
                                    if (listener != null) {
                                        listener.onUploaded(storyRef.getId(), task.getException());
                                    }
                                });
                            })
                            .addOnFailureListener(error -> {
                                if (listener != null) listener.onUploaded(null, error);
                            }))
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "uploadStory: upload failed", error);
                        if (listener != null) listener.onUploaded(null, new IllegalStateException(
                                "Story upload failed. Check your connection and Storage rules."));
                    });
        });
    }

    private long sizeOf(Uri uri) {
        try {
            ContentResolver resolver = com.google.firebase.FirebaseApp.getInstance()
                    .getApplicationContext().getContentResolver();
            try (android.content.res.AssetFileDescriptor descriptor =
                         resolver.openAssetFileDescriptor(uri, "r")) {
                return descriptor != null ? descriptor.getLength() : 0L;
            }
        } catch (Exception e) {
            Log.w(TAG, "sizeOf: could not read media size", e);
            return 0L;
        }
    }

    public void deleteStory(String storyId, AuthManager.ActionListener listener) {
        db.collection(COLLECTION_STORIES).document(storyId).delete()
                .addOnCompleteListener(task -> {
                    if (listener != null) listener.onComplete(task.getException());
                });
    }

    /**
     * Records a view once per user. The view document is the idempotency key, and both writes
     * happen in one transaction so the counter cannot drift.
     */
    public void trackView(final String storyId) {
        final String uid = SocialRepository.currentUidOrNull();
        if (uid == null) return;
        final DocumentReference storyRef = db.collection(COLLECTION_STORIES).document(storyId);
        final DocumentReference viewRef = storyRef.collection(SUB_VIEWS).document(uid);
        db.runTransaction(transaction -> {
            DocumentSnapshot viewSnapshot = transaction.get(viewRef);
            if (viewSnapshot.exists()) return null;
            Map<String, Object> view = new HashMap<>();
            view.put("viewedAt", FieldValue.serverTimestamp());
            transaction.set(viewRef, view);
            transaction.update(storyRef, "viewCount", FieldValue.increment(1));
            return null;
        }).addOnFailureListener(error -> Log.w(TAG, "trackView: failure", error));
    }

    /** Replies are stored under the story for the author; text only. */
    public void replyToStory(String storyId, String content, AuthManager.ActionListener listener) {
        final String uid = SocialRepository.currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        UserRepository.getInstance().getProfile(uid, (profile, profileError) -> {
            Map<String, Object> reply = new HashMap<>();
            reply.put("authorId", uid);
            reply.put("authorName", profile != null && profile.getDisplayName() != null
                    ? profile.getDisplayName() : "Campus member");
            reply.put("content", content);
            reply.put("createdAt", FieldValue.serverTimestamp());
            db.collection(COLLECTION_STORIES).document(storyId)
                    .collection(SUB_REPLIES).add(reply)
                    .addOnCompleteListener(task -> {
                        if (listener != null) listener.onComplete(task.getException());
                    });
        });
    }

    /** Guesses a MIME type so the upload can be labelled for the story viewer. */
    public static String resolveMediaType(Uri uri, String mimeType) {
        String type = mimeType;
        if (type == null && uri != null) {
            type = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()));
        }
        return type != null && type.startsWith("video") ? Story.TYPE_VIDEO : Story.TYPE_IMAGE;
    }

    public interface StoriesListener {
        void onStories(List<Story> stories, Exception error);
    }

    public interface UploadListener {
        void onUploaded(String storyId, Exception error);
    }
}
