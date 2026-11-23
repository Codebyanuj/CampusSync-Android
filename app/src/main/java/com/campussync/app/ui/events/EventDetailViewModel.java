package com.campussync.app.ui.events;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.FirestoreManager;
import com.campussync.app.model.Event;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for a single event, including the current user's join state and attendee names.
 */
public class EventDetailViewModel extends ViewModel {

    private final FirestoreManager firestoreManager = FirestoreManager.getInstance();
    private final MutableLiveData<Event> event = new MutableLiveData<>();
    private final MutableLiveData<Boolean> joined = new MutableLiveData<>(false);
    private final MutableLiveData<List<String>> attendeeNames = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> actionInProgress = new MutableLiveData<>(false);
    private final MutableLiveData<String> actionMessage = new MutableLiveData<>();

    private String eventId;
    private ListenerRegistration registration;

    public LiveData<Event> getEvent() {
        return event;
    }

    public LiveData<Boolean> getJoined() {
        return joined;
    }

    public LiveData<List<String>> getAttendeeNames() {
        return attendeeNames;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getActionInProgress() {
        return actionInProgress;
    }

    public LiveData<String> getActionMessage() {
        return actionMessage;
    }

    public void load(String eventId) {
        if (this.eventId != null && this.eventId.equals(eventId) && registration != null) return;
        this.eventId = eventId;
        loading.setValue(true);
        error.setValue(null);

        if (registration != null) {
            registration.remove();
        }

        // Keep the screen live so attendee counts update when other students join.
        registration = firestoreManager.listenToEvents((events, exception) -> {
            if (exception != null) {
                loading.setValue(false);
                error.setValue("Unable to load this event.");
                return;
            }
            Event found = null;
            if (events != null) {
                for (Event candidate : events) {
                    if (eventId.equals(candidate.getId())) {
                        found = candidate;
                        break;
                    }
                }
            }
            loading.setValue(false);
            if (found == null) {
                error.setValue("This event is no longer available.");
                return;
            }
            publish(found);
        });
    }

    private void publish(Event found) {
        event.setValue(found);
        String uid = AuthManager.getInstance().getCurrentUserId();
        List<String> ids = found.getAttendeeIds();
        joined.setValue(uid != null && ids != null && ids.contains(uid));

        if (ids == null || ids.isEmpty()) {
            attendeeNames.setValue(new ArrayList<>());
            return;
        }
        firestoreManager.getDisplayNames(ids, new FirestoreManager.DisplayNamesCallback() {
            @Override
            public void onSuccess(Map<String, String> namesById) {
                List<String> names = new ArrayList<>();
                for (String id : ids) {
                    String name = namesById.get(id);
                    names.add(name != null ? name : "Member");
                }
                attendeeNames.setValue(names);
            }

            @Override
            public void onError(Exception exception) {
                // Names are decoration; failing to resolve them must not hide the event.
                List<String> names = new ArrayList<>();
                for (int i = 0; i < ids.size(); i++) names.add("Member");
                attendeeNames.setValue(names);
            }
        });
    }

    public void toggleJoin() {
        Event current = event.getValue();
        if (current == null || eventId == null) return;
        if (Boolean.TRUE.equals(actionInProgress.getValue())) return;

        actionInProgress.setValue(true);
        boolean isJoined = Boolean.TRUE.equals(joined.getValue());
        FirestoreManager.getInstance();
        AuthManager.ActionListener listener = exception -> {
            actionInProgress.setValue(false);
            if (exception != null) {
                actionMessage.setValue(exception.getMessage() != null
                        ? exception.getMessage()
                        : "Could not update your RSVP. Try again.");
            } else {
                joined.setValue(!isJoined);
                actionMessage.setValue(!isJoined ? "You joined this event." : "You left this event.");
            }
        };
        if (isJoined) {
            firestoreManager.leaveEvent(eventId, listener);
        } else {
            firestoreManager.joinEvent(eventId, listener);
        }
    }

    public void clearMessage() {
        actionMessage.setValue(null);
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
