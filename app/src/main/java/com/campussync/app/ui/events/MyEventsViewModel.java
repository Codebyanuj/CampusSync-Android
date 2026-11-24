package com.campussync.app.ui.events;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.EventRepository;
import com.campussync.app.model.Event;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Events the signed-in user hosts and the ones they registered for. */
public class MyEventsViewModel extends ViewModel {

    private final MutableLiveData<List<Event>> hosted = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>> registered =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration listener;

    public LiveData<List<Event>> getHosted() {
        return hosted;
    }

    public LiveData<List<Event>> getRegistered() {
        return registered;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void start() {
        if (listener != null) return;
        listener = EventRepository.getInstance().listenToMyEvents(
                (hostedEvents, registeredEvents, exception) -> {
                    loading.setValue(false);
                    if (exception != null) {
                        error.setValue("Unable to load your events");
                        return;
                    }
                    if (hostedEvents != null) hosted.setValue(hostedEvents);
                    if (registeredEvents != null) registered.setValue(registeredEvents);
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
