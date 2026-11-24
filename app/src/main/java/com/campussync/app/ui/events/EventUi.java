package com.campussync.app.ui.events;

import android.content.Context;

import com.campussync.app.R;
import com.campussync.app.model.Event;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small formatting helpers shared by the event screens. */
public final class EventUi {

    public static final String[] CATEGORY_KEYS = {
            Event.CATEGORY_ACADEMIC,
            Event.CATEGORY_CULTURAL,
            Event.CATEGORY_SPORTS,
            Event.CATEGORY_WORKSHOP,
            Event.CATEGORY_TECH,
            Event.CATEGORY_SOCIAL,
            Event.CATEGORY_OTHER
    };

    private EventUi() {
    }

    public static String categoryLabel(Context context, String key) {
        if (context == null || key == null) return "";
        switch (key) {
            case Event.CATEGORY_ACADEMIC:
                return context.getString(R.string.category_academic);
            case Event.CATEGORY_CULTURAL:
                return context.getString(R.string.category_cultural);
            case Event.CATEGORY_SPORTS:
                return context.getString(R.string.category_sports);
            case Event.CATEGORY_WORKSHOP:
                return context.getString(R.string.category_workshop);
            case Event.CATEGORY_TECH:
                return context.getString(R.string.category_tech);
            case Event.CATEGORY_SOCIAL:
                return context.getString(R.string.category_social);
            default:
                return context.getString(R.string.category_other);
        }
    }

    public static String[] categoryLabels(Context context) {
        String[] labels = new String[CATEGORY_KEYS.length];
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            labels[i] = categoryLabel(context, CATEGORY_KEYS[i]);
        }
        return labels;
    }

    /** "Nov 23, 2026 · 2:00 PM – 5:00 PM" (end omitted when unknown). */
    public static String formatRange(Date start, Date end) {
        if (start == null) return "";
        SimpleDateFormat date = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        SimpleDateFormat time = new SimpleDateFormat("h:mm a", Locale.getDefault());
        StringBuilder builder = new StringBuilder(date.format(start)).append(" · ")
                .append(time.format(start));
        if (end != null) builder.append(" – ").append(time.format(end));
        return builder.toString();
    }

    public static String formatDateTime(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date);
    }
}
