package com.campussync.app.ui.academics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.MessagingRepository;
import com.campussync.app.firebase.TutoringRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.Conversation;
import com.campussync.app.model.TutorProfile;
import com.campussync.app.model.TutoringRequest;
import com.campussync.app.model.UserProfile;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Tutor profile: rating, subjects, reviews, a tutoring request and the existing chat module. */
public class TutorProfileViewModel extends ViewModel {

    private final TutoringRepository tutoring = TutoringRepository.getInstance();
    private final MessagingRepository messaging = MessagingRepository.getInstance();

    private final MutableLiveData<TutorProfile> tutor = new MutableLiveData<>();
    private final MutableLiveData<List<TutoringRequest>> reviews =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> conversationId = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);

    private String tutorId;
    private ListenerRegistration tutorRegistration;
    private ListenerRegistration reviewRegistration;

    public LiveData<TutorProfile> getTutor() {
        return tutor;
    }

    public LiveData<List<TutoringRequest>> getReviews() {
        return reviews;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<String> getConversationId() {
        return conversationId;
    }

    public LiveData<Boolean> getBusy() {
        return busy;
    }

    public void clearMessage() {
        message.setValue(null);
    }

    public void clearConversation() {
        conversationId.setValue(null);
    }

    public void start(String tutorId) {
        if (tutorId == null || tutorId.equals(this.tutorId)) return;
        stopListening();
        this.tutorId = tutorId;
        tutorRegistration = tutoring.listenToTutor(tutorId, (value, error) -> {
            if (error != null) {
                message.setValue("Unable to load this tutor.");
                return;
            }
            tutor.setValue(value);
        });
        reviewRegistration = tutoring.listenToTutorReviews(tutorId, (list, error) -> {
            if (error == null) reviews.setValue(list);
        });
    }

    /** Sends a tutoring request; the tutor answers it from the requests screen. */
    public void requestSession(String subject, String details, String preferredTime) {
        TutorProfile current = tutor.getValue();
        if (current == null) {
            message.setValue("This tutor is still loading.");
            return;
        }
        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid != null && uid.equals(current.getId())) {
            message.setValue("This is your own tutor listing.");
            return;
        }
        busy.setValue(true);
        TutoringRequest request = new TutoringRequest();
        request.setTutorId(current.getId());
        request.setTutorName(current.getDisplayName());
        request.setStudentName(AuthManager.getInstance().getCurrentUser() != null
                ? AuthManager.getInstance().getCurrentUser().getDisplayName() : null);
        request.setSubject(subject);
        request.setMessage(details);
        request.setPreferredTime(preferredTime);
        tutoring.createRequest(request, error -> {
            busy.setValue(false);
            message.setValue(error == null
                    ? "Request sent to " + (current.getDisplayName() != null
                            ? current.getDisplayName() : "the tutor")
                    : error.getMessage() != null ? error.getMessage()
                            : "Could not send the request");
        });
    }

    /** Opens the direct chat with the tutor using the existing messaging module. */
    public void openChat() {
        TutorProfile current = tutor.getValue();
        if (current == null || current.getId() == null) return;
        busy.setValue(true);
        UserProfile partner = new UserProfile();
        partner.setId(current.getId());
        partner.setDisplayName(current.getDisplayName());
        partner.setPhotoUrl(current.getPhotoUrl());
        partner.setCollege(current.getCollege());
        partner.setDepartment(current.getDepartment());
        partner.setSemester(current.getSemester());
        messaging.getOrCreateDirectConversation(partner, new MessagingRepository.ConversationListener() {
            @Override
            public void onConversation(Conversation conversation, Exception error) {
                busy.setValue(false);
                if (error != null || conversation == null || conversation.getId() == null) {
                    message.setValue(error != null && error.getMessage() != null
                            ? error.getMessage() : "Could not open the chat");
                    return;
                }
                conversationId.setValue(conversation.getId());
            }
        });
    }

    private void stopListening() {
        if (tutorRegistration != null) tutorRegistration.remove();
        if (reviewRegistration != null) reviewRegistration.remove();
        tutorRegistration = null;
        reviewRegistration = null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }
}
