package com.campussync.app.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.FirestoreManager;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.Event;
import com.campussync.app.model.Group;
import com.campussync.app.model.UserProfile;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the profile tab: the profile document plus the user's joined events and groups.
 */
public class ProfileViewModel extends ViewModel {

    private final FirestoreManager firestoreManager = FirestoreManager.getInstance();
    private final UserRepository userRepository = UserRepository.getInstance();

    private final MutableLiveData<UserProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<Boolean> admin = new MutableLiveData<>(false);
    private final MutableLiveData<List<Event>> joinedEvents = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Group>> joinedGroups = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration profileRegistration;
    private ListenerRegistration eventsRegistration;
    private ListenerRegistration groupsRegistration;

    public LiveData<UserProfile> getProfile() {
        return profile;
    }

    public LiveData<Boolean> getAdmin() {
        return admin;
    }

    public LiveData<List<Event>> getJoinedEvents() {
        return joinedEvents;
    }

    public LiveData<List<Group>> getJoinedGroups() {
        return joinedGroups;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void startListening() {
        if (profileRegistration == null) {
            String uid = AuthManager.getInstance().getCurrentUserId();
            profileRegistration = userRepository.listenToProfile(uid, (value, exception) -> {
                loading.setValue(false);
                if (exception != null) {
                    error.setValue("Could not load your profile.");
                    return;
                }
                profile.setValue(value);
            });
            userRepository.checkIsAdmin((isAdmin, exception) ->
                    admin.setValue(Boolean.TRUE.equals(isAdmin)));
        }
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
        if (profileRegistration != null) {
            profileRegistration.remove();
            profileRegistration = null;
        }
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
