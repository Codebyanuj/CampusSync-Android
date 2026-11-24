package com.campussync.app.ui.messages;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.MessagingRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.Conversation;
import com.campussync.app.model.UserProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * People picker used for two flows: opening a direct chat and adding members to an existing group.
 */
public class UserPickerViewModel extends ViewModel {

    public static final String MODE_DIRECT = "direct";
    public static final String MODE_ADD_MEMBERS = "addMembers";

    private final MessagingRepository repository = MessagingRepository.getInstance();

    private final MutableLiveData<List<UserProfile>> users =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Conversation> openedConversation = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();
    private final MutableLiveData<Boolean> membersAdded = new MutableLiveData<>(false);

    private final Set<String> excluded = new HashSet<>();
    private String mode = MODE_DIRECT;
    private String conversationId;

    public LiveData<List<UserProfile>> getUsers() {
        return users;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Conversation> getOpenedConversation() {
        return openedConversation;
    }

    public LiveData<Integer> getMessageRes() {
        return messageRes;
    }

    public LiveData<Boolean> getMembersAdded() {
        return membersAdded;
    }

    public String getMode() {
        return mode;
    }

    /** Excludes accounts already in the group (add-members mode) from the results. */
    public void configure(String mode, String conversationId) {
        this.mode = mode != null ? mode : MODE_DIRECT;
        this.conversationId = conversationId;
        if (MODE_ADD_MEMBERS.equals(this.mode) && conversationId != null) {
            repository.listenToConversation(conversationId, (Conversation conversation,
                                                           Exception exception) -> {
                if (conversation == null || conversation.getMemberIds() == null) return;
                excluded.clear();
                excluded.addAll(conversation.getMemberIds());
                if (users.getValue() != null) {
                    users.setValue(filter(users.getValue()));
                }
            });
        }
    }

    public void search(String query) {
        loading.setValue(true);
        repository.searchUsers(query, (List<UserProfile> result, Exception exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load people. Check your connection.");
                return;
            }
            users.setValue(filter(result != null ? result : new ArrayList<>()));
        });
    }

    private List<UserProfile> filter(List<UserProfile> source) {
        List<UserProfile> filtered = new ArrayList<>();
        for (UserProfile profile : source) {
            if (profile.getId() != null && excluded.contains(profile.getId())) continue;
            filtered.add(profile);
        }
        return filtered;
    }

    /** Direct mode: opens the existing thread or creates it. */
    public void openChatWith(UserProfile user) {
        if (user == null) return;
        final String name = user.getDisplayName();
        repository.isBlocked(user.getId(), (isBlocked, exception) -> {
            if (Boolean.TRUE.equals(isBlocked)) {
                messageRes.setValue(com.campussync.app.R.string.chat_blocked_notice);
                return;
            }
            repository.getOrCreateDirectConversation(user, (Conversation conversation,
                                                            Exception failure) -> {
                if (failure != null || conversation == null) {
                    reportError(failure);
                    return;
                }
                openedConversation.setValue(conversation);
            });
        });
        if (name == null) {
            // Name is only used for the report label elsewhere; nothing else to do here.
        }
    }

    /** Adds the selected accounts to the group being managed. */
    public void addSelected(Set<String> selectedIds, List<UserProfile> current) {
        if (selectedIds == null || selectedIds.isEmpty() || conversationId == null) {
            messageRes.setValue(com.campussync.app.R.string.error_group_members_required);
            return;
        }
        List<UserProfile> chosen = new ArrayList<>();
        if (current != null) {
            for (UserProfile profile : current) {
                if (profile.getId() != null && selectedIds.contains(profile.getId())) {
                    chosen.add(profile);
                }
            }
        }
        repository.addMembers(conversationId, chosen, error -> {
            if (error != null) {
                this.error.setValue("Could not add members. Please try again.");
                return;
            }
            messageRes.setValue(com.campussync.app.R.string.members_added);
            membersAdded.setValue(true);
        });
    }

    private void reportError(Exception exception) {
        if (exception != null && exception.getMessage() != null) {
            error.setValue(exception.getMessage());
        } else {
            error.setValue("Could not open the conversation.");
        }
    }

    public void clearError() {
        error.setValue(null);
    }

    public void clearMessage() {
        messageRes.setValue(null);
    }
}
