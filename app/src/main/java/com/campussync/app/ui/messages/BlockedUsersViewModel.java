package com.campussync.app.ui.messages;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.R;
import com.campussync.app.firebase.MessagingRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Blocked-account list with unblock. */
public class BlockedUsersViewModel extends ViewModel {

    private final MessagingRepository repository = MessagingRepository.getInstance();

    private final MutableLiveData<List<MessagingRepository.BlockedUser>> blocked =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();

    private ListenerRegistration registration;

    public LiveData<List<MessagingRepository.BlockedUser>> getBlocked() {
        return blocked;
    }

    public LiveData<Integer> getMessageRes() {
        return messageRes;
    }

    public void start() {
        if (registration != null) return;
        registration = repository.listenToBlockedUsers((list, error) -> {
            if (list != null) blocked.setValue(list);
        });
    }

    public void unblock(MessagingRepository.BlockedUser user) {
        if (user == null) return;
        repository.unblockUser(user.uid, exception ->
                messageRes.setValue(exception == null
                        ? R.string.unblocked_toast : R.string.error_generic));
    }

    public void clearMessage() {
        messageRes.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (registration != null) registration.remove();
    }
}
