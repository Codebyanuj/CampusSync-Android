package com.campussync.app.ui.academics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.ResourceRepository;
import com.campussync.app.model.AcademicResource;
import com.campussync.app.model.ResourceRating;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Resource detail: live document, the viewer's rating and bookmark, and the feedback list. */
public class ResourceDetailViewModel extends ViewModel {

    private final ResourceRepository repository = ResourceRepository.getInstance();

    private final MutableLiveData<AcademicResource> resource = new MutableLiveData<>();
    private final MutableLiveData<Boolean> bookmarked = new MutableLiveData<>(false);
    private final MutableLiveData<ResourceRating> myRating = new MutableLiveData<>();
    private final MutableLiveData<List<ResourceRating>> ratings =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> deleted = new MutableLiveData<>(false);

    private String resourceId;
    private ListenerRegistration resourceRegistration;
    private ListenerRegistration bookmarkRegistration;
    private ListenerRegistration ratingRegistration;
    private ListenerRegistration ratingsRegistration;

    public LiveData<AcademicResource> getResource() {
        return resource;
    }

    public LiveData<Boolean> getBookmarked() {
        return bookmarked;
    }

    public LiveData<ResourceRating> getMyRating() {
        return myRating;
    }

    public LiveData<List<ResourceRating>> getRatings() {
        return ratings;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<Boolean> getDeleted() {
        return deleted;
    }

    public void clearMessage() {
        message.setValue(null);
    }

    public void start(String resourceId) {
        if (resourceId == null || resourceId.equals(this.resourceId)) return;
        stopListening();
        this.resourceId = resourceId;
        resourceRegistration = repository.listenToResource(resourceId, (value, error) -> {
            if (error != null) {
                message.setValue("Unable to load this resource.");
                return;
            }
            resource.setValue(value);
        });
        bookmarkRegistration = repository.listenToBookmark(resourceId, (saved, error) -> {
            if (error == null) bookmarked.setValue(saved);
        });
        ratingRegistration = repository.listenToMyRating(resourceId, (value, error) -> {
            if (error == null) myRating.setValue(value);
        });
        ratingsRegistration = repository.listenToRatings(resourceId, (list, error) -> {
            if (error != null) return;
            ratings.setValue(list);
        });
    }

    public void recordDownload() {
        AcademicResource current = resource.getValue();
        if (current == null) return;
        repository.recordDownload(current);
    }

    public void rate(long stars, String feedback) {
        AcademicResource current = resource.getValue();
        if (current == null) {
            message.setValue("This resource is still loading.");
            return;
        }
        String name = AuthManager.getInstance().getCurrentUser() != null
                ? AuthManager.getInstance().getCurrentUser().getDisplayName() : null;
        repository.rate(current, stars, feedback, name, error -> message.setValue(
                error == null ? "Thanks for the feedback" : errorMessage(error)));
    }

    public void toggleBookmark() {
        AcademicResource current = resource.getValue();
        if (current == null) return;
        boolean saved = Boolean.TRUE.equals(bookmarked.getValue());
        repository.toggleBookmark(current, !saved, error -> {
            if (error == null) {
                bookmarked.setValue(!saved);
                message.setValue(!saved ? "Saved to your bookmarks" : "Removed from bookmarks");
            } else {
                message.setValue(errorMessage(error));
            }
        });
    }

    public void deleteResource() {
        AcademicResource current = resource.getValue();
        if (current == null) return;
        repository.deleteResource(current, error -> {
            if (error != null) {
                message.setValue(errorMessage(error));
                return;
            }
            deleted.setValue(true);
        });
    }

    private static String errorMessage(Exception error) {
        return error.getMessage() != null ? error.getMessage() : "Something went wrong. Try again.";
    }

    private void stopListening() {
        if (resourceRegistration != null) resourceRegistration.remove();
        if (bookmarkRegistration != null) bookmarkRegistration.remove();
        if (ratingRegistration != null) ratingRegistration.remove();
        if (ratingsRegistration != null) ratingsRegistration.remove();
        resourceRegistration = null;
        bookmarkRegistration = null;
        ratingRegistration = null;
        ratingsRegistration = null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }
}
