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
import com.campussync.app.util.NotificationPrefs;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Handles Firebase Cloud Messaging for CampusSync.
 *
 * <p>Message and group-invitation pushes respect the local notification preferences, so turning a
 * category off in Settings silences it on this device even though the backend still sends it.
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
        String category = message.getData() != null ? message.getData().get("category") : null;

        if (category != null) {
            if ("message".equals(category)) {
                if (!NotificationPrefs.messagesEnabled(this)) return;
                channelId = CampusSyncApp.CHANNEL_MESSAGES;
                if (title == null) title = getString(R.string.title_messages);
            } else if ("groupInvite".equals(category)) {
                if (!NotificationPrefs.groupsEnabled(this)) return;
                channelId = CampusSyncApp.CHANNEL_MESSAGES;
                if (title == null) title = getString(R.string.btn_new_group);
            } else if ("event".equals(category)) {
                channelId = CampusSyncApp.CHANNEL_EVENTS;
                if (title == null) title = getString(R.string.title_events);
            } else if ("group".equals(category)) {
                channelId = CampusSyncApp.CHANNEL_GROUPS;
                if (title == null) title = getString(R.string.nav_groups);
            }
        }

        if (!NotificationPrefs.pushEnabled(this)) return;

        showNotification(title, body, channelId, message.getData());
    }

    private void showNotification(String title, String body, String channelId,
                                  java.util.Map<String, String> data) {
        if (title == null) title = getString(R.string.app_name);
        if (body == null) body = "";

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (data != null && data.containsKey("screen")) {
            intent.putExtra("screen", data.get("screen"));
        }
        if (data != null && data.containsKey("conversationId")) {
            intent.putExtra("conversationId", data.get("conversationId"));
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(CampusSyncApp.CHANNEL_MESSAGES.equals(channelId)
                        ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis() % 100000, builder.build());
    }
}
