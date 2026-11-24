package com.campussync.app.ui.social;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.SocialRepository;

import java.util.Collections;
import java.util.List;

/** Publishing a new post (with an optional image) or editing the text of an existing one. */
public class CreatePostViewModel extends ViewModel {

    private final SocialRepository repository = SocialRepository.getInstance();

    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> done = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<Boolean> getBusy() {
        return busy;
    }

    public LiveData<Boolean> getDone() {
        return done;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void save(String postId, String content, boolean anonymous, Uri image) {
        if (Boolean.TRUE.equals(busy.getValue())) return;
        busy.setValue(true);
        error.setValue(null);
        if (postId != null) {
            repository.editPost(postId, content, exception -> finish(exception));
            return;
        }
        List<Uri> images = image != null
                ? Collections.singletonList(image) : Collections.<Uri>emptyList();
        repository.createPost(content, anonymous, null, images, (id, exception) ->
                finish(exception));
    }

    private void finish(Exception exception) {
        busy.setValue(false);
        if (exception != null) {
            String message = exception.getMessage();
            error.setValue(message != null && !message.isEmpty()
                    ? message : "Could not publish your post. Please try again.");
            return;
        }
        done.setValue(true);
    }

    public void clearError() {
        error.setValue(null);
    }
}
