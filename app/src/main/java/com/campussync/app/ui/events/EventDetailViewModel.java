package com.campussync.app.ui.events;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.EventRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.Event;
import com.campussync.app.model.EventRegistration;
import com.campussync.app.model.EventUpdate;
import com.campussync.app.model.UserProfile;
import com.campussync.app.util.QrCode;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Detail screen state: the live event document, the signed-in user's registration, organizer
 * updates, and the write actions (register, cancel, check-in, post update, cancel event).
 */
public class EventDetailViewModel extends ViewModel {

    /** Result of a QR scan, surfaced once to the fragment. */
    public static final class ScanResult {
        public final boolean success;
        public final String message;

        public ScanResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private final EventRepository repository = EventRepository.getInstance();
    private final UserRepository userRepository = UserRepository.getInstance();

    private final MutableLiveData<Event> event = new MutableLiveData<>();
    private final MutableLiveData<EventRegistration> registration = new MutableLiveData<>();
    private final MutableLiveData<List<EventUpdate>> updates =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<ScanResult> scanResult = new MutableLiveData<>();

    private String eventId;
    private UserProfile profile;
    private ListenerRegistration eventListener;
    private ListenerRegistration registrationListener;
    private ListenerRegistration updatesListener;

    public LiveData<Event> getEvent() {
        return event;
    }

    public LiveData<EventRegistration> getRegistration() {
        return registration;
    }

    public LiveData<List<EventUpdate>> getUpdates() {
        return updates;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getBusy() {
        return busy;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<ScanResult> getScanResult() {
        return scanResult;
    }

    public String getEventId() {
        return eventId;
    }

    public boolean isOrganizer() {
        Event current = event.getValue();
        String uid = EventRepository.currentUidOrNull();
        return current != null && uid != null && uid.equals(current.getOrganizerId());
    }

    public void load(String id) {
        if (id == null) {
            loading.setValue(false);
            error.setValue("Missing event id");
            return;
        }
        if (id.equals(eventId) && eventListener != null) return;
        eventId = id;
        loading.setValue(true);
        detach();

        loadProfile();

        eventListener = repository.listenToEvent(id, (loaded, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load this event.");
                return;
            }
            if (loaded == null) {
                error.setValue("This event is no longer available.");
                return;
            }
            event.setValue(loaded);
        });

        registrationListener = repository.listenToMyRegistration(id,
                (registered, exception) -> {
                    if (exception == null) registration.setValue(registered);
                });

        updatesListener = repository.listenToUpdates(id, (list, exception) -> {
            if (exception == null && list != null) updates.setValue(list);
        });
    }

    private void loadProfile() {
        String uid = EventRepository.currentUidOrNull();
        if (uid == null) return;
        userRepository.getProfile(uid, new UserRepository.ProfileListener() {
            @Override
            public void onProfile(UserProfile loaded, Exception exception) {
                if (loaded != null) profile = loaded;
            }
        });
    }

    public void register() {
        Event current = event.getValue();
        if (current == null || Boolean.TRUE.equals(busy.getValue())) return;
        if (current.isCancelled()) {
            error.setValue("This event has been cancelled");
            return;
        }
        busy.setValue(true);
        repository.register(current, profile, exception -> {
            busy.setValue(false);
            if (exception != null) {
                error.setValue(exception.getMessage() != null
                        ? exception.getMessage() : "Could not register for this event");
            } else {
                message.setValue("You are registered! Your ticket is ready.");
            }
        });
    }

    public void cancelRegistration() {
        Event current = event.getValue();
        if (current == null || Boolean.TRUE.equals(busy.getValue())) return;
        busy.setValue(true);
        repository.cancelRegistration(current, exception -> {
            busy.setValue(false);
            if (exception != null) {
                error.setValue(exception.getMessage() != null
                        ? exception.getMessage() : "Could not cancel your registration");
            } else {
                message.setValue("Registration cancelled");
            }
        });
    }

    /** Validates and redeems a scanned ticket; the repository re-checks the token. */
    public void checkIn(String rawCode) {
        QrCode.Ticket ticket = QrCode.parse(rawCode);
        if (ticket == null) {
            scanResult.setValue(new ScanResult(false, "That code is not a CampusSync ticket"));
            return;
        }
        Event current = event.getValue();
        if (current == null) return;
        repository.checkIn(current, ticket, (success, messageOrName) ->
                scanResult.setValue(new ScanResult(success,
                        success ? messageOrName + " checked in" : messageOrName)));
    }

    public void postUpdate(String title, String body) {
        if (eventId == null) return;
        busy.setValue(true);
        String authorName = profile != null ? profile.getDisplayName() : null;
        repository.addUpdate(eventId, title, body, authorName, exception -> {
            busy.setValue(false);
            if (exception != null) {
                error.setValue("Could not post the update");
            } else {
                message.setValue("Update posted");
            }
        });
    }

    public void cancelEvent() {
        if (eventId == null) return;
        busy.setValue(true);
        repository.cancelEvent(eventId, exception -> {
            busy.setValue(false);
            if (exception != null) {
                error.setValue("Could not cancel this event");
            } else {
                message.setValue("Event cancelled. Participants will be notified.");
            }
        });
    }

    public void clearMessage() {
        message.setValue(null);
    }

    public void clearError() {
        error.setValue(null);
    }

    public void clearScanResult() {
        scanResult.setValue(null);
    }

    private void detach() {
        if (eventListener != null) {
            eventListener.remove();
            eventListener = null;
        }
        if (registrationListener != null) {
            registrationListener.remove();
            registrationListener = null;
        }
        if (updatesListener != null) {
            updatesListener.remove();
            updatesListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        detach();
    }
}
