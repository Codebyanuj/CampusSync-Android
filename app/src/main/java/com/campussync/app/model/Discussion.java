package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * A board discussion stored in {@code boards/{boardId}/discussions/{discussionId}}.
 *
 * Anonymous discussions omit {@code authorId}; attribution lives in
 * {@code anonymousAuthors/{discussionId}} and is readable only by its author and administrators.
 */
public class Discussion {

    @Exclude
    private String id;

    private String boardId;
    private String title;
    private String titleLower;
    private String body;
    private String authorId;
    private String authorName;
    private String college;
    private boolean anonymous;
    private long upvoteCount;
    private long downvoteCount;
    private long commentCount;
    private Date createdAt;
    private Date lastActivityAt;

    @Exclude
    private long myVote;
    @Exclude
    private boolean ownedByMe;

    public Discussion() {
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

    public String getBoardId() {
        return boardId;
    }

    public void setBoardId(String boardId) {
        this.boardId = boardId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleLower() {
        return titleLower;
    }

    public void setTitleLower(String titleLower) {
        this.titleLower = titleLower;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
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

    public long getUpvoteCount() {
        return upvoteCount;
    }

    public void setUpvoteCount(long upvoteCount) {
        this.upvoteCount = upvoteCount;
    }

    public long getDownvoteCount() {
        return downvoteCount;
    }

    public void setDownvoteCount(long downvoteCount) {
        this.downvoteCount = downvoteCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Date lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    @Exclude
    public long getMyVote() {
        return myVote;
    }

    @Exclude
    public void setMyVote(long myVote) {
        this.myVote = myVote;
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
    public long getScore() {
        return upvoteCount - downvoteCount;
    }
}
