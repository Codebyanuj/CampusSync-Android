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
 * ViewModel for login, registration, password reset and the email-verification gate.
 * Holds only UI state; all Firebase work is delegated to {@link AuthManager}.
 */
public class AuthViewModel extends ViewModel {

    private final AuthManager authManager;
    private final MediatorLiveData<AuthState> authState = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> status = new MutableLiveData<>();
    private final MutableLiveData<Boolean> resetEmailSent = new MutableLiveData<>(false);

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

    public LiveData<String> getStatus() {
        return status;
    }

    public LiveData<Boolean> getResetEmailSent() {
        return resetEmailSent;
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
        authManager.signInWithEmailPassword(email.trim(), password, (result, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue(mapError(exception));
            }
        });
    }

    /** Registers a student or faculty account and creates its full profile document. */
    public void register(RegistrationInput input) {
        if (authManager == null) {
            error.setValue("Firebase is not configured. Add app/google-services.json and rebuild.");
            return;
        }
        loading.setValue(true);
        error.setValue(null);
        authManager.createUserWithEmailPassword(input.email.trim(), input.password,
                input.fullName.trim(), input.toProfileMap(), (result, exception) -> {
                    loading.setValue(false);
                    if (exception != null) {
                        error.setValue(mapError(exception));
                    }
                });
    }

    public void sendPasswordReset(String email) {
        if (authManager == null) {
            error.setValue("Firebase is not configured. Add app/google-services.json and rebuild.");
            return;
        }
        loading.setValue(true);
        error.setValue(null);
        resetEmailSent.setValue(false);
        authManager.sendPasswordResetEmail(email.trim(), exception -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue(mapError(exception));
                return;
            }
            resetEmailSent.setValue(true);
            status.setValue("Password reset link sent to " + email.trim());
        });
    }

    public void resendVerificationEmail() {
        if (authManager == null) return;
        loading.setValue(true);
        authManager.sendVerificationEmail(exception -> {
            loading.setValue(false);
            status.setValue(exception == null
                    ? "Verification email sent. Check your inbox and spam folder."
                    : "Could not send the email. Try again in a minute.");
        });
    }

    public void refreshVerificationStatus() {
        if (authManager == null) return;
        loading.setValue(true);
        authManager.reloadUser((user, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue(mapError(exception));
                return;
            }
            if (user != null && user.isEmailVerified()) {
                status.setValue("Email verified. Welcome to CampusSync!");
            } else {
                status.setValue("Not verified yet. Open the link in your email, then refresh.");
            }
        });
    }

    public void signOut() {
        if (authManager != null) authManager.signOut();
    }

    public void clearError() {
        error.setValue(null);
    }

    public void clearStatus() {
        status.setValue(null);
    }

    public void clearResetFlag() {
        resetEmailSent.setValue(false);
    }

    /**
     * Converts Firebase auth exceptions into messages that are safe and useful for users.
     */
    String mapError(Exception exception) {
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
        if (exception instanceof java.net.UnknownHostException) {
            return "Network error. Check your connection and try again.";
        }
        String message = exception.getMessage();
        return message != null ? message : "Authentication failed. Please try again.";
    }
}
