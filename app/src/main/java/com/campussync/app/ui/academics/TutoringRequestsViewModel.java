package com.campussync.app.ui.academics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.TutoringRepository;
import com.campussync.app.model.TutoringRequest;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Requests the signed-in user sent and received, with accept/decline/cancel/review actions. */
public class TutoringRequestsViewModel extends ViewModel {

    private final TutoringRepository repository = TutoringRepository.getInstance();

    private final MutableLiveData<List<TutoringRequest>> requests =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);

    private ListenerRegistration registration;

    public LiveData<List<TutoringRequest>> getRequests() {
        return requests;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<Boolean> getBusy() {
        return busy;
    }

    public String getMyUid() {
        return AuthManager.getInstance().getCurrentUserId();
    }

    public void start() {
        if (registration != null) return;
        registration = repository.listenToRequests((list, error) -> {
            if (error != null) {
                message.setValue("Unable to load your tutoring requests.");
                return;
            }
            requests.setValue(list);
        });
    }

    public void respond(TutoringRequest request, boolean accept) {
        busy.setValue(true);
        repository.respondToRequest(request, accept, error -> {
            busy.setValue(false);
            message.setValue(error == null
                    ? (accept ? "Request accepted — say hello in chat" : "Request declined")
                    : errorMessage(error));
        });
    }

    public void cancel(TutoringRequest request) {
        busy.setValue(true);
        repository.cancelRequest(request, error -> {
            busy.setValue(false);
            message.setValue(error == null ? "Request cancelled" : errorMessage(error));
        });
    }

    public void completeAndReview(TutoringRequest request, long rating, String comment) {
        if (rating < 1) {
            message.setValue("Choose a star rating");
            return;
        }
        busy.setValue(true);
        repository.completeAndReview(request, rating, comment, error -> {
            busy.setValue(false);
            message.setValue(error == null
                    ? "Session completed — thanks for the review"
                    : errorMessage(error));
        });
    }

    private static String errorMessage(Exception error) {
        return error.getMessage() != null ? error.getMessage()
                : "Something went wrong. Try again.";
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
