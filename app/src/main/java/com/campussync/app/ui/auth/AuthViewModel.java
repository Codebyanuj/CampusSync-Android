package com.campussync.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.AuthManager.AuthState;
import com.campussync.app.firebase.AuthManager.ResultListener;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;

/**
 * ViewModel for the login / registration screen.
 * Holds only UI state; all Firebase work is delegated to {@link AuthManager}.
 */
public class AuthViewModel extends ViewModel {

    private final AuthManager authManager;
    private final MediatorLiveData<AuthState> authState = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public AuthViewModel() {
        AuthManager manager = null;
        String configurationError = null;
        try {
            manager = AuthManager.getInstance();
        } catch (IllegalStateException e) {
            // Thrown by FirebaseAuth when no google-services.json was supplied at build time.
            configurationError = "Firebase is not configured. Add app/google-services.json from your "
                    + "Firebase project, rebuild, then sign in.";
        }
        authManager = manager;
        if (authManager != null) {
            authState.setValue(authManager.getAuthStateLive().getValue());
            authState.addSource(authManager.getAuthStateLive(), authState::setValue);
        } else {
            authState.setValue(AuthState.UNAUTHENTICATED);
            error.setValue(configurationError);
        }
    }

    public boolean isConfigured() {
        return authManager != null;
    }

    public LiveData<AuthState> getAuthState() {
        return authState;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<FirebaseUser> getCurrentUser() {
        return authManager != null ? authManager.getCurrentUserLive() : new MutableLiveData<>();
    }

    public void signIn(String email, String password) {
        if (authManager == null) {
            error.setValue("Firebase is not configured. Add app/google-services.json and rebuild.");
            return;
        }
        loading.setValue(true);
        error.setValue(null);
        authManager.signInWithEmailPassword(email, password, new ResultListener<FirebaseUser>() {
            @Override
            public void onComplete(FirebaseUser result, Exception exception) {
                loading.setValue(false);
                if (exception != null) {
                    error.setValue(mapError(exception));
                }
            }
        });
    }

    public void register(String email, String password, String displayName) {
        if (authManager == null) {
            error.setValue("Firebase is not configured. Add app/google-services.json and rebuild.");
            return;
        }
        loading.setValue(true);
        error.setValue(null);
        authManager.createUserWithEmailPassword(email, password, displayName,
                new ResultListener<FirebaseUser>() {
                    @Override
                    public void onComplete(FirebaseUser result, Exception exception) {
                        loading.setValue(false);
                        if (exception != null) {
                            error.setValue(mapError(exception));
                        }
                    }
                });
    }

    public void clearError() {
        error.setValue(null);
    }

    /**
     * Converts Firebase auth exceptions into messages that are safe and useful for users.
     */
    private String mapError(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "No account found for that email.";
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        }
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return "An account already exists for that email.";
        }
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            return "Password is too weak. Use at least 6 characters.";
        }
        String message = exception.getMessage();
        return message != null ? message : "Authentication failed. Please try again.";
    }
}
