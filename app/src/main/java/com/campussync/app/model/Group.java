package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.List;

/**
 * Represents a group in CampusSync.
 */
public class Group {

    @Exclude
    private String id;

    private String name;
    private String description;
    private String imageUrl;

    private List<String> memberIds;

    @ServerTimestamp
    private java.util.Date createdAt;

    public Group() {
        // Public no-arg constructor required for Firestore.
    }

    public Group(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Exclude
    public String getId() {
        return id;
    }

    @Exclude
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }

    public java.util.Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.util.Date createdAt) {
        this.createdAt = createdAt;
    }

    @Exclude
    public int getMemberCount() {
        return memberIds != null ? memberIds.size() : 0;
    }
}
