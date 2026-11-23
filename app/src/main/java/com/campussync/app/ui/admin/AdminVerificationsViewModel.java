package com.campussync.app.ui.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.VerificationRequest;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Administrator review queue for pending verification requests.
 *
 * The screen is only reachable for accounts with an {@code admins/{uid}} document, and the write
 * itself is authorised by Firestore rules on the server.
 */
public class AdminVerificationsViewModel extends ViewModel {

    private final UserRepository repository = UserRepository.getInstance();

    private final MutableLiveData<List<VerificationRequest>> requests =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> reviewing = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration registration;

    public LiveData<List<VerificationRequest>> getRequests() {
        return requests;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getReviewing() {
        return reviewing;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void startListening() {
        if (registration != null) return;
        registration = repository.listenToVerificationRequests(
                VerificationRequest.STATUS_PENDING, (value, exception) -> {
                    loading.setValue(false);
                    if (exception != null) {
                        error.setValue("Could not load verification requests.");
                        return;
                    }
                    requests.setValue(value != null ? value : new ArrayList<>());
                });
    }

    public void review(VerificationRequest request, boolean approve, String note) {
        if (request == null || request.getId() == null) return;
        reviewing.setValue(true);
        repository.reviewRequest(request.getId(), approve, note, exception -> {
            reviewing.setValue(false);
            if (exception != null) {
                // A non-administrator is rejected here by the security rules.
                error.setValue("Could not update the request. Administrator access is required.");
                return;
            }
            message.setValue(approve ? "Request approved." : "Request rejected.");
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
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
