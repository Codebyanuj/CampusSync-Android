package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A chat thread stored in {@code conversations/{conversationId}}: either a one-to-one
 * conversation ({@link #TYPE_DIRECT}) or a group chat ({@link #TYPE_GROUP}).
 *
 * <p>Membership lives both on this document ({@code memberIds}, used for listing) and in the
 * {@code members/} subcollection, which security rules read to decide whether a caller may open
 * the thread (see {@code firestore.rules}). The client-only fields below are filled in by
 * {@code MessagingRepository} for list rendering and are never written to Firestore.
 */
public class Conversation {

    public static final String TYPE_DIRECT = "direct";
    public static final String TYPE_GROUP = "group";

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MEMBER = "member";

    @Exclude
    private String id;

    private String type = TYPE_DIRECT;
    private String title;
    private String photoUrl;
    private List<String> memberIds;
    private List<String> adminIds;
    private Map<String, String> memberNames;
    private Map<String, String> memberPhotos;
    private Map<String, Long> unreadCounts;
    private String ownerId;
    private String directKey;
    /** The two participants of a direct chat, so security rules can resolve the other party. */
    private String directA;
    private String directB;
    private long memberCount;
    private String lastMessage;
    private Date lastMessageAt;
    private String lastSenderId;
    private Date createdAt;
    private Date updatedAt;

    @Exclude
    private String otherUserId;
    @Exclude
    private String displayTitle;
    @Exclude
    private String displayPhotoUrl;
    @Exclude
    private long unreadCount;
    @Exclude
    private boolean muted;

    public Conversation() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }

    public List<String> getAdminIds() {
        return adminIds;
    }

    public void setAdminIds(List<String> adminIds) {
        this.adminIds = adminIds;
    }

    public Map<String, String> getMemberNames() {
        return memberNames;
    }

    public void setMemberNames(Map<String, String> memberNames) {
        this.memberNames = memberNames;
    }

    public Map<String, String> getMemberPhotos() {
        return memberPhotos;
    }

    public void setMemberPhotos(Map<String, String> memberPhotos) {
        this.memberPhotos = memberPhotos;
    }

    public Map<String, Long> getUnreadCounts() {
        return unreadCounts;
    }

    public void setUnreadCounts(Map<String, Long> unreadCounts) {
        this.unreadCounts = unreadCounts;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getDirectKey() {
        return directKey;
    }

    public void setDirectKey(String directKey) {
        this.directKey = directKey;
    }

    public String getDirectA() {
        return directA;
    }

    public void setDirectA(String directA) {
        this.directA = directA;
    }

    public String getDirectB() {
        return directB;
    }

    public void setDirectB(String directB) {
        this.directB = directB;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(long memberCount) {
        this.memberCount = memberCount;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Date getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Date lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getLastSenderId() {
        return lastSenderId;
    }

    public void setLastSenderId(String lastSenderId) {
        this.lastSenderId = lastSenderId;
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

    // ---------------- Client-side rendering helpers ----------------

    @Exclude
    public String getOtherUserId() {
        return otherUserId;
    }

    @Exclude
    public void setOtherUserId(String otherUserId) {
        this.otherUserId = otherUserId;
    }

    @Exclude
    public String getDisplayTitle() {
        return displayTitle;
    }

    @Exclude
    public void setDisplayTitle(String displayTitle) {
        this.displayTitle = displayTitle;
    }

    @Exclude
    public String getDisplayPhotoUrl() {
        return displayPhotoUrl;
    }

    @Exclude
    public void setDisplayPhotoUrl(String displayPhotoUrl) {
        this.displayPhotoUrl = displayPhotoUrl;
    }

    @Exclude
    public long getUnreadCount() {
        return unreadCount;
    }

    @Exclude
    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }

    @Exclude
    public boolean isMuted() {
        return muted;
    }

    @Exclude
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    @Exclude
    public boolean isGroup() {
        return TYPE_GROUP.equals(type);
    }

    @Exclude
    public boolean isAdmin(String uid) {
        return uid != null && adminIds != null && adminIds.contains(uid);
    }

    @Exclude
    public Date getActivityAt() {
        return lastMessageAt != null ? lastMessageAt : createdAt;
    }

    @Exclude
    public long unreadFor(String uid) {
        if (uid == null || unreadCounts == null) return 0;
        Object value = unreadCounts.get(uid);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    @Exclude
    public String nameOf(String uid) {
        if (uid == null || memberNames == null) return null;
        return memberNames.get(uid);
    }

    @Exclude
    public String photoOf(String uid) {
        if (uid == null || memberPhotos == null) return null;
        return memberPhotos.get(uid);
    }

    @Exclude
    public String otherMemberId(String uid) {
        if (otherUserId != null) return otherUserId;
        if (memberIds == null || uid == null) return null;
        for (String memberId : memberIds) {
            if (!uid.equals(memberId)) return memberId;
        }
        return null;
    }
}
