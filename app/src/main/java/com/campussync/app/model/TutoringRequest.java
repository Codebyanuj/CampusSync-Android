package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * A peer tutoring request ({@code tutoringRequests/{requestId}}).
 *
 * <p>Feedback lives on the request itself ({@code reviewRating}, {@code reviewComment}), so a
 * session can be reviewed at most once: there is no second document to write. The student who
 * requested the session writes the review; the tutor can only accept, decline or complete it.
 */
public class TutoringRequest {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_DECLINED = "declined";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    @Exclude
    private String id;

    private String studentId;
    private String studentName;
    private String studentPhotoUrl;
    private String tutorId;
    private String tutorName;
    private String subject;
    private String message;
    private String preferredTime;
    private String status = STATUS_PENDING;
    private long reviewRating;
    private String reviewComment;
    private Date createdAt;
    private Date updatedAt;
    private Date reviewedAt;
    private Date completedAt;

    public TutoringRequest() {
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

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentPhotoUrl() {
        return studentPhotoUrl;
    }

    public void setStudentPhotoUrl(String studentPhotoUrl) {
        this.studentPhotoUrl = studentPhotoUrl;
    }

    public String getTutorId() {
        return tutorId;
    }

    public void setTutorId(String tutorId) {
        this.tutorId = tutorId;
    }

    public String getTutorName() {
        return tutorName;
    }

    public void setTutorName(String tutorName) {
        this.tutorName = tutorName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPreferredTime() {
        return preferredTime;
    }

    public void setPreferredTime(String preferredTime) {
        this.preferredTime = preferredTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getReviewRating() {
        return reviewRating;
    }

    public void setReviewRating(long reviewRating) {
        this.reviewRating = reviewRating;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
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

    public Date getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Date reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Date getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Date completedAt) {
        this.completedAt = completedAt;
    }

    @Exclude
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    @Exclude
    public boolean isAccepted() {
        return STATUS_ACCEPTED.equals(status);
    }

    @Exclude
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    @Exclude
    public boolean isReviewed() {
        return reviewRating > 0;
    }

    @Exclude
    public boolean isOpen() {
        return STATUS_PENDING.equals(status) || STATUS_ACCEPTED.equals(status);
    }
}
