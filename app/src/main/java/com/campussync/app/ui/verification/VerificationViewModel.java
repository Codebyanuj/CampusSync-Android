package com.campussync.app.ui.verification;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.UserProfile;
import com.campussync.app.model.VerificationRequest;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Student/faculty verification: submits a request and tracks its status.
 *
 * Approval is performed by an administrator and enforced by security rules, so this ViewModel
 * never changes the verification status of the profile.
 */
public class VerificationViewModel extends ViewModel {

    private final UserRepository repository = UserRepository.getInstance();

    private final MutableLiveData<UserProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<VerificationRequest> request = new MutableLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration profileRegistration;
    private ListenerRegistration requestRegistration;

    public LiveData<UserProfile> getProfile() {
        return profile;
    }

    public LiveData<VerificationRequest> getRequest() {
        return request;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void startListening() {
        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid == null) return;
        if (profileRegistration == null) {
            profileRegistration = repository.listenToProfile(uid, (value, exception) ->
                    profile.setValue(value));
        }
        if (requestRegistration == null) {
            requestRegistration = repository.listenToMyVerificationRequest(uid,
                    (value, exception) -> request.setValue(value));
        }
    }

    public void submit(String idNumber, String note) {
        UserProfile current = profile.getValue();
        if (current == null) {
            error.setValue("Your profile is still loading. Try again in a moment.");
            return;
        }
        if (current.isVerified()) {
            message.setValue("Your account is already verified.");
            return;
        }
        VerificationRequest existing = request.getValue();
        // Only a pending request blocks a new one: a rejected applicant may apply again.
        if (current.isVerificationPending() || (existing != null && existing.isPending())) {
            message.setValue("You already have a pending request.");
            return;
        }

        VerificationRequest newRequest = new VerificationRequest();
        newRequest.setName(current.getDisplayName() != null
                ? current.getDisplayName() : "");
        newRequest.setEmail(current.getEmail() != null ? current.getEmail() : "");
        newRequest.setRole(current.getRole());
        newRequest.setCollege(current.getCollege() != null ? current.getCollege() : "");
        newRequest.setDepartment(current.getDepartment() != null ? current.getDepartment() : "");
        newRequest.setCourse(current.getCourse() != null ? current.getCourse() : "");
        newRequest.setSemester(current.getSemester());
        newRequest.setAcademicYear(current.getAcademicYear() != null
                ? current.getAcademicYear() : "");
        newRequest.setDesignation(current.getDesignation() != null
                ? current.getDesignation() : "");
        newRequest.setIdNumber(idNumber.trim());
        newRequest.setNote(note != null ? note.trim() : "");

        submitting.setValue(true);
        repository.submitVerificationRequest(newRequest, exception -> {
            submitting.setValue(false);
            if (exception != null) {
                error.setValue("Could not submit the request. Check your connection and try again.");
                return;
            }
            message.setValue("Verification request submitted. An administrator will review it.");
        });
    }

    public void clearMessage() {
        message.setValue(null);
    }

    public void clearError() {
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (profileRegistration != null) {
            profileRegistration.remove();
            profileRegistration = null;
        }
        if (requestRegistration != null) {
            requestRegistration.remove();
            requestRegistration = null;
        }
    }
}
