package com.campussync.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local notification preferences.
 *
 * <p>The same toggles are mirrored to the user document ({@code notifyMessages},
 * {@code notifyGroups}) so the Cloud Functions backend can skip a push for a member who turned the
 * category off. The local copy is what the device consults before showing a notification, so the
 * setting works even before the server deploy.
 */
public final class NotificationPrefs {

    private static final String PREFS = "campus_sync_prefs";

    public static final String KEY_PUSH = "notifications_enabled";
    public static final String KEY_MESSAGES = "notify_messages";
    public static final String KEY_GROUPS = "notify_groups";

    private NotificationPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean pushEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PUSH, true);
    }

    public static boolean messagesEnabled(Context context) {
        return pushEnabled(context) && prefs(context).getBoolean(KEY_MESSAGES, true);
    }

    public static boolean groupsEnabled(Context context) {
        return pushEnabled(context) && prefs(context).getBoolean(KEY_GROUPS, true);
    }

    public static void setPushEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PUSH, enabled).apply();
    }

    public static void setMessagesEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MESSAGES, enabled).apply();
    }

    public static void setGroupsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_GROUPS, enabled).apply();
    }
}
