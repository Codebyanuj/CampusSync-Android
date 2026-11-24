package com.campussync.app.firebase;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralised Firebase Authentication manager (singleton).
 *
 * Responsibilities:
 *  - exposes the current user / auth state as LiveData for MVVM consumers,
 *  - performs sign-in, registration and sign-out,
 *  - creates the Firestore profile document on registration,
 *  - keeps the device FCM token stored on the user document.
 */
public class AuthManager {

    private static final String TAG = "AuthManager";
    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_FCM_TOKENS = "fcmTokens";

    private static volatile AuthManager instance;

    private final FirebaseAuth firebaseAuth;
    private final FirestoreManager firestoreManager;
    private final MutableLiveData<FirebaseUser> currentUserLive = new MutableLiveData<>();
    private final MutableLiveData<AuthState> authStateLive = new MutableLiveData<>(AuthState.LOADING);

    public enum AuthState {
        LOADING,
        AUTHENTICATED,
        UNAUTHENTICATED,
        ERROR
    }

    private AuthManager() {
        firebaseAuth = FirebaseAuth.getInstance();
        firestoreManager = FirestoreManager.getInstance();
        currentUserLive.setValue(firebaseAuth.getCurrentUser());
        updateAuthState();
        firebaseAuth.addAuthStateListener(auth -> {
            FirebaseUser user = auth.getCurrentUser();
            currentUserLive.postValue(user);
            updateAuthState();
        });
    }

    public static AuthManager getInstance() {
        if (instance == null) {
            synchronized (AuthManager.class) {
                if (instance == null) {
                    instance = new AuthManager();
                }
            }
        }
        return instance;
    }

    public FirebaseAuth getFirebaseAuth() {
        return firebaseAuth;
    }

    public LiveData<FirebaseUser> getCurrentUserLive() {
        return currentUserLive;
    }

    public LiveData<AuthState> getAuthStateLive() {
        return authStateLive;
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public boolean isSignedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    public String getCurrentUserId() {
        FirebaseUser user = getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ---------------- Sign in / register / sign out ----------------

    public void signInWithEmailPassword(String email, String password, ResultListener<FirebaseUser> listener) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "signInWithEmailPassword:failure", task.getException());
                        if (listener != null) listener.onComplete(null, task.getException());
                        return;
                    }
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    Log.d(TAG, "signInWithEmailPassword:success");
                    currentUserLive.postValue(user);
                    updateAuthState();
                    registerFcmToken(null);
                    // Keep the profile's verification flag in sync for accounts verified elsewhere.
                    if (user != null && user.isEmailVerified()) {
                        Map<String, Object> fields = new HashMap<>();
                        fields.put("emailVerified", true);
                        firestoreManager.updateUserFields(fields, null);
                    }
                    if (listener != null) listener.onComplete(user, null);
                });
    }

    public void createUserWithEmailPassword(String email, String password, String displayName,
                                            ResultListener<FirebaseUser> listener) {
        createUserWithEmailPassword(email, password, displayName, null, listener);
    }

    /**
     * Registration used by the student and faculty sign-up forms.
     *
     * {@code profileFields} carries the academic profile (role, college, department, ...). Role is
     * stored for display only: Firestore rules reject any later change to it by the owner.
     */
    public void createUserWithEmailPassword(String email, String password, String displayName,
                                            Map<String, Object> profileFields,
                                            ResultListener<FirebaseUser> listener) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "createUserWithEmailPassword:failure", task.getException());
                        if (listener != null) listener.onComplete(null, task.getException());
                        return;
                    }
                    FirebaseUser user = task.getResult() != null ? task.getResult().getUser() : null;
                    if (user == null) {
                        if (listener != null) listener.onComplete(null, new IllegalStateException("No user returned"));
                        return;
                    }
                    UserProfileChangeRequest profileChange = new UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .build();
                    user.updateProfile(profileChange).addOnCompleteListener(profileTask -> {
                        // A failed display-name update is non-fatal: the profile document is still created.
                        if (!profileTask.isSuccessful()) {
                            Log.w(TAG, "updateProfile:failure", profileTask.getException());
                        }
                        createUserDocument(user, displayName, profileFields, listener);
                    });
                });
    }

    // ---------------- Email verification / password reset ----------------

    /** Sends the account-verification email to the signed-in user (no-op when already verified). */
    public void sendVerificationEmail(ActionListener listener) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            if (listener != null) listener.onComplete(new IllegalStateException("Not signed in"));
            return;
        }
        if (user.isEmailVerified()) {
            if (listener != null) listener.onComplete(null);
            return;
        }
        user.sendEmailVerification().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "sendVerificationEmail:failure", task.getException());
                if (listener != null) listener.onComplete(task.getException());
                return;
            }
            Log.d(TAG, "sendVerificationEmail:success");
            if (listener != null) listener.onComplete(null);
        });
    }

    public void sendPasswordResetEmail(String email, ActionListener listener) {
        firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "sendPasswordResetEmail:failure", task.getException());
                if (listener != null) listener.onComplete(task.getException());
                return;
            }
            Log.d(TAG, "sendPasswordResetEmail:success");
            if (listener != null) listener.onComplete(null);
        });
    }

    /** Reloads the cached user so a freshly clicked verification link is picked up. */
    public void reloadUser(ResultListener<FirebaseUser> listener) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            if (listener != null) listener.onComplete(null, new IllegalStateException("Not signed in"));
            return;
        }
        user.reload().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "reloadUser:failure", task.getException());
                if (listener != null) listener.onComplete(null, task.getException());
                return;
            }
            FirebaseUser refreshed = firebaseAuth.getCurrentUser();
            currentUserLive.postValue(refreshed);
            updateAuthState();
            if (refreshed != null && refreshed.isEmailVerified()) {
                Map<String, Object> fields = new HashMap<>();
                fields.put("emailVerified", true);
                FirestoreManager.getInstance().updateUserFields(fields, null);
            }
            if (listener != null) listener.onComplete(refreshed, null);
        });
    }

    public boolean isEmailVerified() {
        FirebaseUser user = getCurrentUser();
        return user != null && user.isEmailVerified();
    }

    /**
     * Changes the password of the signed-in email/password account.
     *
     * Firebase requires a recent sign-in for sensitive changes, so the user is re-authenticated
     * with the current password first; a wrong current password fails there.
     */
    public void changePassword(String currentPassword, String newPassword, ActionListener listener) {
        FirebaseUser user = getCurrentUser();
        if (user == null || user.getEmail() == null) {
            if (listener != null) {
                listener.onComplete(new IllegalStateException("You need to be signed in."));
            }
            return;
        }
        if (currentPassword == null || currentPassword.isEmpty()) {
            if (listener != null) {
                listener.onComplete(new IllegalArgumentException("Enter your current password."));
            }
            return;
        }
        AuthCredential credential =
                EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential).addOnCompleteListener(reAuthTask -> {
            if (!reAuthTask.isSuccessful()) {
                Log.w(TAG, "changePassword:reauth:failure", reAuthTask.getException());
                if (listener != null) {
                    Exception error = reAuthTask.getException();
                    if (error instanceof FirebaseAuthInvalidCredentialsException) {
                        error = new IllegalArgumentException("Your current password is incorrect.");
                    }
                    listener.onComplete(error);
                }
                return;
            }
            user.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                if (!updateTask.isSuccessful()) {
                    Log.w(TAG, "changePassword:update:failure", updateTask.getException());
                    if (listener != null) listener.onComplete(updateTask.getException());
                    return;
                }
                Log.d(TAG, "changePassword:success");
                if (listener != null) listener.onComplete(null);
            });
        });
    }

    public void signOut() {
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            // Best-effort removal of this device's token before the session ends.
            removeFcmToken(user.getUid());
        }
        firebaseAuth.signOut();
        currentUserLive.postValue(null);
        updateAuthState();
    }

    /**
     * Fetches a fresh ID token, used to authorise calls to trusted Cloud Functions.
     */
    public void getIdToken(ResultListener<String> listener) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            if (listener != null) listener.onComplete(null, new IllegalStateException("Not signed in"));
            return;
        }
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "getIdToken:failure", task.getException());
                if (listener != null) listener.onComplete(null, task.getException());
                return;
            }
            GetTokenResult result = task.getResult();
            if (listener != null) listener.onComplete(result != null ? result.getToken() : null, null);
        });
    }

    // ---------------- FCM token registration ----------------

    /**
     * Stores the current FCM registration token on the signed-in user's document so the
     * Cloud Functions backend can target this device.
     */
    public void registerFcmToken(ActionListener listener) {
        final String uid = getCurrentUserId();
        if (uid == null) {
            if (listener != null) listener.onComplete(null);
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Log.w(TAG, "registerFcmToken: token fetch failed", task.getException());
                if (listener != null) listener.onComplete(task.getException());
                return;
            }
            String token = task.getResult();
            Map<String, Object> updates = new HashMap<>();
            updates.put(FIELD_FCM_TOKENS, FieldValue.arrayUnion(token));
            FirestoreManager.getInstance().updateUserFields(updates, listener);
        });
    }

    private void removeFcmToken(String uid) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;
            Map<String, Object> updates = new HashMap<>();
            updates.put(FIELD_FCM_TOKENS, FieldValue.arrayRemove(task.getResult()));
            FirestoreManager.getInstance().updateUserFields(updates, null);
        });
    }

    // ---------------- Internal ----------------

    private void createUserDocument(FirebaseUser user, String displayName,
                                    Map<String, Object> profileFields,
                                    ResultListener<FirebaseUser> listener) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("email", user.getEmail());
        String name = displayName != null && !displayName.isEmpty()
                ? displayName
                : (user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
        profile.put("displayName", name);
        profile.put("createdAt", FieldValue.serverTimestamp());
        profile.put("role", com.campussync.app.model.UserProfile.ROLE_STUDENT);
        profile.put("accountStatus", com.campussync.app.model.UserProfile.STATUS_ACTIVE);
        profile.put("verificationStatus",
                com.campussync.app.model.UserProfile.VERIFICATION_UNVERIFIED);
        profile.put("emailVerified", false);
        if (profileFields != null) {
            profile.putAll(profileFields);
        }

        firestoreManager.createUserProfile(user.getUid(), profile, error -> {
            if (error != null) {
                Log.w(TAG, "createUserDocument:failure", error);
                if (listener != null) listener.onComplete(null, error);
                return;
            }
            currentUserLive.postValue(user);
            updateAuthState();
            registerFcmToken(null);
            // Verification mail is best effort: the user can resend it from the verify screen.
            sendVerificationEmail(null);
            if (listener != null) listener.onComplete(user, null);
        });
    }

    private void updateAuthState() {
        authStateLive.postValue(firebaseAuth.getCurrentUser() != null
                ? AuthState.AUTHENTICATED
                : AuthState.UNAUTHENTICATED);
    }

    // ---------------- Callback interfaces ----------------

    /** Completion callback for operations without a typed result. */
    public interface ActionListener {
        void onComplete(Exception error);
    }

    /** Completion callback for operations that produce a value. */
    public interface ResultListener<T> {
        void onComplete(T result, Exception error);
    }
}
