package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * One row of {@code conversations/{conversationId}/members/{uid}}.
 *
 * <p>This subcollection is what security rules check before allowing any read or write on a
 * thread, and it holds the per-member state (unread counter, mute, last read position) that a
 * member may change for themselves and a group admin may change for everybody.
 */
public class ConversationMember {

    @Exclude
    private String uid;

    private String name;
    private String photoUrl;
    private String role = Conversation.ROLE_MEMBER;
    private boolean muted;
    private long unreadCount;
    private Date lastReadAt;
    private Date joinedAt;

    public ConversationMember() {
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

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }

    public Date getLastReadAt() {
        return lastReadAt;
    }

    public void setLastReadAt(Date lastReadAt) {
        this.lastReadAt = lastReadAt;
    }

    public Date getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Date joinedAt) {
        this.joinedAt = joinedAt;
    }

    @Exclude
    public boolean isAdmin() {
        return Conversation.ROLE_ADMIN.equals(role);
    }
}
