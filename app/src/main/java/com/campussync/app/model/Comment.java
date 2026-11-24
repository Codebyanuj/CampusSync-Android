package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * A comment on a feed post ({@code posts/{postId}/comments}) or a board discussion
 * ({@code boards/{boardId}/discussions/{discussionId}/comments}).
 *
 * {@code parentId} points at the comment being replied to; {@code null} means a top-level comment.
 * Anonymous comments omit {@code authorId} and rely on {@code anonymousAuthors/{commentId}}.
 */
public class Comment {

    @Exclude
    private String id;

    private String postId;
    private String discussionId;
    private String authorId;
    private String authorName;
    private String authorPhotoUrl;
    private boolean anonymous;
    private String content;
    private String parentId;
    private Date createdAt;

    @Exclude
    private boolean ownedByMe;

    public Comment() {
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

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getDiscussionId() {
        return discussionId;
    }

    public void setDiscussionId(String discussionId) {
        this.discussionId = discussionId;
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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Exclude
    public boolean isReply() {
        return parentId != null && !parentId.isEmpty();
    }

    @Exclude
    public boolean isOwnedByMe() {
        return ownedByMe;
    }

    @Exclude
    public void setOwnedByMe(boolean ownedByMe) {
        this.ownedByMe = ownedByMe;
    }
}
