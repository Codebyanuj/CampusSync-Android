package com.campussync.app.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parsing/formatting for the free-text skill and interest lists.
 *
 * Users type comma-separated values; the profile stores trimmed, de-duplicated lists.
 */
public final class Tags {

    private static final int MAX_TAGS = 15;
    private static final int MAX_TAG_LENGTH = 32;

    private Tags() {
    }

    public static List<String> parse(String raw) {
        Set<String> unique = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String tag = part.trim();
                if (tag.isEmpty()) continue;
                if (tag.length() > MAX_TAG_LENGTH) tag = tag.substring(0, MAX_TAG_LENGTH).trim();
                unique.add(tag);
                if (unique.size() >= MAX_TAGS) break;
            }
        }
        return new ArrayList<>(unique);
    }

    public static String join(List<String> tags) {
        return tags != null ? android.text.TextUtils.join(", ", tags) : "";
    }
}
