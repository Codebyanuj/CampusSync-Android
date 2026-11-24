package com.campussync.app.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;

/**
 * Account-level settings actions: password change and sign-out.
 *
 * Firebase work stays in {@link AuthManager}; this ViewModel only exposes UI state.
 */
public class SettingsViewModel extends ViewModel {

    private final AuthManager authManager;
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public SettingsViewModel() {
        AuthManager manager = null;
        String configurationError = null;
        try {
            manager = AuthManager.getInstance();
        } catch (IllegalStateException e) {
            configurationError = "Firebase is not configured. Add app/google-services.json and rebuild.";
        }
        authManager = manager;
        if (authManager == null) error.setValue(configurationError);
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void changePassword(String currentPassword, String newPassword) {
        if (authManager == null) {
            error.setValue("Firebase is not configured. Add app/google-services.json and rebuild.");
            return;
        }
        saving.setValue(true);
        authManager.changePassword(currentPassword, newPassword, exception -> {
            saving.setValue(false);
            if (exception != null) {
                error.setValue(exception.getMessage() != null
                        ? exception.getMessage()
                        : "Could not change your password. Try again.");
                return;
            }
            message.setValue("Password updated.");
        });
    }

    public void signOut() {
        if (authManager != null) authManager.signOut();
    }

    public void clearMessage() {
        message.setValue(null);
    }

    public void clearError() {
        error.setValue(null);
    }
}
