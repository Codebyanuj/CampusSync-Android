package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * One student's rating and feedback for a resource
 * ({@code resources/{resourceId}/ratings/{uid}}).
 *
 * <p>The document id is the rater's uid, so a rating can be created once and edited afterwards
 * but never duplicated.
 */
public class ResourceRating {

    @Exclude
    private String uid;

    private long stars;
    private String feedback;
    private String raterName;
    private Date createdAt;
    private Date updatedAt;

    public ResourceRating() {
        // Public no-arg constructor required for Firestore.
    }

    @Exclude
    public String getUid() {
        return uid;
    }

    @Exclude
    public void setUid(String uid) {
        this.uid = uid;
    }

    public long getStars() {
        return stars;
    }

    public void setStars(long stars) {
        this.stars = stars;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getRaterName() {
        return raterName;
    }

    public void setRaterName(String raterName) {
        this.raterName = raterName;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
