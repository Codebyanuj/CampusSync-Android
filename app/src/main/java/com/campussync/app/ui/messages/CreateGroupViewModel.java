package com.campussync.app.ui.messages;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.R;
import com.campussync.app.firebase.MessagingRepository;
import com.campussync.app.model.Conversation;
import com.campussync.app.model.UserProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Creates a group chat: name, photo, member selection. */
public class CreateGroupViewModel extends ViewModel {

    private final MessagingRepository repository = MessagingRepository.getInstance();

    private final MutableLiveData<List<UserProfile>> users =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> creating = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();
    private final MutableLiveData<Conversation> created = new MutableLiveData<>();

    private final Set<String> selected = new HashSet<>();
    private Uri photoUri;

    public LiveData<List<UserProfile>> getUsers() {
        return users;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getCreating() {
        return creating;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Integer> getMessageRes() {
        return messageRes;
    }

    public LiveData<Conversation> getCreated() {
        return created;
    }

    public Uri getPhotoUri() {
        return photoUri;
    }

    public void setPhotoUri(Uri uri) {
        photoUri = uri;
    }

    public void search(String query) {
        loading.setValue(true);
        repository.searchUsers(query, (List<UserProfile> result, Exception exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load people. Check your connection.");
                return;
            }
            users.setValue(result != null ? result : new ArrayList<>());
        });
    }

    public List<UserProfile> selectedUsers(Set<String> ids) {
        List<UserProfile> chosen = new ArrayList<>();
        List<UserProfile> source = users.getValue();
        if (source == null || ids == null) return chosen;
        for (UserProfile profile : source) {
            if (profile.getId() != null && ids.contains(profile.getId())) chosen.add(profile);
        }
        return chosen;
    }

    public Set<String> getSelectedIds() {
        return new HashSet<>(selected);
    }

    public void setSelected(Set<String> ids) {
        selected.clear();
        if (ids != null) selected.addAll(ids);
    }

    /** Validates, uploads the photo when one was picked, then creates the group. */
    public void create(String name, Set<String> selectedIds) {
        String title = name != null ? name.trim() : "";
        if (title.isEmpty()) {
            messageRes.setValue(R.string.error_group_name_required);
            return;
        }
        List<UserProfile> chosen = selectedUsers(selectedIds);
        if (chosen.isEmpty()) {
            messageRes.setValue(R.string.error_group_members_required);
            return;
        }
        creating.setValue(true);
        if (photoUri == null) {
            createGroup(title, null, chosen);
            return;
        }
        repository.uploadGroupPhoto(photoUri, (String url, Exception photoError) -> {
            if (photoError != null) {
                creating.setValue(false);
                this.error.setValue("The group photo could not be uploaded. Try again without it.");
                return;
            }
            createGroup(title, url, chosen);
        });
    }

    private void createGroup(String title, String photoUrl, List<UserProfile> members) {
        repository.createGroup(title, photoUrl, members, (Conversation conversation,
                                                          Exception exception) -> {
            creating.setValue(false);
            if (exception != null || conversation == null) {
                error.setValue("The group could not be created. Please try again.");
                return;
            }
            created.setValue(conversation);
        });
    }

    public void clearError() {
        error.setValue(null);
    }

    public void clearMessage() {
        messageRes.setValue(null);
    }
}
