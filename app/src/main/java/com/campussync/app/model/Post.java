package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;
import java.util.List;

/**
 * A social feed post stored in {@code posts/{postId}}.
 *
 * When {@link #anonymous} is true the document deliberately has no {@code authorId}: the author
 * mapping lives in {@code anonymousAuthors/{postId}} and is only readable by that author and by
 * administrators. {@code ownedByMe} is resolved on the client, never trusted by security rules.
 */
public class Post {

    @Exclude
    private String id;

    private String authorId;
    private String authorName;
    private String authorPhotoUrl;
    private String authorRole;
    private String college;
    private boolean anonymous;
    private String content;
    private List<String> imageUrls;
    private Date createdAt;
    private Date updatedAt;
    private long likeCount;
    private long commentCount;
    private long shareCount;
    private boolean edited;

    @Exclude
    private boolean likedByMe;
    @Exclude
    private boolean bookmarkedByMe;
    @Exclude
    private boolean ownedByMe;

    public Post() {
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

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorPhotoUrl() {
        return authorPhotoUrl;
    }

    public void setAuthorPhotoUrl(String authorPhotoUrl) {
        this.authorPhotoUrl = authorPhotoUrl;
    }

    public String getAuthorRole() {
        return authorRole;
    }

    public void setAuthorRole(String authorRole) {
        this.authorRole = authorRole;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
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

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public long getShareCount() {
        return shareCount;
    }

    public void setShareCount(long shareCount) {
        this.shareCount = shareCount;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    @Exclude
    public boolean isLikedByMe() {
        return likedByMe;
    }

    @Exclude
    public void setLikedByMe(boolean likedByMe) {
        this.likedByMe = likedByMe;
    }

    @Exclude
    public boolean isBookmarkedByMe() {
        return bookmarkedByMe;
    }

    @Exclude
    public void setBookmarkedByMe(boolean bookmarkedByMe) {
        this.bookmarkedByMe = bookmarkedByMe;
    }

    @Exclude
    public boolean isOwnedByMe() {
        return ownedByMe;
    }

    @Exclude
    public void setOwnedByMe(boolean ownedByMe) {
        this.ownedByMe = ownedByMe;
    }

    @Exclude
    public String getFirstImage() {
        return imageUrls != null && !imageUrls.isEmpty() ? imageUrls.get(0) : null;
    }

    @Exclude
    public boolean hasText() {
        return content != null && !content.trim().isEmpty();
    }
}
