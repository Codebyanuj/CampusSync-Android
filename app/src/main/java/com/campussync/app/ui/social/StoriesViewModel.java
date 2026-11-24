package com.campussync.app.ui.social;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.R;
import com.campussync.app.firebase.StoryRepository;
import com.campussync.app.model.Story;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Active (24 hour) stories plus upload, view tracking, replies and deletion. */
public class StoriesViewModel extends ViewModel {

    private final StoryRepository repository = StoryRepository.getInstance();

    private final MutableLiveData<List<Story>> stories = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> uploading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();

    private ListenerRegistration registration;

    public LiveData<List<Story>> getStories() {
        return stories;
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

    public void start() {
        if (registration != null) return;
        registration = repository.listenToActiveStories((list, exception) -> {
            if (exception != null) {
                error.setValue("Unable to load stories.");
                return;
            }
            stories.setValue(list != null ? list : new ArrayList<Story>());
        });
    }

    public void upload(Uri uri, String mediaType) {
        if (uri == null) return;
        uploading.setValue(true);
        repository.uploadStory(uri, mediaType, (storyId, exception) -> {
            uploading.setValue(false);
            if (exception != null) {
                error.setValue(exception.getMessage() != null
                        ? exception.getMessage() : "Story upload failed.");
            } else {
                messageRes.setValue(R.string.story_uploaded);
            }
        });
    }

    public void markViewed(String storyId) {
        if (storyId != null) repository.trackView(storyId);
    }

    public void reply(String storyId, String content, Runnable onSent) {
        repository.replyToStory(storyId, content, exception -> {
            if (exception != null) {
                error.setValue("Could not send your reply.");
            } else {
                messageRes.setValue(R.string.story_reply_sent);
                if (onSent != null) onSent.run();
            }
        });
    }

    public void deleteStory(String storyId, Runnable onDeleted) {
        repository.deleteStory(storyId, exception -> {
            if (exception != null) {
                error.setValue("Could not delete the story.");
            } else {
                messageRes.setValue(R.string.story_deleted);
                if (onDeleted != null) onDeleted.run();
            }
        });
    }

    public void clearMessage() {
        messageRes.setValue(null);
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
