package com.campussync.app.ui.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.SocialRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.ContentReport;
import com.campussync.app.model.Post;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Feed state: the active mode, the real-time first page, pagination and optimistic reactions.
 */
public class FeedViewModel extends ViewModel {

    private final SocialRepository repository = SocialRepository.getInstance();

    private final MutableLiveData<List<Post>> posts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> empty = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();

    private ListenerRegistration registration;
    private String mode = SocialRepository.MODE_COLLEGE;
    private String college;
    private boolean collegeLoaded;
    private final Set<String> seenIds = new HashSet<>();

    public LiveData<List<Post>> getPosts() {
        return posts;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getEmpty() {
        return empty;
    }

    public LiveData<Boolean> getHasMore() {
        return hasMore;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Integer> getMessageRes() {
        return messageRes;
    }

    public String getMode() {
        return mode;
    }

    /** Loads the signed-in user's college once, then attaches the feed listener. */
    public void start() {
        if (registration != null) return;
        String uid = SocialRepository.currentUidOrNull();
        if (uid == null) {
            loading.setValue(false);
            return;
        }
        loading.setValue(true);
        if (collegeLoaded) {
            attach();
            return;
        }
        UserRepository.getInstance().getProfile(uid, (profile, profileError) -> {
            college = profile != null ? profile.getCollege() : null;
            collegeLoaded = true;
            attach();
        });
    }

    private void attach() {
        loading.setValue(true);
        error.setValue(null);
        registration = repository.listenToFeed(mode, college, (list, more, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load the feed. Check your connection and retry.");
                empty.setValue(false);
                return;
            }
            List<Post> incoming = list != null ? list : new ArrayList<>();
            List<Post> merged = new ArrayList<>(incoming);
            // Keep pages already loaded while the first page updates in real time.
            List<Post> existing = posts.getValue();
            if (existing != null) {
                Set<String> incomingIds = new HashSet<>();
                for (Post post : incoming) incomingIds.add(post.getId());
                for (Post post : existing) {
                    if (post.getId() != null && !incomingIds.contains(post.getId())) {
                        merged.add(post);
                    }
                }
            }
            seenIds.clear();
            for (Post post : merged) {
                if (post.getId() != null) seenIds.add(post.getId());
            }
            posts.setValue(merged);
            hasMore.setValue(more);
            empty.setValue(incoming.isEmpty());
        });
    }

    public void switchMode(String newMode) {
        if (newMode == null || newMode.equals(mode)) return;
        mode = newMode;
        detach();
        posts.setValue(new ArrayList<>());
        seenIds.clear();
        empty.setValue(false);
        attach();
    }

    public void loadMore() {
        if (!Boolean.TRUE.equals(hasMore.getValue()) || Boolean.TRUE.equals(loading.getValue())) return;
        repository.loadMoreFeed(mode, college, (list, more, exception) -> {
            if (exception != null || list == null) return;
            List<Post> merged = new ArrayList<>(posts.getValue() != null
                    ? posts.getValue() : new ArrayList<Post>());
            for (Post post : list) {
                if (post.getId() != null && seenIds.add(post.getId())) merged.add(post);
            }
            posts.setValue(merged);
            hasMore.setValue(more);
        });
    }

    public void refresh() {
        detach();
        attach();
    }

    public void toggleLike(Post post) {
        if (post == null || post.getId() == null) return;
        boolean next = !post.isLikedByMe();
        post.setLikedByMe(next);
        post.setLikeCount(Math.max(0L, post.getLikeCount() + (next ? 1 : -1)));
        publish();
        repository.setLiked(post.getId(), next, exception -> {
            if (exception == null) return;
            post.setLikedByMe(!next);
            post.setLikeCount(Math.max(0L, post.getLikeCount() + (next ? -1 : 1)));
            publish();
            error.setValue("Could not update your like. Please try again.");
        });
    }

    public void toggleBookmark(Post post) {
        if (post == null || post.getId() == null) return;
        boolean next = !post.isBookmarkedByMe();
        post.setBookmarkedByMe(next);
        publish();
        repository.setBookmarked(post, next, exception -> {
            if (exception == null) {
                messageRes.setValue(next ? com.campussync.app.R.string.bookmark_saved
                        : com.campussync.app.R.string.bookmark_removed);
                return;
            }
            post.setBookmarkedByMe(!next);
            publish();
        });
    }

    public void share(Post post) {
        if (post != null && post.getId() != null) {
            repository.sharePost(post.getId(), null);
        }
    }

    public void deletePost(Post post, AuthManager.ActionListener callback) {
        if (post == null || post.getId() == null) return;
        repository.deletePost(post.getId(), callback);
    }

    public void report(Post post, String reason) {
        if (post == null || post.getId() == null) return;
        ContentReport report = new ContentReport();
        report.setTargetType(ContentReport.TYPE_POST);
        report.setTargetId(post.getId());
        report.setTargetLabel(preview(post.getContent()));
        report.setReason(reason);
        report.setDetails("");
        repository.report(report, exception -> {
            if (exception == null) {
                messageRes.setValue(com.campussync.app.R.string.report_sent);
            } else {
                error.setValue("Could not send the report. Please try again.");
            }
        });
    }

    public void clearMessage() {
        messageRes.setValue(null);
    }

    public void clearError() {
        error.setValue(null);
    }

    private void publish() {
        List<Post> current = posts.getValue();
        posts.setValue(current != null ? new ArrayList<>(current) : new ArrayList<Post>());
    }

    private static String preview(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        return trimmed.length() > 60 ? trimmed.substring(0, 60) + "…" : trimmed;
    }

    private void detach() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        detach();
    }
}
