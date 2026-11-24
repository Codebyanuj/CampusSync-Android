package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/** A college notice shown at the top of the event radar ({@code notices/{noticeId}}). */
public class CollegeNotice {

    @Exclude
    private String id;

    private String title;
    private String body;
    private String college;
    private String authorId;
    private String authorName;
    private boolean pinned;
    private Date createdAt;

    public CollegeNotice() {
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
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

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
