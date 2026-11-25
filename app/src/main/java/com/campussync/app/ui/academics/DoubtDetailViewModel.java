package com.campussync.app.ui.academics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.TutoringRepository;
import com.campussync.app.model.Doubt;
import com.campussync.app.model.DoubtAnswer;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** A doubt thread: the question, its answers, accepting an answer and marking it solved. */
public class DoubtDetailViewModel extends ViewModel {

    private final TutoringRepository repository = TutoringRepository.getInstance();

    private final MutableLiveData<Doubt> doubt = new MutableLiveData<>();
    private final MutableLiveData<List<DoubtAnswer>> answers =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);

    private String doubtId;
    private ListenerRegistration doubtRegistration;
    private ListenerRegistration answersRegistration;

    public LiveData<Doubt> getDoubt() {
        return doubt;
    }

    public LiveData<List<DoubtAnswer>> getAnswers() {
        return answers;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<Boolean> getBusy() {
        return busy;
    }

    public void clearMessage() {
        message.setValue(null);
    }

    public boolean isAsker() {
        Doubt current = doubt.getValue();
        String uid = AuthManager.getInstance().getCurrentUserId();
        return current != null && uid != null && uid.equals(current.getAuthorId());
    }

    public void start(String doubtId) {
        if (doubtId == null || doubtId.equals(this.doubtId)) return;
        stopListening();
        this.doubtId = doubtId;
        doubtRegistration = repository.listenToDoubt(doubtId, (value, error) -> {
            if (error != null) {
                message.setValue("Unable to load this doubt.");
                return;
            }
            doubt.setValue(value);
        });
        answersRegistration = repository.listenToAnswers(doubtId, (list, error) -> {
            if (error != null) {
                message.setValue("Unable to load the answers.");
                return;
            }
            answers.setValue(list);
        });
    }

    public void answer(String body) {
        Doubt current = doubt.getValue();
        if (current == null || current.getId() == null) return;
        if (body == null || body.trim().isEmpty()) {
            message.setValue("Write your answer first");
            return;
        }
        busy.setValue(true);
        DoubtAnswer answer = new DoubtAnswer();
        answer.setBody(body);
        if (AuthManager.getInstance().getCurrentUser() != null) {
            answer.setAuthorName(AuthManager.getInstance().getCurrentUser().getDisplayName());
        }
        repository.answerDoubt(current.getId(), answer, error -> {
            busy.setValue(false);
            if (error != null) {
                message.setValue(error.getMessage() != null
                        ? error.getMessage() : "Could not post your answer");
                return;
            }
            message.setValue("Answer posted");
        });
    }

    public void accept(DoubtAnswer answer) {
        Doubt current = doubt.getValue();
        if (current == null) return;
        busy.setValue(true);
        repository.acceptAnswer(current, answer, error -> {
            busy.setValue(false);
            message.setValue(error == null
                    ? "Answer accepted — the answerer earns karma"
                    : error.getMessage() != null ? error.getMessage()
                            : "Could not accept this answer");
        });
    }

    public void markSolved() {
        Doubt current = doubt.getValue();
        if (current == null) return;
        busy.setValue(true);
        repository.markSolved(current, error -> {
            busy.setValue(false);
            message.setValue(error == null ? "Marked as solved"
                    : error.getMessage() != null ? error.getMessage()
                            : "Could not update this doubt");
        });
    }

    private void stopListening() {
        if (doubtRegistration != null) doubtRegistration.remove();
        if (answersRegistration != null) answersRegistration.remove();
        doubtRegistration = null;
        answersRegistration = null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }
}
