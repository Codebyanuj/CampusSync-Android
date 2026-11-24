package com.campussync.app.ui.messages;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.R;
import com.campussync.app.firebase.MessagingRepository;
import com.campussync.app.model.Conversation;
import com.campussync.app.model.ConversationMember;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Group details: members, admin actions, rename, photo, mute and leaving. */
public class GroupInfoViewModel extends ViewModel {

    private final MessagingRepository repository = MessagingRepository.getInstance();

    private final MutableLiveData<Conversation> conversation = new MutableLiveData<>();
    private final MutableLiveData<List<ConversationMember>> members =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> admin = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> muted = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> leftGroup = new MutableLiveData<>(false);

    private ListenerRegistration conversationRegistration;
    private ListenerRegistration membersRegistration;
    private String conversationId;
    private String currentUserId;

    public LiveData<Conversation> getConversation() {
        return conversation;
    }

    public LiveData<List<ConversationMember>> getMembers() {
        return members;
    }

    public LiveData<Boolean> getAdmin() {
        return admin;
    }

    public LiveData<Boolean> getMuted() {
        return muted;
    }

    public LiveData<Boolean> getBusy() {
        return busy;
    }

    public LiveData<Integer> getMessageRes() {
        return messageRes;
    }

    public LiveData<String> getError() {
        return error;
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
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        conversationRegistration = repository.listenToConversation(conversationId,
                (Conversation current, Exception exception) -> {
                    if (exception != null) {
                        error.setValue("Unable to load this group.");
                        return;
                    }
                    conversation.setValue(current);
                    if (current != null) {
                        admin.setValue(current.isAdmin(currentUserId));
                    }
                });

        membersRegistration = repository.listenToMembers(conversationId,
                (List<ConversationMember> list, Exception exception) -> {
                    if (exception != null) return;
                    members.setValue(list != null ? list : new ArrayList<>());
                    if (currentUserId != null && list != null) {
                        for (ConversationMember member : list) {
                            if (currentUserId.equals(member.getUid())) {
                                muted.setValue(member.isMuted());
                                break;
                            }
                        }
                    }
                });
    }

    public void rename(String title) {
        if (title == null || title.trim().isEmpty()) {
            messageRes.setValue(R.string.error_group_name_required);
            return;
        }
        busy.setValue(true);
        repository.updateGroupInfo(conversationId, title, null, exception -> {
            busy.setValue(false);
            messageRes.setValue(exception == null
                    ? R.string.group_updated : R.string.error_generic);
        });
    }

    public void changePhoto(Uri uri) {
        if (uri == null) return;
        busy.setValue(true);
        repository.uploadGroupPhoto(uri, (String url, Exception uploadError) -> {
            if (uploadError != null) {
                busy.setValue(false);
                error.setValue("The photo could not be uploaded.");
                return;
            }
            repository.updateGroupInfo(conversationId, null, url, exception -> {
                busy.setValue(false);
                messageRes.setValue(exception == null
                        ? R.string.group_photo_updated : R.string.error_generic);
            });
        });
    }

    public void setAdmin(ConversationMember member, boolean makeAdmin) {
        if (member == null) return;
        busy.setValue(true);
        repository.setMemberAdmin(conversationId, member.getUid(), makeAdmin, exception -> {
            busy.setValue(false);
            messageRes.setValue(exception == null
                    ? (makeAdmin ? R.string.admin_granted : R.string.admin_revoked)
                    : R.string.error_generic);
        });
    }

    public void removeMember(ConversationMember member) {
        if (member == null) return;
        busy.setValue(true);
        repository.removeMember(conversationId, member.getUid(), exception -> {
            busy.setValue(false);
            messageRes.setValue(exception == null ? R.string.member_removed
                    : R.string.error_generic);
        });
    }

    public void setMuted(boolean value) {
        busy.setValue(true);
        repository.setMuted(conversationId, value, exception -> {
            busy.setValue(false);
            if (exception == null) {
                muted.setValue(value);
                messageRes.setValue(value ? R.string.muted_toast : R.string.unmuted_toast);
            } else {
                error.setValue("Could not update notifications.");
            }
        });
    }

    public void leaveGroup() {
        busy.setValue(true);
        repository.leaveGroup(conversationId, exception -> {
            busy.setValue(false);
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

    @Override
    protected void onCleared() {
        super.onCleared();
        if (conversationRegistration != null) conversationRegistration.remove();
        if (membersRegistration != null) membersRegistration.remove();
    }
}
