package com.campussync.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

/**
 * CampusSync application class.
 * Created: initial project scaffolding.
 */
public class CampusSyncApp extends Application {

    public static final String CHANNEL_EVENTS = "campus_sync_events";
    public static final String CHANNEL_GROUPS = "campus_sync_groups";
    public static final String CHANNEL_GENERAL = "campus_sync_general";
    public static final String CHANNEL_MESSAGES = "campus_sync_messages";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            CharSequence name = getString(R.string.nav_events);
            String description = "Events and event updates";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_EVENTS, name, importance);
            channel.setDescription(description);
            manager.createNotificationChannel(channel);

            name = getString(R.string.nav_groups);
            description = "Groups and group activity";
            channel = new NotificationChannel(CHANNEL_GROUPS, name, importance);
            channel.setDescription(description);
            manager.createNotificationChannel(channel);

            name = getString(R.string.title_settings);
            description = "General notifications";
            channel = new NotificationChannel(CHANNEL_GENERAL, name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            manager.createNotificationChannel(channel);

            name = getString(R.string.notification_channel_messages);
            description = "Direct messages and group chats";
            channel = new NotificationChannel(CHANNEL_MESSAGES, name,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(description);
            manager.createNotificationChannel(channel);
        }
    }
}
