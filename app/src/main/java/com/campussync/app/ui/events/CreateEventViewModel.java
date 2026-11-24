package com.campussync.app.ui.events;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.EventRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.Event;
import com.campussync.app.model.UserProfile;

/** Publishes a new event owned by the signed-in organizer. */
public class CreateEventViewModel extends ViewModel {

    private final EventRepository repository = EventRepository.getInstance();
    private final UserRepository userRepository = UserRepository.getInstance();
    private final MutableLiveData<UserProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<Boolean> publishing = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<UserProfile> getProfile() {
        return profile;
    }

    public LiveData<Boolean> getPublishing() {
        return publishing;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadProfile() {
        String uid = EventRepository.currentUidOrNull();
        if (uid == null) return;
        userRepository.getProfile(uid, new UserRepository.ProfileListener() {
            @Override
            public void onProfile(UserProfile loaded, Exception exception) {
                if (loaded != null) profile.setValue(loaded);
            }
        });
    }

    public UserProfile currentProfile() {
        return profile.getValue();
    }

    public void create(Event event, final AuthManager.ActionListener listener) {
        publishing.setValue(true);
        repository.createEvent(event, exception -> {
            publishing.setValue(false);
            if (exception != null) {
                error.setValue(exception.getMessage() != null
                        ? exception.getMessage() : "Could not publish the event");
            }
            if (listener != null) listener.onComplete(exception);
        });
    }

    public void clearError() {
        error.setValue(null);
    }
}
