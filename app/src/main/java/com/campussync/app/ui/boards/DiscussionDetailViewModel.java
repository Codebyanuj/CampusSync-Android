package com.campussync.app.ui.boards;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.R;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.BoardsRepository;
import com.campussync.app.firebase.SocialRepository;
import com.campussync.app.model.Comment;
import com.campussync.app.model.Discussion;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** One discussion: votes, comments, replies and deletion by its author. */
public class DiscussionDetailViewModel extends ViewModel {

    private final BoardsRepository repository = BoardsRepository.getInstance();

    private final MutableLiveData<Discussion> discussion = new MutableLiveData<>();
    private final MutableLiveData<List<Comment>> comments =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();

    private String boardId;
    private String discussionId;
    private ListenerRegistration discussionRegistration;
    private ListenerRegistration commentsRegistration;

    public LiveData<Discussion> getDiscussion() {
        return discussion;
    }

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

    public void init(String boardId, String discussionId) {
        if (this.discussionId == null) {
            this.boardId = boardId;
            this.discussionId = discussionId;
        }
    }

    public void start() {
        if (discussionId == null || discussionRegistration != null) return;
        loading.setValue(true);
        discussionRegistration = repository.listenToDiscussion(boardId, discussionId,
                (result, exception) -> {
                    loading.setValue(false);
                    if (exception != null) {
                        error.setValue("Unable to load the discussion.");
                        return;
                    }
                    discussion.setValue(result);
                });
        commentsRegistration = repository.listenToDiscussionComments(boardId, discussionId,
                (list, exception) -> {
                    if (exception != null) {
                        error.setValue("Unable to load comments.");
                        return;
                    }
                    comments.setValue(SocialRepository.flattenComments(list));
                });
    }

    public void vote(long value) {
        repository.vote(boardId, discussionId, value, exception -> {
            if (exception != null) error.setValue("Could not save your vote.");
        });
    }

    public void addComment(String content, String parentId) {
        if (content == null || content.trim().isEmpty()) return;
        repository.addDiscussionComment(boardId, discussionId, content.trim(), parentId,
                false, exception -> {
                    if (exception == null) {
                        messageRes.setValue(R.string.comment_posted);
                    } else {
                        error.setValue("Could not post your comment.");
                    }
                });
    }

    public void deleteComment(Comment comment) {
        if (comment == null || comment.getId() == null) return;
        repository.deleteDiscussionComment(boardId, discussionId, comment.getId(),
                exception -> {
                    if (exception != null) error.setValue("Could not delete the comment.");
                });
    }

    public void deleteDiscussion(AuthManager.ActionListener callback) {
        repository.deleteDiscussion(boardId, discussionId, callback);
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
        if (discussionRegistration != null) {
            discussionRegistration.remove();
            discussionRegistration = null;
        }
        if (commentsRegistration != null) {
            commentsRegistration.remove();
            commentsRegistration = null;
        }
    }
}
