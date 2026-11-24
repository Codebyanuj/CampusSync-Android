package com.campussync.app.firebase;

import android.net.Uri;
import android.util.Log;

import com.campussync.app.model.Comment;
import com.campussync.app.model.ContentReport;
import com.campussync.app.model.Post;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Firestore / Storage access for the social feed.
 *
 * <p>Security model: public post documents never contain an author id when they are anonymous.
 * The restricted attribution lives in {@code anonymousAuthors/{contentId}} which only the author
 * and administrators may read (enforced by {@code firestore.rules}). No client query can join a
 * post to its author; the app therefore resolves ownership with one targeted read per anonymous
 * item, which simply fails for everyone except the author and admins and is treated as
 * "not mine".
 */
public class SocialRepository {

    private static final String TAG = "SocialRepository";

    public static final String COLLECTION_POSTS = "posts";
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_ANONYMOUS = "anonymousAuthors";
    public static final String COLLECTION_REPORTS = "reports";

    public static final String SUB_LIKES = "likes";
    public static final String SUB_COMMENTS = "comments";
    public static final String SUB_BOOKMARKS = "bookmarks";
    public static final String SUB_FOLLOWING = "following";

    public static final String MODE_COLLEGE = "college";
    public static final String MODE_FOLLOWING = "following";
    public static final String MODE_TRENDING = "trending";

    public static final int PAGE_SIZE = 15;
    private static final int FOLLOWING_SCAN_LIMIT = 60;

    private static volatile SocialRepository instance;

    private final FirebaseFirestore db;

    /** Cursor for the current real-time page, used by {@link #loadMoreFeed}. */
    private DocumentSnapshot lastPageCursor;
    private boolean canLoadMore;

    private SocialRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static SocialRepository getInstance() {
        if (instance == null) {
            synchronized (SocialRepository.class) {
                if (instance == null) instance = new SocialRepository();
            }
        }
        return instance;
    }

    public static String currentUidOrNull() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    // ------------------------------------------------------------------ feeds

    /**
     * Live feed. {@code college} narrows the campus feed; following and trending ignore it.
     * The callback reports whether more pages exist.
     */
    public ListenerRegistration listenToFeed(final String mode, final String college,
                                             final FeedListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onFeed(new ArrayList<>(), false, null);
            return null;
        }
        lastPageCursor = null;
        canLoadMore = false;

        final ListenerRegistration[] holder = new ListenerRegistration[1];
        if (MODE_FOLLOWING.equals(mode)) {
            loadFollowingIds(uid, ids -> {
                Query query = db.collection(COLLECTION_POSTS)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(FOLLOWING_SCAN_LIMIT);
                holder[0] = query.addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToFeed(following): failure", error);
                        if (listener != null) listener.onFeed(null, false, error);
                        return;
                    }
                    List<Post> posts = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Post post = toPost(doc);
                            if (post == null) continue;
                            // Only attributed posts can belong to someone you follow; anonymous
                            // posts stay in the campus and trending feeds.
                            if (!post.isAnonymous() && post.getAuthorId() != null
                                    && ids.contains(post.getAuthorId())) {
                                posts.add(post);
                            }
                        }
                    }
                    decoratePosts(posts, uid,
                            (decorated, decorateError) ->
                                    listener.onFeed(decorated, false, decorateError));
                });
            });
            return () -> {
                if (holder[0] != null) holder[0].remove();
            };
        }

        Query query;
        if (MODE_TRENDING.equals(mode)) {
            query = db.collection(COLLECTION_POSTS)
                    .orderBy("likeCount", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE);
        } else if (college != null && !college.isEmpty()) {
            query = db.collection(COLLECTION_POSTS)
                    .whereEqualTo("college", college)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE);
        } else {
            query = db.collection(COLLECTION_POSTS)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE);
        }

        holder[0] = query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                Log.w(TAG, "listenToFeed: failure", error);
                if (listener != null) listener.onFeed(null, false, error);
                return;
            }
            List<Post> posts = postsFrom(snapshots);
            if (snapshots != null && !snapshots.isEmpty()) {
                lastPageCursor = snapshots.getDocuments()
                        .get(snapshots.getDocuments().size() - 1);
            }
            canLoadMore = snapshots != null && snapshots.size() >= PAGE_SIZE;
            final boolean hasMore = canLoadMore;
            decoratePosts(posts, uid,
                    (decorated, decorateError) ->
                            listener.onFeed(decorated, hasMore, decorateError));
        });
        return holder[0];
    }

    /** Appends the next page. Not supported for the following feed (it scans the latest posts). */
    public void loadMoreFeed(final String mode, final String college,
                             final FeedListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null || lastPageCursor == null || !canLoadMore
                || MODE_FOLLOWING.equals(mode)) {
            if (listener != null) listener.onFeed(new ArrayList<>(), false, null);
            return;
        }
        Query query;
        if (MODE_TRENDING.equals(mode)) {
            query = db.collection(COLLECTION_POSTS)
                    .orderBy("likeCount", Query.Direction.DESCENDING)
                    .startAfter(lastPageCursor)
                    .limit(PAGE_SIZE);
        } else if (college != null && !college.isEmpty()) {
            query = db.collection(COLLECTION_POSTS)
                    .whereEqualTo("college", college)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(lastPageCursor)
                    .limit(PAGE_SIZE);
        } else {
            query = db.collection(COLLECTION_POSTS)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(lastPageCursor)
                    .limit(PAGE_SIZE);
        }
        query.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                if (listener != null) listener.onFeed(null, false, task.getException());
                return;
            }
            QuerySnapshot snapshots = task.getResult();
            List<Post> posts = postsFrom(snapshots);
            if (snapshots != null && !snapshots.isEmpty()) {
                lastPageCursor = snapshots.getDocuments()
                        .get(snapshots.getDocuments().size() - 1);
            }
            canLoadMore = snapshots != null && snapshots.size() >= PAGE_SIZE;
            final boolean hasMore = canLoadMore;
            decoratePosts(posts, uid,
                    (decorated, decorateError) ->
                            listener.onFeed(decorated, hasMore, decorateError));
        });
    }

    private List<Post> postsFrom(QuerySnapshot snapshots) {
        List<Post> posts = new ArrayList<>();
        if (snapshots == null) return posts;
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Post post = toPost(doc);
            if (post != null) posts.add(post);
        }
        return posts;
    }

    private Post toPost(DocumentSnapshot doc) {
        Post post = doc.toObject(Post.class);
        if (post != null) post.setId(doc.getId());
        return post;
    }

    /** Resolves like / bookmark / ownership flags for a page of posts with one tiny read each. */
    private void decoratePosts(final List<Post> posts, final String uid,
                               final DecorateListener listener) {
        if (posts.isEmpty()) {
            listener.onDecorated(posts, null);
            return;
        }
        List<Task<?>> tasks = new ArrayList<>();
        List<String> anonymousIds = new ArrayList<>();
        for (final Post post : posts) {
            if (post.getId() == null) continue;
            post.setOwnedByMe(uid.equals(post.getAuthorId()));

            Task<DocumentSnapshot> likeTask = db.collection(COLLECTION_POSTS)
                    .document(post.getId()).collection(SUB_LIKES).document(uid).get();
            likeTask.addOnSuccessListener(snapshot ->
                    post.setLikedByMe(snapshot != null && snapshot.exists()));
            tasks.add(likeTask);

            Task<DocumentSnapshot> bookmarkTask = db.collection(COLLECTION_USERS)
                    .document(uid).collection(SUB_BOOKMARKS).document(post.getId()).get();
            bookmarkTask.addOnSuccessListener(snapshot ->
                    post.setBookmarkedByMe(snapshot != null && snapshot.exists()));
            tasks.add(bookmarkTask);

            if (post.isAnonymous()) anonymousIds.add(post.getId());
        }
        loadOwnedAnonymousIds(anonymousIds, owned -> {
            for (Post post : posts) {
                if (post.isAnonymous()) post.setOwnedByMe(owned.contains(post.getId()));
            }
            Tasks.whenAllComplete(tasks).addOnCompleteListener(done ->
                    listener.onDecorated(posts, null));
        });
    }

    /**
     * Resolves which of the given anonymous content ids are owned by the signed-in user.
     * Each read only succeeds for the author or an administrator; failures mean "not mine".
     */
    public void loadOwnedAnonymousIds(final List<String> contentIds, final OwnedIdsListener listener) {
        if (contentIds == null || contentIds.isEmpty()) {
            if (listener != null) listener.onOwned(new HashSet<>());
            return;
        }
        final Set<String> owned = new HashSet<>();
        List<Task<?>> tasks = new ArrayList<>();
        for (final String id : contentIds) {
            Task<DocumentSnapshot> task = db.collection(COLLECTION_ANONYMOUS).document(id).get();
            task.addOnSuccessListener(snapshot -> {
                if (snapshot != null && snapshot.exists()) owned.add(id);
            });
            tasks.add(task);
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
            if (listener != null) listener.onOwned(owned);
        });
    }

    // ------------------------------------------------------------------ write posts

    public void createPost(final String content, final boolean anonymous, final String college,
                           final List<Uri> images, final PostCreatedListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onCreated(null, new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference docRef = db.collection(COLLECTION_POSTS).document();
        UserRepository.getInstance().getProfile(uid, (profile, profileError) -> {
            final String name = profile != null && profile.getDisplayName() != null
                    ? profile.getDisplayName() : "Campus member";
            final String photo = profile != null ? profile.getPhotoUrl() : null;
            final String role = profile != null ? profile.getRole() : null;
            final String userCollege = college != null && !college.isEmpty()
                    ? college : (profile != null ? profile.getCollege() : null);

            List<Uri> picks = images != null ? images : Collections.<Uri>emptyList();
            uploadImages(uid, docRef.getId(), picks, 0, new ArrayList<>(),
                    (urls, uploadError) -> {
                        if (uploadError != null) {
                            if (listener != null) listener.onCreated(null, uploadError);
                            return;
                        }
                        Map<String, Object> data = new HashMap<>();
                        data.put("content", content != null ? content : "");
                        data.put("anonymous", anonymous);
                        data.put("college", userCollege != null ? userCollege : "");
                        data.put("imageUrls", urls);
                        data.put("createdAt", FieldValue.serverTimestamp());
                        data.put("updatedAt", FieldValue.serverTimestamp());
                        data.put("likeCount", 0);
                        data.put("commentCount", 0);
                        data.put("shareCount", 0);
                        data.put("edited", false);
                        if (!anonymous) {
                            data.put("authorId", uid);
                            data.put("authorName", name);
                            data.put("authorPhotoUrl", photo);
                            data.put("authorRole", role);
                        }
                        docRef.set(data).addOnCompleteListener(task -> {
                            if (!task.isSuccessful()) {
                                if (listener != null) listener.onCreated(null, task.getException());
                                return;
                            }
                            if (anonymous) {
                                createAnonymousMapping(docRef.getId(), "post", rawError -> {
                                    if (listener != null) {
                                        listener.onCreated(docRef.getId(), rawError);
                                    }
                                });
                            } else if (listener != null) {
                                listener.onCreated(docRef.getId(), null);
                            }
                        });
                    });
        });
    }

    private void uploadImages(final String uid, final String postId, final List<Uri> images,
                              final int index, final List<String> urls,
                              final UploadListListener listener) {
        if (index >= images.size()) {
            listener.onUploaded(urls, null);
            return;
        }
        FirebaseStorage storage = FirestoreManager.getInstance().getStorageOrNull();
        if (storage == null) {
            listener.onUploaded(urls, new IllegalStateException(
                    "Image upload needs Firebase Storage, which this project does not have configured."));
            return;
        }
        StorageReference reference = storage.getReference()
                .child("post_images").child(uid).child(postId).child(index + ".jpg");
        reference.putFile(images.get(index))
                .addOnSuccessListener(snapshot -> reference.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            urls.add(uri.toString());
                            uploadImages(uid, postId, images, index + 1, urls, listener);
                        })
                        .addOnFailureListener(error -> listener.onUploaded(urls, error)))
                .addOnFailureListener(error -> {
                    Log.w(TAG, "uploadImages: failure", error);
                    listener.onUploaded(urls, error);
                });
    }

    public void editPost(String postId, String content, AuthManager.ActionListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("content", content != null ? content : "");
        updates.put("edited", true);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_POSTS).document(postId).update(updates)
                .addOnCompleteListener(task -> {
                    if (listener != null) listener.onComplete(task.getException());
                });
    }

    /** Deletes the post, its anonymous attribution (if any) and its like documents. */
    public void deletePost(final String postId, final AuthManager.ActionListener listener) {
        final DocumentReference postRef = db.collection(COLLECTION_POSTS).document(postId);
        postRef.get().addOnCompleteListener(task -> {
            final boolean anonymous = task.isSuccessful() && task.getResult() != null
                    && Boolean.TRUE.equals(task.getResult().getBoolean("anonymous"));
            postRef.delete().addOnCompleteListener(deleteTask -> {
                if (!deleteTask.isSuccessful()) {
                    if (listener != null) listener.onComplete(deleteTask.getException());
                    return;
                }
                if (!anonymous) {
                    if (listener != null) listener.onComplete(null);
                    return;
                }
                db.collection(COLLECTION_ANONYMOUS).document(postId).delete()
                        .addOnCompleteListener(mappingTask -> {
                            if (listener != null) listener.onComplete(null);
                        });
            });
        });
    }

    // ------------------------------------------------------------------ reactions

    public void setLiked(final String postId, final boolean liked,
                         final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference postRef = db.collection(COLLECTION_POSTS).document(postId);
        final DocumentReference likeRef = postRef.collection(SUB_LIKES).document(uid);
        db.runTransaction(transaction -> {
            DocumentSnapshot likeSnapshot = transaction.get(likeRef);
            DocumentSnapshot postSnapshot = transaction.get(postRef);
            long likeCount = postSnapshot.getLong("likeCount") != null
                    ? postSnapshot.getLong("likeCount") : 0L;
            if (liked) {
                if (!likeSnapshot.exists()) {
                    Map<String, Object> like = new HashMap<>();
                    like.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(likeRef, like);
                    transaction.update(postRef, "likeCount", likeCount + 1);
                }
            } else if (likeSnapshot.exists()) {
                transaction.delete(likeRef);
                transaction.update(postRef, "likeCount", Math.max(0L, likeCount - 1));
            }
            return null;
        }).addOnCompleteListener(task -> {
            if (listener != null) listener.onComplete(task.getException());
        });
    }

    public void setBookmarked(final Post post, final boolean bookmarked,
                              final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        DocumentReference ref = db.collection(COLLECTION_USERS).document(uid)
                .collection(SUB_BOOKMARKS).document(post.getId());
        if (bookmarked) {
            Map<String, Object> saved = new HashMap<>();
            saved.put("postId", post.getId());
            saved.put("preview", previewOf(post.getContent()));
            saved.put("authorName", post.isAnonymous() ? "Anonymous" : post.getAuthorName());
            saved.put("savedAt", FieldValue.serverTimestamp());
            ref.set(saved, SetOptions.merge())
                    .addOnCompleteListener(task -> {
                        if (listener != null) listener.onComplete(task.getException());
                    });
        } else {
            ref.delete().addOnCompleteListener(task -> {
                if (listener != null) listener.onComplete(task.getException());
            });
        }
    }

    public void sharePost(final String postId, final AuthManager.ActionListener listener) {
        db.collection(COLLECTION_POSTS).document(postId)
                .update("shareCount", FieldValue.increment(1))
                .addOnCompleteListener(task -> {
                    if (listener != null) listener.onComplete(null);
                });
    }

    private static String previewOf(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) + "…" : trimmed;
    }

    // ------------------------------------------------------------------ comments

    public ListenerRegistration listenToComments(final String postId, final CommentsListener listener) {
        return db.collection(COLLECTION_POSTS).document(postId)
                .collection(SUB_COMMENTS)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(300)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToComments: failure", error);
                        if (listener != null) listener.onComments(null, error);
                        return;
                    }
                    final List<Comment> comments = new ArrayList<>();
                    List<String> anonymousIds = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Comment comment = doc.toObject(Comment.class);
                            if (comment == null) continue;
                            comment.setId(doc.getId());
                            comment.setPostId(postId);
                            comments.add(comment);
                            if (comment.isAnonymous()) anonymousIds.add(comment.getId());
                        }
                    }
                    final String uid = currentUidOrNull();
                    loadOwnedAnonymousIds(anonymousIds, owned -> {
                        for (Comment comment : comments) {
                            boolean mine = uid != null && uid.equals(comment.getAuthorId());
                            comment.setOwnedByMe(mine || owned.contains(comment.getId()));
                        }
                        if (listener != null) listener.onComments(comments, null);
                    });
                });
    }

    /**
     * Adds a comment or a reply. Anonymous comments keep their author out of the public document;
     * replies carry the parent comment id so the UI can nest them.
     */
    public void addComment(final String postId, final String content, final String parentId,
                           final boolean anonymous, final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference postRef = db.collection(COLLECTION_POSTS).document(postId);
        final DocumentReference commentRef = postRef.collection(SUB_COMMENTS).document();
        UserRepository.getInstance().getProfile(uid, (profile, profileError) -> {
            Map<String, Object> data = new HashMap<>();
            data.put("content", content);
            data.put("anonymous", anonymous);
            data.put("parentId", parentId != null ? parentId : "");
            data.put("createdAt", FieldValue.serverTimestamp());
            if (!anonymous) {
                data.put("authorId", uid);
                data.put("authorName", profile != null && profile.getDisplayName() != null
                        ? profile.getDisplayName() : "Campus member");
                data.put("authorPhotoUrl", profile != null ? profile.getPhotoUrl() : null);
            }
            commentRef.set(data).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    if (listener != null) listener.onComplete(task.getException());
                    return;
                }
                postRef.update("commentCount", FieldValue.increment(1));
                if (anonymous) {
                    createAnonymousMapping(commentRef.getId(), "postComment", listener);
                } else if (listener != null) {
                    listener.onComplete(null);
                }
            });
        });
    }

    public void deleteComment(final String postId, final String commentId,
                              final AuthManager.ActionListener listener) {
        final DocumentReference postRef = db.collection(COLLECTION_POSTS).document(postId);
        postRef.collection(SUB_COMMENTS).document(commentId).delete()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        if (listener != null) listener.onComplete(task.getException());
                        return;
                    }
                    postRef.update("commentCount", FieldValue.increment(-1));
                    db.collection(COLLECTION_ANONYMOUS).document(commentId).delete();
                    if (listener != null) listener.onComplete(null);
                });
    }

    // ------------------------------------------------------------------ following

    public void loadFollowingIds(String uid, FollowingListener listener) {
        final Set<String> ids = new HashSet<>();
        if (uid == null) {
            if (listener != null) listener.onFollowing(ids);
            return;
        }
        db.collection(COLLECTION_USERS).document(uid).collection(SUB_FOLLOWING)
                .limit(300).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            ids.add(doc.getId());
                        }
                    }
                    if (listener != null) listener.onFollowing(ids);
                });
    }

    public void setFollowing(final String targetUid, final boolean following,
                             final AuthManager.ActionListener listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        if (uid.equals(targetUid)) {
            if (listener != null) listener.onComplete(new IllegalArgumentException(
                    "You cannot follow yourself"));
            return;
        }
        DocumentReference ref = db.collection(COLLECTION_USERS).document(uid)
                .collection(SUB_FOLLOWING).document(targetUid);
        if (following) {
            Map<String, Object> data = new HashMap<>();
            data.put("followingId", targetUid);
            data.put("since", FieldValue.serverTimestamp());
            ref.set(data, SetOptions.merge()).addOnCompleteListener(task -> {
                if (listener != null) listener.onComplete(task.getException());
            });
        } else {
            ref.delete().addOnCompleteListener(task -> {
                if (listener != null) listener.onComplete(task.getException());
            });
        }
    }

    public void isFollowing(String targetUid, final ResultListener<Boolean> listener) {
        String uid = currentUidOrNull();
        if (uid == null || targetUid == null) {
            if (listener != null) listener.onComplete(false, null);
            return;
        }
        db.collection(COLLECTION_USERS).document(uid).collection(SUB_FOLLOWING)
                .document(targetUid).get()
                .addOnCompleteListener(task -> {
                    boolean follows = task.isSuccessful() && task.getResult() != null
                            && task.getResult().exists();
                    if (listener != null) listener.onComplete(follows, null);
                });
    }

    // ------------------------------------------------------------------ moderation

    public void report(ContentReport report, final AuthManager.ActionListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        report.setReporterId(uid);
        report.setStatus(ContentReport.STATUS_OPEN);
        db.collection(COLLECTION_REPORTS).add(report)
                .addOnCompleteListener(task -> {
                    if (listener != null) listener.onComplete(task.getException());
                });
    }

    /** Administrator-only read of the restricted attribution for anonymous content. */
    public void getAnonymousAuthor(String contentId,
                                   final ResultListener<DocumentSnapshot> listener) {
        db.collection(COLLECTION_ANONYMOUS).document(contentId).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        if (listener != null) listener.onComplete(null, task.getException());
                        return;
                    }
                    if (listener != null) listener.onComplete(task.getResult(), null);
                });
    }

    private void createAnonymousMapping(String contentId, String contentType,
                                        AuthManager.ActionListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("ownerId", uid);
        mapping.put("contentType", contentType);
        mapping.put("targetId", contentId);
        mapping.put("createdAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_ANONYMOUS).document(contentId).set(mapping)
                .addOnCompleteListener(task -> {
                    if (listener != null) listener.onComplete(task.getException());
                });
    }

    /** Administrator queue: open reports, newest first (sorted client-side to avoid an index). */
    public ListenerRegistration listenToOpenReports(final ReportsListener listener) {
        return db.collection(COLLECTION_REPORTS)
                .whereEqualTo("status", ContentReport.STATUS_OPEN)
                .limit(100)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToOpenReports: failure", error);
                        if (listener != null) listener.onReports(null, error);
                        return;
                    }
                    List<ContentReport> reports = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            ContentReport report = doc.toObject(ContentReport.class);
                            if (report == null) continue;
                            report.setId(doc.getId());
                            reports.add(report);
                        }
                    }
                    Collections.sort(reports, (a, b) -> {
                        long left = a.getCreatedAt() != null ? a.getCreatedAt().getTime() : 0L;
                        long right = b.getCreatedAt() != null ? b.getCreatedAt().getTime() : 0L;
                        return Long.compare(right, left);
                    });
                    if (listener != null) listener.onReports(reports, null);
                });
    }

    /** Only administrators can update report status; the rules enforce that on the server. */
    public void updateReportStatus(String reportId, String status,
                                   AuthManager.ActionListener listener) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        update.put("reviewedAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_REPORTS).document(reportId).update(update)
                .addOnCompleteListener(task -> {
                    if (listener != null) listener.onComplete(task.getException());
                });
    }

    /** Orders comments so every reply directly follows its parent. */
    public static List<Comment> flattenComments(List<Comment> comments) {
        List<Comment> result = new ArrayList<>();
        if (comments == null) return result;
        List<Comment> roots = new ArrayList<>();
        Map<String, List<Comment>> replies = new HashMap<>();
        for (Comment comment : comments) {
            if (comment.isReply()) {
                List<Comment> bucket = replies.get(comment.getParentId());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    replies.put(comment.getParentId(), bucket);
                }
                bucket.add(comment);
            } else {
                roots.add(comment);
            }
        }
        Comparator<Comment> byDate = (a, b) -> {
            long left = a.getCreatedAt() != null ? a.getCreatedAt().getTime() : 0L;
            long right = b.getCreatedAt() != null ? b.getCreatedAt().getTime() : 0L;
            return Long.compare(left, right);
        };
        Collections.sort(roots, byDate);
        for (Comment root : roots) {
            result.add(root);
            List<Comment> children = replies.get(root.getId());
            if (children != null) {
                Collections.sort(children, byDate);
                result.addAll(children);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ callbacks

    public interface FeedListener {
        void onFeed(List<Post> posts, boolean hasMore, Exception error);
    }

    public interface PostCreatedListener {
        void onCreated(String postId, Exception error);
    }

    public interface CommentsListener {
        void onComments(List<Comment> comments, Exception error);
    }

    public interface ReportsListener {
        void onReports(List<ContentReport> reports, Exception error);
    }

    public interface FollowingListener {
        void onFollowing(Set<String> followingIds);
    }

    public interface OwnedIdsListener {
        void onOwned(Set<String> ownedIds);
    }

    public interface ResultListener<T> {
        void onComplete(T result, Exception error);
    }

    private interface DecorateListener {
        void onDecorated(List<Post> posts, Exception error);
    }

    private interface UploadListListener {
        void onUploaded(List<String> urls, Exception error);
    }
}
