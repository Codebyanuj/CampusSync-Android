package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;
import java.util.List;

/**
 * A single chat message stored in
 * {@code conversations/{conversationId}/messages/{messageId}}.
 *
 * <p>{@code readBy} carries the read receipts: the sender is inserted when the message is written
 * and every other member is added when they open the thread.
 */
public class Message {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_DOCUMENT = "document";
    public static final String TYPE_SYSTEM = "system";

    @Exclude
    private String id;

    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderPhotoUrl;
    private String type = TYPE_TEXT;
    private String text;
    private String attachmentUrl;
    private String attachmentName;
    private long attachmentSize;
    private String mimeType;
    private List<String> readBy;
    private Date createdAt;

    public Message() {
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

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderPhotoUrl() {
        return senderPhotoUrl;
    }

    public void setSenderPhotoUrl(String senderPhotoUrl) {
        this.senderPhotoUrl = senderPhotoUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }

    public long getAttachmentSize() {
        return attachmentSize;
    }

    public void setAttachmentSize(long attachmentSize) {
        this.attachmentSize = attachmentSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public List<String> getReadBy() {
        return readBy;
    }

    public void setReadBy(List<String> readBy) {
        this.readBy = readBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Exclude
    public boolean isImage() {
        return TYPE_IMAGE.equals(type);
    }

    @Exclude
    public boolean isDocument() {
        return TYPE_DOCUMENT.equals(type);
    }

    @Exclude
    public boolean hasText() {
        return text != null && !text.trim().isEmpty();
    }

    @Exclude
    public boolean isReadBy(String uid) {
        return uid != null && readBy != null && readBy.contains(uid);
    }

    @Exclude
    public boolean isSystem() {
        return TYPE_SYSTEM.equals(type);
    }
}
