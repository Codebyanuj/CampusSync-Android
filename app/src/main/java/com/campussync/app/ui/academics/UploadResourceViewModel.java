package com.campussync.app.ui.academics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.ResourceRepository;
import com.campussync.app.model.AcademicResource;

/** Drives the resource upload: progress, completion and the failure message. */
public class UploadResourceViewModel extends ViewModel {

    private final ResourceRepository repository = ResourceRepository.getInstance();

    private final MutableLiveData<Integer> progress = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> uploading = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> uploaded = new MutableLiveData<>(false);

    public LiveData<Integer> getProgress() {
        return progress;
    }

    public LiveData<Boolean> getUploading() {
        return uploading;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<Boolean> getUploaded() {
        return uploaded;
    }

    public void clearMessage() {
        message.setValue(null);
    }

    public void publish(AcademicResource resource, android.net.Uri file) {
        if (Boolean.TRUE.equals(uploading.getValue())) return;
        uploading.setValue(true);
        progress.setValue(0);
        repository.uploadResource(resource, file, new ResourceRepository.UploadListener() {
            @Override
            public void onProgress(int percent) {
                progress.setValue(percent);
            }

            @Override
            public void onComplete(String resourceId, Exception error) {
                uploading.setValue(false);
                if (error != null) {
                    message.setValue(error.getMessage() != null
                            ? error.getMessage() : "Upload failed. Try again.");
                    return;
                }
                uploaded.setValue(true);
            }
        });
    }
}
