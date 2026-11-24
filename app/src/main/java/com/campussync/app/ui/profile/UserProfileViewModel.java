package com.campussync.app.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.UserProfile;

/**
 * Loads a single profile for the read-only profile screen.
 */
public class UserProfileViewModel extends ViewModel {

    private final UserRepository repository = UserRepository.getInstance();
    private final MutableLiveData<UserProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> notFound = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> following = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> self = new MutableLiveData<>(false);

    private String targetUid;

    public LiveData<UserProfile> getProfile() {
        return profile;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getNotFound() {
        return notFound;
    }

    public LiveData<Boolean> getFollowing() {
        return following;
    }

    public LiveData<Boolean> getIsSelf() {
        return self;
    }

    /** Follow state powers the following feed; you cannot follow your own profile. */
    public void toggleFollow(com.campussync.app.firebase.AuthManager.ActionListener callback) {
        if (targetUid == null) return;
        boolean next = !Boolean.TRUE.equals(following.getValue());
        com.campussync.app.firebase.SocialRepository.getInstance()
                .setFollowing(targetUid, next, error -> {
                    if (error == null) following.setValue(next);
                    if (callback != null) callback.onComplete(error);
                });
    }

    public void load(String uid) {
        targetUid = uid;
        loading.setValue(true);
        String current = com.campussync.app.firebase.SocialRepository.currentUidOrNull();
        boolean isSelf = current != null && current.equals(uid);
        self.setValue(isSelf);
        if (!isSelf) {
            com.campussync.app.firebase.SocialRepository.getInstance()
                    .isFollowing(uid, (value, error) -> following.setValue(value));
        }
        repository.getProfile(uid, (value, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                notFound.setValue(true);
                return;
            }
            if (value == null) {
                notFound.setValue(true);
                return;
            }
            profile.setValue(value);
        });
    }
}
