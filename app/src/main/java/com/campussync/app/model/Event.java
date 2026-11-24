package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.List;

/**
 * An event in {@code events/{eventId}}.
 *
 * <p>{@code registrationCount} and {@code attendeeIds} mirror the
 * {@code registrations/} subcollection so lists can show capacity and "registered" state without
 * reading every registration. The subcollection is the authoritative record; security rules only
 * let the counters move within the capacity.
 */
public class Event {

    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_CANCELLED = "cancelled";

    public static final String CATEGORY_ACADEMIC = "academic";
    public static final String CATEGORY_CULTURAL = "cultural";
    public static final String CATEGORY_SPORTS = "sports";
    public static final String CATEGORY_WORKSHOP = "workshop";
    public static final String CATEGORY_TECH = "tech";
    public static final String CATEGORY_SOCIAL = "social";
    public static final String CATEGORY_OTHER = "other";

    @Exclude
    private String id;

    private String title;
    private String description;
    private String location;
    private String organizerId;
    private String organizerName;

    @ServerTimestamp
    private java.util.Date startDate;

    @ServerTimestamp
    private java.util.Date endDate;

    private String imageUrl;
    private String category;
    private String college;
    private String status = STATUS_PUBLISHED;
    private long capacity;
    private long registrationCount;
    private boolean intercollege;

    private List<String> attendeeIds;

    private java.util.Date createdAt;
    private java.util.Date updatedAt;

    public Event() {
        // Public no-arg constructor required for Firestore.
    }

    public Event(String title, String description, String location, String organizerId) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.organizerId = organizerId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
    }

    public java.util.Date getStartDate() {
        return startDate;
    }

    public void setStartDate(java.util.Date startDate) {
        this.startDate = startDate;
    }

    public java.util.Date getEndDate() {
        return endDate;
    }

    public void setEndDate(java.util.Date endDate) {
        this.endDate = endDate;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public long getRegistrationCount() {
        return registrationCount;
    }

    public void setRegistrationCount(long registrationCount) {
        this.registrationCount = registrationCount;
    }

    public boolean isIntercollege() {
        return intercollege;
    }

    public void setIntercollege(boolean intercollege) {
        this.intercollege = intercollege;
    }

    public List<String> getAttendeeIds() {
        return attendeeIds;
    }

    public void setAttendeeIds(List<String> attendeeIds) {
        this.attendeeIds = attendeeIds;
    }

    public java.util.Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.util.Date createdAt) {
        this.createdAt = createdAt;
    }

    public java.util.Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.util.Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ---------------- Client-side helpers ----------------

    @Exclude
    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    @Exclude
    public boolean isIntercollegeOrUnknown() {
        return intercollege;
    }

    /** 0 means unlimited seats. */
    @Exclude
    public boolean isFull() {
        return capacity > 0 && registrationCount >= capacity;
    }

    @Exclude
    public long getSeatsLeft() {
        if (capacity <= 0) return -1;
        return Math.max(0, capacity - registrationCount);
    }

    @Exclude
    public boolean hasEnded() {
        java.util.Date reference = endDate != null ? endDate : startDate;
        return reference != null && reference.before(new java.util.Date());
    }

    @Exclude
    public boolean isRegistered(String uid) {
        return uid != null && attendeeIds != null && attendeeIds.contains(uid);
    }

    @Exclude
    public String getShortDate() {
        if (startDate == null) return null;
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
        return format.format(startDate);
    }

    @Exclude
    public String getShortTime() {
        if (startDate == null) return null;
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
        return format.format(startDate);
    }

    @Exclude
    public String getCategoryOrDefault() {
        return category != null && !category.isEmpty() ? category : CATEGORY_OTHER;
    }
}
