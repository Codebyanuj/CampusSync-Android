package com.campussync.app.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.FirestoreManager;
import com.campussync.app.model.Event;
import com.campussync.app.model.Group;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the profile tab: identity summary plus the user's joined events and groups.
 */
public class ProfileViewModel extends ViewModel {

    private final FirestoreManager firestoreManager = FirestoreManager.getInstance();
    private final MutableLiveData<List<Event>> joinedEvents = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Group>> joinedGroups = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);

    private ListenerRegistration eventsRegistration;
    private ListenerRegistration groupsRegistration;

    public LiveData<List<Event>> getJoinedEvents() {
        return joinedEvents;
    }

    public LiveData<List<Group>> getJoinedGroups() {
        return joinedGroups;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public String getDisplayName() {
        com.google.firebase.auth.FirebaseUser user = AuthManager.getInstance().getCurrentUser();
        if (user == null) return "";
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        return user.getEmail() != null ? user.getEmail() : "Campus Sync user";
    }

    public String getEmail() {
        com.google.firebase.auth.FirebaseUser user = AuthManager.getInstance().getCurrentUser();
        return user != null && user.getEmail() != null ? user.getEmail() : "";
    }

    public void startListening() {
        if (eventsRegistration == null) {
            eventsRegistration = firestoreManager.listenToEvents((events, exception) -> {
                loading.setValue(false);
                if (exception != null) return;
                publishEvents(events);
            });
        }
        if (groupsRegistration == null) {
            groupsRegistration = firestoreManager.listenToGroups((groups, exception) -> {
                loading.setValue(false);
                if (exception != null) return;
                publishGroups(groups);
            });
        }
    }

    private void publishEvents(List<Event> events) {
        String uid = AuthManager.getInstance().getCurrentUserId();
        List<Event> joined = new ArrayList<>();
        if (uid != null && events != null) {
            for (Event event : events) {
                if (event.getAttendeeIds() != null && event.getAttendeeIds().contains(uid)) {
                    joined.add(event);
                }
            }
        }
        joinedEvents.setValue(joined);
    }

    private void publishGroups(List<Group> groups) {
        String uid = AuthManager.getInstance().getCurrentUserId();
        List<Group> joined = new ArrayList<>();
        if (uid != null && groups != null) {
            for (Group group : groups) {
                if (group.getMemberIds() != null && group.getMemberIds().contains(uid)) {
                    joined.add(group);
                }
            }
        }
        joinedGroups.setValue(joined);
    }

    public void signOut() {
        AuthManager.getInstance().signOut();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (eventsRegistration != null) {
            eventsRegistration.remove();
            eventsRegistration = null;
        }
        if (groupsRegistration != null) {
            groupsRegistration.remove();
            groupsRegistration = null;
        }
    }
}
