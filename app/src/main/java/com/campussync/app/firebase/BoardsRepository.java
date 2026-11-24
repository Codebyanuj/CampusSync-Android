package com.campussync.app.firebase;

import android.util.Log;

import com.campussync.app.model.Board;
import com.campussync.app.model.Comment;
import com.campussync.app.model.Discussion;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Firestore access for Reddit-style message boards.
 *
 * <p>{@code boards/{boardId}} holds the board, {@code discussions} its posts, and each discussion
 * keeps {@code votes/{uid}} and {@code comments}. Vote counts are maintained in a transaction so
 * repeated taps cannot double count.
 */
public class BoardsRepository {

    private static final String TAG = "BoardsRepository";

    public static final String COLLECTION_BOARDS = "boards";
    public static final String SUB_DISCUSSIONS = "discussions";
    public static final String SUB_VOTES = "votes";
    public static final String SUB_COMMENTS = "comments";

    public static final long VOTE_UP = 1L;
    public static final long VOTE_DOWN = -1L;

    private static volatile BoardsRepository instance;

    private final FirebaseFirestore db;

    private BoardsRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static BoardsRepository getInstance() {
        if (instance == null) {
            synchronized (BoardsRepository.class) {
                if (instance == null) instance = new BoardsRepository();
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ boards

    public ListenerRegistration listenToBoards(final BoardsListener listener) {
        return db.collection(COLLECTION_BOARDS)
                .orderBy("name", Query.Direction.ASCENDING)
                .limit(100)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToBoards: failure", error);
                        if (listener != null) listener.onBoards(null, error);
                        return;
                    }
                    List<Board> boards = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Board board = doc.toObject(Board.class);
                            if (board != null) {
                                board.setId(doc.getId());
                                boards.add(board);
                            }
                        }
                    }
                    if (listener != null) listener.onBoards(boards, null);
                });
    }

    /** Prefix search over the stored lowercase board name. */
    public ListenerRegistration searchBoards(final String query, final BoardsListener listener) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.isEmpty()) return listenToBoards(listener);
        return db.collection(COLLECTION_BOARDS)
                .whereGreaterThanOrEqualTo("nameLower", needle)
                .whereLessThanOrEqualTo("nameLower", needle + "\uf8ff")
                .limit(50)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onBoards(null, error);
                        return;
                    }
                    List<Board> boards = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Board board = doc.toObject(Board.class);
                            if (board != null) {
                                board.setId(doc.getId());
                                boards.add(board);
                            }
                        }
                    }
                    if (listener != null) listener.onBoards(boards, null);
                });
    }

    /** Single board read, used by the board detail header. */
    public void getBoard(String boardId, final BoardListener listener) {
        db.collection(COLLECTION_BOARDS).document(boardId).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        if (listener != null) listener.onBoard(null, task.getException());
                        return;
                    }
                    Board board = null;
                    if (task.getResult() != null && task.getResult().exists()) {
                        board = task.getResult().toObject(Board.class);
                        if (board != null) board.setId(task.getResult().getId());
                    }
                    if (listener != null) listener.onBoard(board, null);
                });
    }

    public void createBoard(final String name, final String description, final String college,
                            final AuthManager.ActionListener listener) {
        final String uid = SocialRepository.currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("nameLower", name != null ? name.toLowerCase() : "");
        data.put("description", description != null ? description : "");
        data.put("college", college != null ? college : "");
        data.put("createdBy", uid);
        data.put("discussionCount", 0);
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_BOARDS).add(data)
                .addOnCompleteListener(task -> {
                    if (listener != null) listener.onComplete(task.getException());
                });
    }

    // ------------------------------------------------------------------ discussions

    public ListenerRegistration listenToDiscussions(final String boardId,
                                                    final DiscussionsListener listener) {
        return db.collection(COLLECTION_BOARDS).document(boardId)
                .collection(SUB_DISCUSSIONS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .addSnapshotListener((snapshots, error) -> deliverDiscussions(snapshots, error, listener));
    }

    /** Trending = most upvoted discussions across every board (collection group query). */
    public ListenerRegistration listenToTrendingDiscussions(final DiscussionsListener listener) {
        return db.collectionGroup(SUB_DISCUSSIONS)
                .orderBy("upvoteCount", Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener((snapshots, error) -> deliverDiscussions(snapshots, error, listener));
    }

    public ListenerRegistration searchDiscussions(final String query,
                                                  final DiscussionsListener listener) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.isEmpty()) return listenToTrendingDiscussions(listener);
        return db.collectionGroup(SUB_DISCUSSIONS)
                .whereGreaterThanOrEqualTo("titleLower", needle)
                .whereLessThanOrEqualTo("titleLower", needle + "\uf8ff")
                .limit(30)
                .addSnapshotListener((snapshots, error) -> deliverDiscussions(snapshots, error, listener));
    }

    private void deliverDiscussions(QuerySnapshot snapshots, Exception error,
                                    DiscussionsListener listener) {
        if (error != null) {
            Log.w(TAG, "discussions query: failure", error);
            if (listener != null) listener.onDiscussions(null, error);
            return;
        }
        final List<Discussion> discussions = new ArrayList<>();
        final List<String> anonymousIds = new ArrayList<>();
        final String uid = SocialRepository.currentUidOrNull();
        if (snapshots != null) {
            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                Discussion discussion = doc.toObject(Discussion.class);
                if (discussion == null) continue;
                discussion.setId(doc.getId());
                discussion.setBoardId(doc.getReference().getParent().getParent().getId());
                discussion.setOwnedByMe(uid != null && uid.equals(discussion.getAuthorId()));
                if (discussion.isAnonymous()) anonymousIds.add(discussion.getId());
                discussions.add(discussion);
            }
        }
        SocialRepository.getInstance().loadOwnedAnonymousIds(anonymousIds, owned -> {
            for (Discussion discussion : discussions) {
                if (discussion.isAnonymous()) {
                    discussion.setOwnedByMe(owned.contains(discussion.getId()));
                }
            }
            decorateVotes(discussions, listener);
        });
    }

    /** Loads the signed-in user's vote for each discussion so the arrows render correctly. */
    private void decorateVotes(final List<Discussion> discussions,
                               final DiscussionsListener listener) {
        final String uid = SocialRepository.currentUidOrNull();
        if (uid == null || discussions.isEmpty()) {
            if (listener != null) listener.onDiscussions(discussions, null);
            return;
        }
        final List<Task<?>> tasks = new ArrayList<>();
        for (final Discussion discussion : discussions) {
            if (discussion.getBoardId() == null || discussion.getId() == null) continue;
            Task<DocumentSnapshot> task = db.collection(COLLECTION_BOARDS)
                    .document(discussion.getBoardId())
                    .collection(SUB_DISCUSSIONS).document(discussion.getId())
                    .collection(SUB_VOTES).document(uid).get();
            task.addOnSuccessListener(snapshot -> {
                if (snapshot != null && snapshot.exists()) {
                    Long value = snapshot.getLong("value");
                    discussion.setMyVote(value != null ? value : 0L);
                }
            });
            tasks.add(task);
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
            if (listener != null) listener.onDiscussions(discussions, null);
        });
    }

    public ListenerRegistration listenToDiscussion(String boardId, String discussionId,
                                                   final DiscussionListener listener) {
        return db.collection(COLLECTION_BOARDS).document(boardId)
                .collection(SUB_DISCUSSIONS).document(discussionId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        if (listener != null) listener.onDiscussion(null, error);
                        return;
                    }
                    Discussion discussion = null;
                    if (snapshot != null && snapshot.exists()) {
                        discussion = snapshot.toObject(Discussion.class);
                        if (discussion != null) {
                            discussion.setId(snapshot.getId());
                            discussion.setBoardId(boardId);
                            String uid = SocialRepository.currentUidOrNull();
                            discussion.setOwnedByMe(uid != null
                                    && uid.equals(discussion.getAuthorId()));
                        }
                    }
                    if (discussion == null || !discussion.isAnonymous()) {
                        if (listener != null) listener.onDiscussion(discussion, null);
                        return;
                    }
                    final Discussion anonymousDiscussion = discussion;
                    SocialRepository.getInstance().loadOwnedAnonymousIds(
                            java.util.Collections.singletonList(discussion.getId()), owned -> {
                                anonymousDiscussion.setOwnedByMe(
                                        owned.contains(anonymousDiscussion.getId()));
                                if (listener != null) {
                                    listener.onDiscussion(anonymousDiscussion, null);
                                }
                            });
                });
    }

    public void createDiscussion(final String boardId, final String title, final String body,
                                 final boolean anonymous, final String college,
                                 final AuthManager.ActionListener listener) {
        final String uid = SocialRepository.currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference discussionRef = db.collection(COLLECTION_BOARDS)
                .document(boardId).collection(SUB_DISCUSSIONS).document();
        UserRepository.getInstance().getProfile(uid, (profile, profileError) -> {
            Map<String, Object> data = new HashMap<>();
            data.put("boardId", boardId);
            data.put("title", title);
            data.put("titleLower", title != null ? title.toLowerCase() : "");
            data.put("body", body != null ? body : "");
            data.put("anonymous", anonymous);
            data.put("college", college != null ? college : "");
            data.put("upvoteCount", 0);
            data.put("downvoteCount", 0);
            data.put("commentCount", 0);
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("lastActivityAt", FieldValue.serverTimestamp());
            if (!anonymous) {
                data.put("authorId", uid);
                data.put("authorName", profile != null && profile.getDisplayName() != null
                        ? profile.getDisplayName() : "Campus member");
            }
            discussionRef.set(data).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    if (listener != null) listener.onComplete(task.getException());
                    return;
                }
                db.collection(COLLECTION_BOARDS).document(boardId)
                        .update("discussionCount", FieldValue.increment(1));
                if (anonymous) {
                    Map<String, Object> mapping = new HashMap<>();
                    mapping.put("ownerId", uid);
                    mapping.put("contentType", "discussion");
                    mapping.put("targetId", discussionRef.getId());
                    mapping.put("createdAt", FieldValue.serverTimestamp());
                    db.collection(SocialRepository.COLLECTION_ANONYMOUS)
                            .document(discussionRef.getId()).set(mapping)
                            .addOnCompleteListener(mappingTask -> {
                                if (listener != null) listener.onComplete(mappingTask.getException());
                            });
                } else if (listener != null) {
                    listener.onComplete(null);
                }
            });
        });
    }

    public void deleteDiscussion(final String boardId, final String discussionId,
                                 final AuthManager.ActionListener listener) {
        db.collection(COLLECTION_BOARDS).document(boardId)
                .collection(SUB_DISCUSSIONS).document(discussionId).delete()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        if (listener != null) listener.onComplete(task.getException());
                        return;
                    }
                    db.collection(COLLECTION_BOARDS).document(boardId)
                            .update("discussionCount", FieldValue.increment(-1));
                    db.collection(SocialRepository.COLLECTION_ANONYMOUS)
                            .document(discussionId).delete();
                    if (listener != null) listener.onComplete(null);
                });
    }

    /** Sets / clears the signed-in user's vote and keeps both counters accurate. */
    public void vote(final String boardId, final String discussionId, final long value,
                     final AuthManager.ActionListener listener) {
        final String uid = SocialRepository.currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference discussionRef = db.collection(COLLECTION_BOARDS)
                .document(boardId).collection(SUB_DISCUSSIONS).document(discussionId);
        final DocumentReference voteRef = discussionRef.collection(SUB_VOTES).document(uid);
        db.runTransaction(transaction -> {
            DocumentSnapshot discussionSnapshot = transaction.get(discussionRef);
            DocumentSnapshot voteSnapshot = transaction.get(voteRef);
            long up = discussionSnapshot.getLong("upvoteCount") != null
                    ? discussionSnapshot.getLong("upvoteCount") : 0L;
            long down = discussionSnapshot.getLong("downvoteCount") != null
                    ? discussionSnapshot.getLong("downvoteCount") : 0L;
            long previous = 0L;
            if (voteSnapshot.exists()) {
                Long stored = voteSnapshot.getLong("value");
                previous = stored != null ? stored : 0L;
            }
            long next = value;
            if (previous == next) next = 0L; // tapping the same arrow clears the vote
            if (previous == VOTE_UP) up = Math.max(0L, up - 1);
            if (previous == VOTE_DOWN) down = Math.max(0L, down - 1);
            if (next == VOTE_UP) up++;
            if (next == VOTE_DOWN) down++;

            Map<String, Object> counters = new HashMap<>();
            counters.put("upvoteCount", up);
            counters.put("downvoteCount", down);
            transaction.update(discussionRef, counters);
            if (next == 0L) {
                transaction.delete(voteRef);
            } else {
                Map<String, Object> vote = new HashMap<>();
                vote.put("value", next);
                vote.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(voteRef, vote);
            }
            return next;
        }).addOnCompleteListener(task -> {
            if (listener != null) listener.onComplete(task.getException());
        });
    }

    // ------------------------------------------------------------------ discussion comments

    public ListenerRegistration listenToDiscussionComments(final String boardId,
                                                           final String discussionId,
                                                           final CommentsListener listener) {
        return db.collection(COLLECTION_BOARDS).document(boardId)
                .collection(SUB_DISCUSSIONS).document(discussionId)
                .collection(SUB_COMMENTS)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(300)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
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
                            comment.setDiscussionId(discussionId);
                            comments.add(comment);
                            if (comment.isAnonymous()) anonymousIds.add(comment.getId());
                        }
                    }
                    final String uid = SocialRepository.currentUidOrNull();
                    SocialRepository.getInstance().loadOwnedAnonymousIds(anonymousIds, owned -> {
                        for (Comment comment : comments) {
                            boolean mine = uid != null && uid.equals(comment.getAuthorId());
                            comment.setOwnedByMe(mine || owned.contains(comment.getId()));
                        }
                        if (listener != null) listener.onComments(comments, null);
                    });
                });
    }

    public void addDiscussionComment(final String boardId, final String discussionId,
                                     final String content, final String parentId,
                                     final boolean anonymous,
                                     final AuthManager.ActionListener listener) {
        final String uid = SocialRepository.currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        final DocumentReference discussionRef = db.collection(COLLECTION_BOARDS)
                .document(boardId).collection(SUB_DISCUSSIONS).document(discussionId);
        final DocumentReference commentRef = discussionRef.collection(SUB_COMMENTS).document();
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
                Map<String, Object> counters = new HashMap<>();
                counters.put("commentCount", FieldValue.increment(1));
                counters.put("lastActivityAt", FieldValue.serverTimestamp());
                discussionRef.update(counters);
                if (anonymous) {
                    Map<String, Object> mapping = new HashMap<>();
                    mapping.put("ownerId", uid);
                    mapping.put("contentType", "discussionComment");
                    mapping.put("targetId", commentRef.getId());
                    mapping.put("createdAt", FieldValue.serverTimestamp());
                    db.collection(SocialRepository.COLLECTION_ANONYMOUS)
                            .document(commentRef.getId()).set(mapping)
                            .addOnCompleteListener(mappingTask -> {
                                if (listener != null) listener.onComplete(mappingTask.getException());
                            });
                } else if (listener != null) {
                    listener.onComplete(null);
                }
            });
        });
    }

    public void deleteDiscussionComment(final String boardId, final String discussionId,
                                        final String commentId,
                                        final AuthManager.ActionListener listener) {
        final DocumentReference discussionRef = db.collection(COLLECTION_BOARDS)
                .document(boardId).collection(SUB_DISCUSSIONS).document(discussionId);
        discussionRef.collection(SUB_COMMENTS).document(commentId).delete()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        if (listener != null) listener.onComplete(task.getException());
                        return;
                    }
                    discussionRef.update("commentCount", FieldValue.increment(-1));
                    db.collection(SocialRepository.COLLECTION_ANONYMOUS).document(commentId).delete();
                    if (listener != null) listener.onComplete(null);
                });
    }

    /** Ids of the anonymous discussions owned by the signed-in user (for menu visibility). */
    public void loadOwnedAnonymousIds(List<String> ids, SocialRepository.OwnedIdsListener listener) {
        SocialRepository.getInstance().loadOwnedAnonymousIds(ids, listener);
    }

    public static Set<String> emptyIds() {
        return new HashSet<>();
    }

    // ------------------------------------------------------------------ callbacks

    public interface BoardListener {
        void onBoard(Board board, Exception error);
    }

    public interface BoardsListener {
        void onBoards(List<Board> boards, Exception error);
    }

    public interface DiscussionsListener {
        void onDiscussions(List<Discussion> discussions, Exception error);
    }

    public interface DiscussionListener {
        void onDiscussion(Discussion discussion, Exception error);
    }

    public interface CommentsListener {
        void onComments(List<Comment> comments, Exception error);
    }
}
