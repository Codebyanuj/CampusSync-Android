package com.campussync.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small wrapper over the app's SharedPreferences.
 *
 * Only UI/session flags live here (onboarding seen, first-run). Anything that affects
 * authorisation lives in Firestore and is enforced by security rules.
 */
public final class SessionPrefs {

    private static final String PREFS = "campus_sync_prefs";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_LAST_EMAIL = "last_email";

    private SessionPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isOnboardingComplete(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public static void setOnboardingComplete(Context context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply();
    }

    public static String getLastEmail(Context context) {
        return prefs(context).getString(KEY_LAST_EMAIL, "");
    }

    public static void setLastEmail(Context context, String email) {
        prefs(context).edit().putString(KEY_LAST_EMAIL, email != null ? email : "").apply();
    }
}
