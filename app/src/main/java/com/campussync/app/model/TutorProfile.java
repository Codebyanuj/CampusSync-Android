package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;
import java.util.List;

/**
 * A student's tutor listing ({@code tutors/{uid}}). The profile fields mirror
 * {@code users/{uid}} so discovery can show a name, photo and department without a second read;
 * the owner is the only one who may edit them.
 */
public class TutorProfile {

    @Exclude
    private String id;

    private String displayName;
    private String photoUrl;
    private String college;
    private String department;
    private long semester;
    private List<String> subjects;
    private String bio;
    private long ratingSum;
    private long ratingCount;
    private long completedSessions;
    private Date createdAt;
    private Date updatedAt;

    public TutorProfile() {
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public long getSemester() {
        return semester;
    }

    public void setSemester(long semester) {
        this.semester = semester;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<String> subjects) {
        this.subjects = subjects;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public long getRatingSum() {
        return ratingSum;
    }

    public void setRatingSum(long ratingSum) {
        this.ratingSum = ratingSum;
    }

    public long getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(long ratingCount) {
        this.ratingCount = ratingCount;
    }

    public long getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(long completedSessions) {
        this.completedSessions = completedSessions;
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

    @Exclude
    public float getRatingAverage() {
        return ratingCount > 0 ? (float) ratingSum / ratingCount : 0f;
    }

    @Exclude
    public String getSubjectsLabel() {
        return subjects != null ? android.text.TextUtils.join(" · ", subjects) : "";
    }

    @Exclude
    public boolean offers(String subject) {
        if (subject == null || subjects == null) return false;
        for (String offered : subjects) {
            if (offered != null && offered.equalsIgnoreCase(subject.trim())) return true;
        }
        return false;
    }
}
