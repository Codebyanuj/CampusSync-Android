package com.campussync.app.ui.profile;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.UserProfile;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Map;

/**
 * ViewModel for editing the signed-in user's profile.
 *
 * Only editable fields are sent to Firestore; role, verification and account status are never
 * written from here (the rules would reject it).
 */
public class EditProfileViewModel extends ViewModel {

    private final UserRepository repository = UserRepository.getInstance();

    private final MutableLiveData<UserProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration registration;

    public LiveData<UserProfile> getProfile() {
        return profile;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void startListening() {
        if (registration != null) return;
        registration = repository.listenToProfile(
                AuthManager.getInstance().getCurrentUserId(), (value, exception) -> {
                    if (exception != null) {
                        error.setValue("Could not load your profile.");
                        return;
                    }
                    profile.setValue(value);
                });
    }

    public void uploadPhoto(Uri uri) {
        saving.setValue(true);
        repository.uploadProfilePhoto(uri, (photoUrl, exception) -> {
            saving.setValue(false);
            if (exception != null) {
                error.setValue(exception.getMessage() != null
                        ? exception.getMessage()
                        : "Photo upload failed. Try again.");
                return;
            }
            message.setValue("Profile photo updated.");
        });
    }

    public void save(Map<String, Object> fields) {
        saving.setValue(true);
        repository.saveProfile(fields, exception -> {
            saving.setValue(false);
            if (exception != null) {
                error.setValue(exception.getMessage() != null
                        ? exception.getMessage()
                        : "Could not save your profile.");
                return;
            }
            message.setValue("Profile saved.");
        });
    }

    public void clearMessage() {
        message.setValue(null);
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
