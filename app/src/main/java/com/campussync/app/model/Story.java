package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * A 24-hour story stored in {@code stories/{storyId}}.
 *
 * Expiry is derived from the trusted server timestamp in {@code createdAt}: the app only queries
 * stories created within the last 24 hours and opportunistically deletes expired ones. A scheduled
 * Cloud Function can prune the remainder (see the README).
 */
public class Story {

    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";

    public static final long TTL_MS = 24L * 60L * 60L * 1000L;

    @Exclude
    private String id;

    private String authorId;
    private String authorName;
    private String authorPhotoUrl;
    private String mediaUrl;
    private String mediaType = TYPE_IMAGE;
    private Date createdAt;
    private long viewCount;

    @Exclude
    private boolean ownedByMe;

    public Story() {
        // Public no-arg constructor required for Firestore.
    }

    @Exclude
    public String getId() {
        return id;
    }

    @Exclude
    public void setId(String id) {
        this.id = id;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorPhotoUrl() {
        return authorPhotoUrl;
    }

    public void setAuthorPhotoUrl(String authorPhotoUrl) {
        this.authorPhotoUrl = authorPhotoUrl;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    @Exclude
    public boolean isOwnedByMe() {
        return ownedByMe;
    }

    @Exclude
    public void setOwnedByMe(boolean ownedByMe) {
        this.ownedByMe = ownedByMe;
    }

    @Exclude
    public boolean isVideo() {
        return TYPE_VIDEO.equals(mediaType);
    }

    /** Expiry check against trusted creation time; expired stories are never shown. */
    @Exclude
    public boolean isExpired() {
        return createdAt == null || System.currentTimeMillis() - createdAt.getTime() > TTL_MS;
    }
}
