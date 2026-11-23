package com.campussync.app.firebase;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
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
                    if (listener != null) listener.onComplete(user, null);
                });
    }

    public void createUserWithEmailPassword(String email, String password, String displayName,
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
                        createUserDocument(user, displayName, listener);
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

    private void createUserDocument(FirebaseUser user, String displayName, ResultListener<FirebaseUser> listener) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("email", user.getEmail());
        String name = displayName != null && !displayName.isEmpty()
                ? displayName
                : (user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
        profile.put("displayName", name);
        profile.put("createdAt", FieldValue.serverTimestamp());

        firestoreManager.createUserProfile(user.getUid(), profile, error -> {
            if (error != null) {
                Log.w(TAG, "createUserDocument:failure", error);
                if (listener != null) listener.onComplete(null, error);
                return;
            }
            currentUserLive.postValue(user);
            updateAuthState();
            registerFcmToken(null);
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
