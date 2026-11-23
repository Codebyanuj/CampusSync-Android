package com.campussync.app.ui.events;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.AuthManager.ActionListener;
import com.campussync.app.firebase.FirestoreManager;
import com.campussync.app.model.Event;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the events tab.
 */
public class EventsViewModel extends ViewModel {

    private final FirestoreManager firestoreManager = FirestoreManager.getInstance();
    private final MutableLiveData<List<Event>> events = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> empty = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration registration;

    public LiveData<List<Event>> getEvents() {
        return events;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getEmpty() {
        return empty;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void startListening() {
        if (registration != null) return;
        loading.setValue(true);
        error.setValue(null);
        registration = firestoreManager.listenToEvents((list, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load events. Check your connection and retry.");
                empty.setValue(false);
                return;
            }
            List<Event> result = list != null ? list : new ArrayList<>();
            events.setValue(result);
            empty.setValue(result.isEmpty());
        });
    }

    public void retry() {
        stopListening();
        startListening();
    }

    public void refresh() {
        // Firestore listeners push updates automatically; retry recreates it after an error.
        error.setValue(null);
        retry();
    }

    public void toggleJoin(Event event, ActionListener callback) {
        if (event == null || event.getId() == null) return;
        String uid = AuthManager.getInstance().getCurrentUserId();
        boolean joined = uid != null && event.getAttendeeIds() != null
                && event.getAttendeeIds().contains(uid);
        if (joined) {
            firestoreManager.leaveEvent(event.getId(), callback);
        } else {
            firestoreManager.joinEvent(event.getId(), callback);
        }
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
