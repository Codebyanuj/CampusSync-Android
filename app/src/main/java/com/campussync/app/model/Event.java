package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.List;

/**
 * Represents an event in CampusSync.
 */
public class Event {

    @Exclude
    private String id;

    private String title;
    private String description;
    private String location;
    private String organizerId;

    @ServerTimestamp
    private java.util.Date startDate;

    @ServerTimestamp
    private java.util.Date endDate;

    private String imageUrl;

    private List<String> attendeeIds;

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

    public List<String> getAttendeeIds() {
        return attendeeIds;
    }

    public void setAttendeeIds(List<String> attendeeIds) {
        this.attendeeIds = attendeeIds;
    }

    @Exclude
    public String getShortDate() {
        if (startDate == null) return null;
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
        return format.format(startDate);
    }

    @Exclude
    public String getShortTime() {
        if (startDate == null) return null;
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
        return format.format(startDate);
    }
}
