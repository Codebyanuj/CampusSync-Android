package com.campussync.app.ui.academics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.TutoringRepository;
import com.campussync.app.model.Doubt;

/** Posts a doubt to the peer board. */
public class CreateDoubtViewModel extends ViewModel {

    private final TutoringRepository repository = TutoringRepository.getInstance();

    private final MutableLiveData<Boolean> posting = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> posted = new MutableLiveData<>(false);

    public LiveData<Boolean> getPosting() {
        return posting;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<Boolean> getPosted() {
        return posted;
    }

    public void clearMessage() {
        message.setValue(null);
    }

    public void post(String title, String body, String subject, String department, long semester,
                     String college) {
        if (Boolean.TRUE.equals(posting.getValue())) return;
        if (title == null || title.trim().isEmpty()) {
            message.setValue("Add a short title for your doubt");
            return;
        }
        if (body == null || body.trim().isEmpty()) {
            message.setValue("Describe what you are stuck on");
            return;
        }
        posting.setValue(true);
        Doubt doubt = new Doubt();
        doubt.setTitle(title.trim());
        doubt.setBody(body.trim());
        doubt.setSubject(subject != null ? subject.trim() : "");
        doubt.setDepartment(department != null ? department.trim() : "");
        doubt.setSemester(semester);
        doubt.setCollege(college);
        if (AuthManager.getInstance().getCurrentUser() != null) {
            doubt.setAuthorName(AuthManager.getInstance().getCurrentUser().getDisplayName());
        }
        repository.askDoubt(doubt, error -> {
            posting.setValue(false);
            if (error != null) {
                message.setValue(error.getMessage() != null
                        ? error.getMessage() : "Could not post your doubt");
                return;
            }
            posted.setValue(true);
        });
    }
}
