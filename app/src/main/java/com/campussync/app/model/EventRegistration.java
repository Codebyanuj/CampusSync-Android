package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * One row of {@code events/{eventId}/registrations/{uid}).
 *
 * <p>The document id is the participant's uid, so a person can only ever have one registration per
 * event. {@code qrToken} is the secret embedded in that participant's QR ticket; the organizer's
 * scanner compares it with this document before attendance is written, which is what makes a
 * check-in trustworthy and impossible to duplicate.
 */
public class EventRegistration {

    public static final String STATUS_REGISTERED = "registered";
    public static final String STATUS_CANCELLED = "cancelled";

    @Exclude
    private String uid;

    private String name;
    private String email;
    private String college;
    private String status = STATUS_REGISTERED;
    private String qrToken;
    private boolean attended;
    private Date checkedInAt;
    private Date createdAt;
    private Date updatedAt;

    public EventRegistration() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public boolean isAttended() {
        return attended;
    }

    public void setAttended(boolean attended) {
        this.attended = attended;
    }

    public Date getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(Date checkedInAt) {
        this.checkedInAt = checkedInAt;
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
    public boolean isRegistered() {
        return STATUS_REGISTERED.equals(status);
    }
}
