package com.campussync.app.ui.groups;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.FirestoreManager;
import com.campussync.app.model.Group;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the groups tab.
 */
public class GroupsViewModel extends ViewModel {

    private final FirestoreManager firestoreManager = FirestoreManager.getInstance();
    private final MutableLiveData<List<Group>> groups = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> empty = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration registration;

    public LiveData<List<Group>> getGroups() {
        return groups;
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
        registration = firestoreManager.listenToGroups((list, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load groups. Check your connection and retry.");
                empty.setValue(false);
                return;
            }
            List<Group> result = list != null ? list : new ArrayList<>();
            groups.setValue(result);
            empty.setValue(result.isEmpty());
        });
    }

    public void refresh() {
        retry();
    }

    public void retry() {
        stopListening();
        startListening();
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
