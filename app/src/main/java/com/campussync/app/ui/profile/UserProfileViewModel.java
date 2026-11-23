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

    public LiveData<UserProfile> getProfile() {
        return profile;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getNotFound() {
        return notFound;
    }

    public void load(String uid) {
        loading.setValue(true);
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
