package com.campussync.app.util;

import java.util.Date;

/**
 * Human readable countdown labels for the event radar and detail screen.
 *
 * <p>The labels are recomputed from the current clock on every bind/tick, so a screen that ticks
 * once a minute stays accurate without storing anything.
 */
public final class Countdown {

    private static final long MINUTE = 60L * 1000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    private Countdown() {
    }

    /** "Starts in 2d 3h", "Starting now", "Ended", … */
    public static String label(Date start, Date end) {
        long now = System.currentTimeMillis();
        if (start == null) return "";
        long startMillis = start.getTime();
        long endMillis = end != null ? end.getTime() : startMillis + 2 * HOUR;

        if (now >= startMillis && now <= endMillis) return "Happening now";
        if (now > endMillis) return "Ended";

        long diff = startMillis - now;
        if (diff < MINUTE) return "Starts in less than a minute";
        if (diff < HOUR) return "Starts in " + (diff / MINUTE) + "m";
        if (diff < DAY) {
            long hours = diff / HOUR;
            long minutes = (diff % HOUR) / MINUTE;
            return "Starts in " + hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
        long days = diff / DAY;
        long hours = (diff % DAY) / HOUR;
        return "Starts in " + days + "d" + (hours > 0 ? " " + hours + "h" : "");
    }

    /** One step ahead of {@link #label(Date, Date)} used for the next tick. */
    public static boolean shouldTick(Date start) {
        if (start == null) return false;
        long diff = start.getTime() - System.currentTimeMillis();
        return diff > 0 && diff < DAY;
    }
}
