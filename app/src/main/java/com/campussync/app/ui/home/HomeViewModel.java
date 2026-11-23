package com.campussync.app.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.FirestoreManager;
import com.campussync.app.model.Event;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the home screen: shows the signed-in user's joined events.
 */
public class HomeViewModel extends ViewModel {

    private final FirestoreManager firestoreManager = FirestoreManager.getInstance();
    private final MutableLiveData<List<Event>> joinedEvents = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> greeting = new MutableLiveData<>("");

    private ListenerRegistration registration;
    private List<Event> allEvents = new ArrayList<>();

    public LiveData<List<Event>> getJoinedEvents() {
        return joinedEvents;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<String> getGreeting() {
        return greeting;
    }

    /** Builds a short personalised line for the home header. */
    public void setGreeting(com.google.firebase.auth.FirebaseUser user) {
        if (user == null) {
            greeting.setValue("");
            return;
        }
        String name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            String email = user.getEmail();
            name = email != null ? email.split("@")[0] : "there";
        }
        greeting.setValue("\uD83D\uDC4B Hi " + name + ", here is what is happening on campus.");
    }

    /** Starts the live events listener; safe to call repeatedly. */
    public void startListening() {
        if (registration != null) return;
        loading.setValue(allEvents.isEmpty());
        error.setValue(null);
        registration = firestoreManager.listenToEvents((events, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load events. Pull down to retry.");
                return;
            }
            allEvents = events != null ? events : new ArrayList<>();
            publishJoined();
        });
    }

    public void retry() {
        stopListening();
        startListening();
    }

    private void publishJoined() {
        String uid = AuthManager.getInstance().getCurrentUserId();
        List<Event> joined = new ArrayList<>();
        if (uid != null) {
            for (Event event : allEvents) {
                if (event != null && event.getAttendeeIds() != null
                        && event.getAttendeeIds().contains(uid)) {
                    joined.add(event);
                }
            }
        }
        joinedEvents.setValue(joined);
    }

    public void clearError() {
        error.setValue(null);
    }

    private void stopListening() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }
}
