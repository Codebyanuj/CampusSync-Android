package com.campussync.app.ui.events;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.EventRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.CollegeNotice;
import com.campussync.app.model.UserProfile;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** College notices feed plus posting (administrators only, enforced by the rules). */
public class NoticesViewModel extends ViewModel {

    private final MutableLiveData<List<CollegeNotice>> notices =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration listener;
    private UserProfile profile;

    public LiveData<List<CollegeNotice>> getNotices() {
        return notices;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void start() {
        if (listener != null) return;
        listener = EventRepository.getInstance().listenToNotices((list, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load notices");
                return;
            }
            notices.setValue(list != null ? list : new ArrayList<>());
        });
        loadProfile();
    }

    private void loadProfile() {
        String uid = EventRepository.currentUidOrNull();
        if (uid == null) return;
        UserRepository.getInstance().getProfile(uid, new UserRepository.ProfileListener() {
            @Override
            public void onProfile(UserProfile loaded, Exception exception) {
                if (loaded != null) profile = loaded;
            }
        });
    }

    public String defaultCollege() {
        return profile != null && profile.getCollege() != null ? profile.getCollege() : "";
    }

    public void postNotice(String title, String body, String college,
                           final AuthManager.ActionListener listener) {
        String authorName = profile != null ? profile.getDisplayName() : null;
        EventRepository.getInstance().postNotice(title, body, college, authorName, listener);
    }

    public void clearError() {
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }
}
