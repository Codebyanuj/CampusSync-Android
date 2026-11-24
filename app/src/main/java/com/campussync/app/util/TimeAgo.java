package com.campussync.app.util;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Small relative-time formatter shared by the social screens. */
public final class TimeAgo {

    private TimeAgo() {
    }

    public static String format(Date date) {
        if (date == null) return "";
        long millis = System.currentTimeMillis() - date.getTime();
        if (millis < 0) millis = 0;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours < 24) return hours + "h ago";
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        if (days < 7) return days + "d ago";
        return new java.text.SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
    }
}
