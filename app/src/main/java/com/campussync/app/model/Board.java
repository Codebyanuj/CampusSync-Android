package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/** A community board (Reddit-style subreddit) stored in {@code boards/{boardId}}. */
public class Board {

    @Exclude
    private String id;

    private String name;
    private String nameLower;
    private String description;
    private String college;
    private String createdBy;
    private long discussionCount;
    private Date createdAt;

    public Board() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameLower() {
        return nameLower;
    }

    public void setNameLower(String nameLower) {
        this.nameLower = nameLower;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getDiscussionCount() {
        return discussionCount;
    }

    public void setDiscussionCount(long discussionCount) {
        this.discussionCount = discussionCount;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Exclude
    public String getInitial() {
        return name != null && !name.isEmpty() ? name.substring(0, 1).toUpperCase() : "?";
    }

    @Exclude
    public String getDisplayName() {
        return name != null ? name : "";
    }
}
