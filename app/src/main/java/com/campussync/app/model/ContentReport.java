package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * A moderation report stored in {@code reports/{reportId}}.
 *
 * Reports are created by any signed-in user and read/updated only by administrators, who can then
 * resolve the restricted attribution of anonymous content through {@code anonymousAuthors}.
 */
public class ContentReport {

    public static final String TYPE_POST = "post";
    public static final String TYPE_POST_COMMENT = "postComment";
    public static final String TYPE_STORY = "story";
    public static final String TYPE_STORY_REPLY = "storyReply";
    public static final String TYPE_DISCUSSION = "discussion";
    public static final String TYPE_DISCUSSION_COMMENT = "discussionComment";
    public static final String TYPE_USER = "user";
    public static final String TYPE_MESSAGE = "message";

    public static final String STATUS_OPEN = "open";
    public static final String STATUS_RESOLVED = "resolved";
    public static final String STATUS_DISMISSED = "dismissed";

    @Exclude
    private String id;

    private String targetType;
    private String targetId;
    private String targetLabel;
    private String reason;
    private String details;
    private String reporterId;
    private String status = STATUS_OPEN;
    private Date createdAt;

    @Exclude
    private String attributedAuthorId;

    @Exclude
    private String attributedAuthorName;

    public ContentReport() {
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

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public void setTargetLabel(String targetLabel) {
        this.targetLabel = targetLabel;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Exclude
    public String getAttributedAuthorId() {
        return attributedAuthorId;
    }

    @Exclude
    public void setAttributedAuthorId(String attributedAuthorId) {
        this.attributedAuthorId = attributedAuthorId;
    }

    @Exclude
    public String getAttributedAuthorName() {
        return attributedAuthorName;
    }

    @Exclude
    public void setAttributedAuthorName(String attributedAuthorName) {
        this.attributedAuthorName = attributedAuthorName;
    }
}
