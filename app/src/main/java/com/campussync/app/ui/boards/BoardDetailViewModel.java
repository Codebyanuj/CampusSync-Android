package com.campussync.app.ui.boards;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.BoardsRepository;
import com.campussync.app.firebase.SocialRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.Board;
import com.campussync.app.model.Discussion;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** One board: its details, its discussions, publishing and voting. */
public class BoardDetailViewModel extends ViewModel {

    private final BoardsRepository repository = BoardsRepository.getInstance();

    private final MutableLiveData<Board> board = new MutableLiveData<>();
    private final MutableLiveData<List<Discussion>> discussions =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private String boardId;
    private String college;
    private ListenerRegistration discussionsRegistration;

    public LiveData<Board> getBoard() {
        return board;
    }

    public LiveData<List<Discussion>> getDiscussions() {
        return discussions;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void init(String boardId) {
        if (this.boardId == null) this.boardId = boardId;
    }

    public void start() {
        if (boardId == null || discussionsRegistration != null) return;
        loading.setValue(true);
        repository.getBoard(boardId, (result, exception) -> {
            if (exception == null) board.setValue(result);
            if (result != null) college = result.getCollege();
        });
        discussionsRegistration = repository.listenToDiscussions(boardId, (list, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load discussions.");
                return;
            }
            discussions.setValue(list != null ? list : new ArrayList<Discussion>());
        });
    }

    public void vote(Discussion discussion, long value) {
        if (boardId == null || discussion == null || discussion.getId() == null) return;
        repository.vote(boardId, discussion.getId(), value, exception -> {
            if (exception != null) error.setValue("Could not save your vote.");
        });
    }

    public void createDiscussion(String title, String body, boolean anonymous,
                                 final AuthManager.ActionListener callback) {
        if (boardId == null) {
            if (callback != null) callback.onComplete(new IllegalStateException("Missing board"));
            return;
        }
        String uid = SocialRepository.currentUidOrNull();
        if (uid == null) {
            if (callback != null) callback.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        if (college == null) {
            UserRepository.getInstance().getProfile(uid, (profile, profileError) -> {
                if (profile != null) college = profile.getCollege();
                repository.createDiscussion(boardId, title, body, anonymous, college, callback);
            });
            return;
        }
        repository.createDiscussion(boardId, title, body, anonymous, college, callback);
    }

    public void clearError() {
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (discussionsRegistration != null) {
            discussionsRegistration.remove();
            discussionsRegistration = null;
        }
    }
}
