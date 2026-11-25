package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * One line of the karma ledger ({@code karmaAwards/{awardId}}).
 *
 * <p>The document id is derived from the contribution ({@code sourceType_sourceId}), so a single
 * contribution can be rewarded exactly once. Only the trusted backend writes ledger entries; the
 * client may read its own rows and derives the balance by summing them.
 */
public class KarmaEntry {

    public static final String TYPE_ANSWER_ACCEPTED = "answer_accepted";
    public static final String TYPE_TUTOR_REVIEW = "tutor_review";
    public static final String TYPE_RESOURCE_HELPFUL = "resource_helpful";

    @Exclude
    private String id;

    private String recipientId;
    private String sourceType;
    private String sourceId;
    private long amount;
    private String note;
    private Date createdAt;

    public KarmaEntry() {
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

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
