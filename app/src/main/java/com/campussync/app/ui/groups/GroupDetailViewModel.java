package com.campussync.app.ui.groups;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.FirestoreManager;
import com.campussync.app.model.Group;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for a single group, including membership state and member names.
 */
public class GroupDetailViewModel extends ViewModel {

    private final FirestoreManager firestoreManager = FirestoreManager.getInstance();
    private final MutableLiveData<Group> group = new MutableLiveData<>();
    private final MutableLiveData<Boolean> member = new MutableLiveData<>(false);
    private final MutableLiveData<List<String>> memberNames = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> actionInProgress = new MutableLiveData<>(false);
    private final MutableLiveData<String> actionMessage = new MutableLiveData<>();

    private String groupId;
    private ListenerRegistration registration;

    public LiveData<Group> getGroup() {
        return group;
    }

    public LiveData<Boolean> getMember() {
        return member;
    }

    public LiveData<List<String>> getMemberNames() {
        return memberNames;
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

    public void load(String groupId) {
        if (this.groupId != null && this.groupId.equals(groupId) && registration != null) return;
        this.groupId = groupId;
        loading.setValue(true);
        error.setValue(null);
        if (registration != null) registration.remove();

        registration = firestoreManager.listenToGroups((groups, exception) -> {
            if (exception != null) {
                loading.setValue(false);
                error.setValue("Unable to load this group.");
                return;
            }
            Group found = null;
            if (groups != null) {
                for (Group candidate : groups) {
                    if (groupId.equals(candidate.getId())) {
                        found = candidate;
                        break;
                    }
                }
            }
            loading.setValue(false);
            if (found == null) {
                error.setValue("This group is no longer available.");
                return;
            }
            publish(found);
        });
    }

    private void publish(Group found) {
        group.setValue(found);
        String uid = AuthManager.getInstance().getCurrentUserId();
        List<String> ids = found.getMemberIds();
        member.setValue(uid != null && ids != null && ids.contains(uid));

        if (ids == null || ids.isEmpty()) {
            memberNames.setValue(new ArrayList<>());
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
                memberNames.setValue(names);
            }

            @Override
            public void onError(Exception exception) {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < ids.size(); i++) names.add("Member");
                memberNames.setValue(names);
            }
        });
    }

    public void toggleMembership() {
        if (groupId == null || Boolean.TRUE.equals(actionInProgress.getValue())) return;
        boolean isMember = Boolean.TRUE.equals(member.getValue());
        actionInProgress.setValue(true);

        AuthManager.ActionListener listener = exception -> {
            actionInProgress.setValue(false);
            if (exception != null) {
                actionMessage.setValue(exception.getMessage() != null
                        ? exception.getMessage()
                        : "Could not update membership. Try again.");
            } else {
                member.setValue(!isMember);
                actionMessage.setValue(!isMember ? "You joined this group." : "You left this group.");
            }
        };

        if (isMember) {
            firestoreManager.leaveGroup(groupId, listener);
        } else {
            firestoreManager.joinGroup(groupId, listener);
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
