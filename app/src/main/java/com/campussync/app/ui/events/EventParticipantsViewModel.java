package com.campussync.app.ui.events;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.EventRepository;
import com.campussync.app.model.EventRegistration;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Live participant list for one event. */
public class EventParticipantsViewModel extends ViewModel {

    private final MutableLiveData<List<EventRegistration>> registrations =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration listener;

    public LiveData<List<EventRegistration>> getRegistrations() {
        return registrations;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void load(String eventId) {
        if (listener != null || eventId == null) return;
        listener = EventRepository.getInstance().listenToRegistrations(eventId,
                (list, exception) -> {
                    loading.setValue(false);
                    if (exception != null) {
                        error.setValue("Unable to load participants");
                        return;
                    }
                    registrations.setValue(list != null ? list : new ArrayList<>());
                });
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
