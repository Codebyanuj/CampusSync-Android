package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.List;

/**
 * Read-only representation of a CampusSync user profile from Firestore.
 * Kept separate from FirebaseUser for display and cached join state.
 */
public class UserProfile {

    @Exclude
    private String id;

    private String email;
    private String displayName;
    private List<String> joinedEventIds;
    private List<String> joinedGroupIds;
    private java.util.Date createdAt;

    public UserProfile() {
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getJoinedEventIds() {
        return joinedEventIds;
    }

    public void setJoinedEventIds(List<String> joinedEventIds) {
        this.joinedEventIds = joinedEventIds;
    }

    public List<String> getJoinedGroupIds() {
        return joinedGroupIds;
    }

    public void setJoinedGroupIds(List<String> joinedGroupIds) {
        this.joinedGroupIds = joinedGroupIds;
    }

    public java.util.Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.util.Date createdAt) {
        this.createdAt = createdAt;
    }

    @Exclude
    public int getJoinedEventsCount() {
        return joinedEventIds != null ? joinedEventIds.size() : 0;
    }

    @Exclude
    public int getJoinedGroupsCount() {
        return joinedGroupIds != null ? joinedGroupIds.size() : 0;
    }
}
