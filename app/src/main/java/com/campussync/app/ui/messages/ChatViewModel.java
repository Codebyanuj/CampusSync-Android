package com.campussync.app.ui.messages;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.R;
import com.campussync.app.firebase.MessagingRepository;
import com.campussync.app.model.ContentReport;
import com.campussync.app.model.Conversation;
import com.campussync.app.model.Message;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chat state: live latest page, paginated history, typing indicators, read receipts, sending
 * text and attachments, mute, block and report.
 */
public class ChatViewModel extends ViewModel {

    private final MessagingRepository repository = MessagingRepository.getInstance();

    private final MutableLiveData<List<Message>> messages =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Conversation> conversation = new MutableLiveData<>();
    private final MutableLiveData<String> typingName = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> blocked = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> uploading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();
    private final MutableLiveData<Boolean> leftGroup = new MutableLiveData<>(false);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Message> older = new ArrayList<>();
    private final Set<String> seenIds = new HashSet<>();

    private ListenerRegistration messagesRegistration;
    private ListenerRegistration conversationRegistration;
    private ListenerRegistration typingRegistration;

    private String conversationId;
    private Runnable stopTyping;
    private long lastTypingSentAt;
    private boolean loadingOlder;

    public LiveData<List<Message>> getMessages() {
        return messages;
    }

    public LiveData<Conversation> getConversation() {
        return conversation;
    }

    public LiveData<String> getTypingName() {
        return typingName;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getHasMore() {
        return hasMore;
    }

    public LiveData<Boolean> getBlocked() {
        return blocked;
    }

    public LiveData<Boolean> getUploading() {
        return uploading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Integer> getMessageRes() {
        return messageRes;
    }

    public LiveData<Boolean> getLeftGroup() {
        return leftGroup;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void start(String conversationId) {
        if (conversationId == null || conversationId.equals(this.conversationId)) return;
        this.conversationId = conversationId;
        detach();
        older.clear();
        seenIds.clear();
        loading.setValue(true);

        conversationRegistration = repository.listenToConversation(conversationId,
                (Conversation current, Exception exception) -> {
                    if (exception != null) return;
                    conversation.setValue(current);
                    checkBlocked(current);
                });

        messagesRegistration = repository.listenToMessages(conversationId,
                (List<Message> page, boolean more, Exception exception) -> {
                    loading.setValue(false);
                    if (exception != null) {
                        error.setValue("Unable to load messages. Check your connection.");
                        return;
                    }
                    List<Message> merged = new ArrayList<>(older);
                    Set<String> mergedIds = new HashSet<>();
                    for (Message message : merged) {
                        if (message.getId() != null) mergedIds.add(message.getId());
                    }
                    if (page != null) {
                        for (Message message : page) {
                            if (message.getId() == null || mergedIds.add(message.getId())) {
                                merged.add(message);
                            }
                        }
                    }
                    messages.setValue(merged);
                    if (more) {
                        hasMore.setValue(true);
                    } else if (older.isEmpty()) {
                        hasMore.setValue(false);
                    }
                    repository.markConversationRead(conversationId, merged);
                });

        typingRegistration = repository.listenToTyping(conversationId,
                (String name, Exception exception) -> typingName.setValue(name));
    }

    private void checkBlocked(Conversation current) {
        if (current == null || current.isGroup()) {
            blocked.setValue(false);
            return;
        }
        String other = current.getOtherUserId();
        if (other == null) {
            blocked.setValue(false);
            return;
        }
        repository.isBlocked(other, (isBlocked, exception) ->
                blocked.setValue(Boolean.TRUE.equals(isBlocked)));
    }

    /** Loads an older page above what is already shown. */
    public void loadOlder() {
        if (loadingOlder || conversationId == null) return;
        List<Message> current = messages.getValue();
        if (current == null || current.isEmpty()) return;
        Date oldest = null;
        for (Message message : current) {
            if (message.getCreatedAt() != null) {
                oldest = message.getCreatedAt();
                break;
            }
        }
        if (oldest == null) return;
        loadingOlder = true;
        final Date before = oldest;
        repository.loadOlderMessages(conversationId, before,
                (List<Message> page, boolean more, Exception exception) -> {
                    loadingOlder = false;
                    if (exception != null || page == null || page.isEmpty()) {
                        hasMore.setValue(false);
                        return;
                    }
                    List<Message> prepend = new ArrayList<>();
                    Set<String> existingIds = new HashSet<>(seenIds);
                    for (Message message : page) {
                        if (message.getId() != null && existingIds.add(message.getId())) {
                            prepend.add(message);
                        }
                    }
                    older.addAll(0, prepend);
                    List<Message> merged = new ArrayList<>(older);
                    List<Message> latest = messages.getValue();
                    if (latest != null) merged.addAll(latest);
                    messages.setValue(merged);
                    hasMore.setValue(more);
                });
    }

    public void sendText(String text) {
        if (conversationId == null) return;
        String body = text != null ? text.trim() : "";
        if (body.isEmpty()) {
            messageRes.setValue(R.string.error_empty_message);
            return;
        }
        repository.sendMessage(conversationId, body, Message.TYPE_TEXT, null, null, 0, null,
                exception -> {
                    if (exception != null) {
                        error.setValue("Message could not be sent. Check your connection.");
                    }
                });
        clearTyping();
    }

    /** Uploads a picked file, then posts it as an image or document message. */
    public void sendAttachment(Uri uri, String fileNameHint) {
        if (conversationId == null || uri == null) return;
        uploading.setValue(true);
        repository.uploadAttachment(uri, conversationId, fileNameHint,
                (String url, String fileName, long size, String mimeType, Exception exception) -> {
                    uploading.setValue(false);
                    if (exception != null) {
                        error.setValue(exception.getMessage() != null
                                ? exception.getMessage()
                                : "Attachment could not be uploaded");
                        return;
                    }
                    String type = mimeType != null && mimeType.startsWith("image/")
                            ? Message.TYPE_IMAGE : Message.TYPE_DOCUMENT;
                    repository.sendMessage(conversationId, "", type, url, fileName, size,
                            mimeType, error -> {
                                if (error != null) {
                                    ChatViewModel.this.error
                                            .setValue("Message could not be sent.");
                                }
                            });
                });
    }

    /** Throttled typing marker so the other side sees "is typing…". */
    public void onDraftChanged() {
        if (conversationId == null) return;
        String name = myName();
        long now = System.currentTimeMillis();
        if (now - lastTypingSentAt > 2000) {
            lastTypingSentAt = now;
            repository.setTyping(conversationId, true, name);
        }
        if (stopTyping != null) handler.removeCallbacks(stopTyping);
        stopTyping = () -> repository.setTyping(conversationId, false, name);
        handler.postDelayed(stopTyping, 3000);
    }

    private void clearTyping() {
        if (stopTyping != null) handler.removeCallbacks(stopTyping);
        if (conversationId != null) {
            repository.setTyping(conversationId, false, myName());
        }
    }

    private String myName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return "Someone";
        String name = user.getDisplayName();
        return name != null && !name.trim().isEmpty() ? name : "Someone";
    }

    public void setMuted(boolean muted) {
        if (conversationId == null) return;
        repository.setMuted(conversationId, muted, exception ->
                messageRes.setValue(exception == null
                        ? (muted ? R.string.muted_toast : R.string.unmuted_toast)
                        : R.string.error_generic));
    }

    public void blockPartner() {
        Conversation current = conversation.getValue();
        if (current == null || current.isGroup()) return;
        String other = current.getOtherUserId();
        if (other == null) return;
        repository.blockUser(other, current.nameOf(other), exception -> {
            if (exception == null) {
                blocked.setValue(true);
                messageRes.setValue(R.string.blocked_toast);
            } else {
                error.setValue("Could not block this user.");
            }
        });
    }

    public void unblockPartner() {
        Conversation current = conversation.getValue();
        if (current == null) return;
        String other = current.getOtherUserId();
        if (other == null) return;
        repository.unblockUser(other, exception -> {
            if (exception == null) {
                blocked.setValue(false);
                messageRes.setValue(R.string.unblocked_toast);
            }
        });
    }

    public void reportPartner() {
        Conversation current = conversation.getValue();
        if (current == null || current.isGroup()) return;
        String other = current.getOtherUserId();
        if (other == null) return;
        ContentReport report = new ContentReport();
        report.setTargetType(ContentReport.TYPE_USER);
        report.setTargetId(other);
        report.setTargetLabel(current.nameOf(other) != null
                ? current.nameOf(other) : "CampusSync user");
        report.setReason("User report");
        report.setDetails("");
        repository.report(report, exception -> messageRes.setValue(exception == null
                ? R.string.report_sent : R.string.error_generic));
    }

    public void reportMessage(Message message, String reason) {
        if (message == null || message.getId() == null) return;
        ContentReport report = new ContentReport();
        report.setTargetType(ContentReport.TYPE_MESSAGE);
        report.setTargetId(message.getId());
        String label = message.hasText() ? message.getText() : "Attachment";
        report.setTargetLabel(label.length() > 60 ? label.substring(0, 60) + "…" : label);
        report.setReason(reason);
        report.setDetails("");
        repository.report(report, exception -> messageRes.setValue(exception == null
                ? R.string.report_sent : R.string.error_generic));
    }

    public void deleteMessage(Message message) {
        if (message == null || message.getId() == null || conversationId == null) return;
        repository.deleteMessage(conversationId, message.getId(), exception ->
                messageRes.setValue(exception == null
                        ? R.string.message_deleted : R.string.error_generic));
    }

    public void leaveGroup() {
        if (conversationId == null) return;
        repository.leaveGroup(conversationId, exception -> {
            if (exception == null) {
                leftGroup.setValue(true);
            } else {
                error.setValue("Could not leave the group.");
            }
        });
    }

    public void clearError() {
        error.setValue(null);
    }

    public void clearMessage() {
        messageRes.setValue(null);
    }

    private void detach() {
        if (messagesRegistration != null) {
            messagesRegistration.remove();
            messagesRegistration = null;
        }
        if (conversationRegistration != null) {
            conversationRegistration.remove();
            conversationRegistration = null;
        }
        if (typingRegistration != null) {
            typingRegistration.remove();
            typingRegistration = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        detach();
        if (stopTyping != null) handler.removeCallbacks(stopTyping);
        if (conversationId != null) repository.setTyping(conversationId, false, myName());
    }
}
