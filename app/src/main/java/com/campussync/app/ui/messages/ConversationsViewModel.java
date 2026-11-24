package com.campussync.app.ui.messages;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.MessagingRepository;
import com.campussync.app.model.Conversation;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Conversation list state: live threads, unread totals and blocked accounts (a blocked partner's
 * direct thread is hidden from the list).
 */
public class ConversationsViewModel extends ViewModel {

    private final MessagingRepository repository = MessagingRepository.getInstance();

    private final MutableLiveData<List<Conversation>> conversations =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration conversationsRegistration;
    private ListenerRegistration blockedRegistration;
    private final Set<String> blockedIds = new HashSet<>();
    private List<Conversation> latest = new ArrayList<>();
    private boolean started;

    public LiveData<List<Conversation>> getConversations() {
        return conversations;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void start() {
        if (started) return;
        started = true;
        loading.setValue(true);
        blockedRegistration = repository.listenToBlockedUsers((blocked, blockedError) -> {
            blockedIds.clear();
            if (blocked != null) {
                for (MessagingRepository.BlockedUser user : blocked) blockedIds.add(user.uid);
            }
            publish();
        });
        conversationsRegistration = repository.listenToConversations((list, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load your messages. Check your connection and retry.");
                return;
            }
            latest = list != null ? list : new ArrayList<>();
            publish();
        });
    }

    private void publish() {
        List<Conversation> visible = new ArrayList<>();
        for (Conversation conversation : latest) {
            boolean blockedDirect = !conversation.isGroup()
                    && conversation.getOtherUserId() != null
                    && blockedIds.contains(conversation.getOtherUserId());
            if (!blockedDirect) visible.add(conversation);
        }
        conversations.setValue(visible);
    }

    /** Re-attaches both listeners, used by pull-to-refresh. */
    public void refresh() {
        if (conversationsRegistration != null) {
            conversationsRegistration.remove();
            conversationsRegistration = null;
        }
        if (blockedRegistration != null) {
            blockedRegistration.remove();
            blockedRegistration = null;
        }
        started = false;
        start();
    }

    public void clearError() {
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (conversationsRegistration != null) conversationsRegistration.remove();
        if (blockedRegistration != null) blockedRegistration.remove();
    }
}
