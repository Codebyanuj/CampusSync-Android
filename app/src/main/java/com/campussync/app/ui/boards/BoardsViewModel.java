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

/** Boards tab state: browse boards or trending discussions, with prefix search on both. */
public class BoardsViewModel extends ViewModel {

    public static final int MODE_BOARDS = 0;
    public static final int MODE_TRENDING = 1;

    private final BoardsRepository repository = BoardsRepository.getInstance();

    private final MutableLiveData<List<Board>> boards = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Discussion>> discussions =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private int mode = MODE_BOARDS;
    private String query = "";
    private String college;
    private boolean collegeLoaded;
    private ListenerRegistration boardsRegistration;
    private ListenerRegistration discussionsRegistration;

    public LiveData<List<Board>> getBoards() {
        return boards;
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

    public int getMode() {
        return mode;
    }

    public void start() {
        if (collegeLoaded) {
            attach();
            return;
        }
        String uid = SocialRepository.currentUidOrNull();
        if (uid == null) {
            collegeLoaded = true;
            attach();
            return;
        }
        UserRepository.getInstance().getProfile(uid, (profile, profileError) -> {
            college = profile != null ? profile.getCollege() : null;
            collegeLoaded = true;
            attach();
        });
    }

    public void switchMode(int newMode) {
        if (newMode == mode) return;
        mode = newMode;
        if (mode == MODE_BOARDS) {
            discussions.setValue(new ArrayList<>());
        } else {
            boards.setValue(new ArrayList<>());
        }
        attach();
    }

    /** Empty query restores the live list for the current mode. */
    public void setQuery(String newQuery) {
        String next = newQuery != null ? newQuery.trim().toLowerCase() : "";
        if (next.equals(query)) return;
        query = next;
        attach();
    }

    private void attach() {
        detach();
        loading.setValue(true);
        error.setValue(null);
        if (mode == MODE_BOARDS) {
            BoardsRepository.BoardsListener listener = (list, exception) -> {
                loading.setValue(false);
                if (exception != null) {
                    error.setValue("Unable to load boards.");
                    return;
                }
                boards.setValue(list != null ? list : new ArrayList<Board>());
            };
            boardsRegistration = query.isEmpty()
                    ? repository.listenToBoards(listener)
                    : repository.searchBoards(query, listener);
        } else {
            BoardsRepository.DiscussionsListener listener = (list, exception) -> {
                loading.setValue(false);
                if (exception != null) {
                    error.setValue("Unable to load discussions.");
                    return;
                }
                discussions.setValue(list != null ? list : new ArrayList<Discussion>());
            };
            discussionsRegistration = query.isEmpty()
                    ? repository.listenToTrendingDiscussions(listener)
                    : repository.searchDiscussions(query, listener);
        }
    }

    public void vote(Discussion discussion, long value) {
        if (discussion == null || discussion.getBoardId() == null
                || discussion.getId() == null) return;
        repository.vote(discussion.getBoardId(), discussion.getId(), value, exception -> {
            if (exception != null) error.setValue("Could not save your vote.");
        });
    }

    public void createBoard(String name, String description, AuthManager.ActionListener callback) {
        repository.createBoard(name, description, college, callback);
    }

    public void clearError() {
        error.setValue(null);
    }

    private void detach() {
        if (boardsRegistration != null) {
            boardsRegistration.remove();
            boardsRegistration = null;
        }
        if (discussionsRegistration != null) {
            discussionsRegistration.remove();
            discussionsRegistration = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        detach();
    }
}
