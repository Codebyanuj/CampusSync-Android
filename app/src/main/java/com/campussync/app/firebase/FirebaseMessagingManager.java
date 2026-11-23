package com.campussync.app.firebase;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Thin helper around Firebase Cloud Messaging token retrieval.
 */
public final class FirebaseMessagingManager {

    private static final String TAG = "FCMManager";

    private FirebaseMessagingManager() {
        // No instances.
    }

    public interface TokenListener {
        void onToken(String token, Exception error);
    }

    /**
     * Asynchronously fetches the current FCM registration token.
     */
    public static void getToken(TokenListener listener) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Log.w(TAG, "getToken failed", task.getException());
                if (listener != null) listener.onToken(null, task.getException());
                return;
            }
            if (listener != null) listener.onToken(task.getResult(), null);
        });
    }

    /**
     * Fetches the token and registers it on the signed-in user's Firestore document.
     */
    public static void refreshAndRegister(AuthManager.ActionListener listener) {
        AuthManager.getInstance().registerFcmToken(listener);
    }
}
