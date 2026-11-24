package com.campussync.app.ui.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.R;
import com.campussync.app.firebase.SocialRepository;
import com.campussync.app.model.Comment;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Live comment thread for one post, flattened so replies follow their parent. */
public class CommentsViewModel extends ViewModel {

    private final SocialRepository repository = SocialRepository.getInstance();

    private final MutableLiveData<List<Comment>> comments = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();

    private String postId;
    private ListenerRegistration registration;

    public LiveData<List<Comment>> getComments() {
        return comments;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Integer> getMessageRes() {
        return messageRes;
    }

    public void init(String postId) {
        if (this.postId == null) this.postId = postId;
    }

    public void start() {
        if (registration != null || postId == null) return;
        loading.setValue(true);
        registration = repository.listenToComments(postId, (list, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load comments.");
                return;
            }
            comments.setValue(SocialRepository.flattenComments(list));
        });
    }

    public void addComment(String content, String parentId) {
        if (postId == null || content == null || content.trim().isEmpty()) return;
        repository.addComment(postId, content.trim(), parentId, false, exception -> {
            if (exception == null) {
                messageRes.setValue(R.string.comment_posted);
            } else {
                error.setValue("Could not post your comment.");
            }
        });
    }

    public void deleteComment(Comment comment) {
        if (postId == null || comment == null || comment.getId() == null) return;
        repository.deleteComment(postId, comment.getId(), exception -> {
            if (exception != null) error.setValue("Could not delete the comment.");
        });
    }

    public void clearMessage() {
        messageRes.setValue(null);
    }

    public void clearError() {
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
