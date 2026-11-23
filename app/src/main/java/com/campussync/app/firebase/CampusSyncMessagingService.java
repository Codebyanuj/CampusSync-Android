package com.campussync.app.firebase;

import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.campussync.app.CampusSyncApp;
import com.campussync.app.R;
import com.campussync.app.ui.MainActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Handles Firebase Cloud Messaging for CampusSync.
 */
public class CampusSyncMessagingService extends FirebaseMessagingService {

    private static final String TAG = "CampusSyncMessaging";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.i(TAG, "Refreshed token: " + token);
        // Token refresh is persisted by the AuthManager when the user is signed in.
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        Log.i(TAG, "From: " + message.getFrom());

        // Handle data messages or notification messages that arrive while the app is in foreground.
        String title = message.getNotification() != null ? message.getNotification().getTitle() : null;
        String body = message.getNotification() != null ? message.getNotification().getBody() : null;
        String channelId = CampusSyncApp.CHANNEL_GENERAL;

        if (message.getData() != null && !message.getData().isEmpty()) {
            // Trusted data messages from Cloud Functions carry a category.
            if ("event".equals(message.getData().get("category"))) {
                channelId = CampusSyncApp.CHANNEL_EVENTS;
                if (title == null) title = getString(R.string.title_events);
            } else if ("group".equals(message.getData().get("category"))) {
                channelId = CampusSyncApp.CHANNEL_GROUPS;
                if (title == null) title = getString(R.string.nav_groups);
            }
        }

        showNotification(title, body, channelId, message.getData());
    }

    private void showNotification(String title, String body, String channelId, java.util.Map<String, String> data) {
        if (title == null) title = getString(R.string.app_name);
        if (body == null) body = "";

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (data != null && data.containsKey("screen")) {
            intent.putExtra("screen", data.get("screen"));
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis() % 100000, builder.build());
    }
}
