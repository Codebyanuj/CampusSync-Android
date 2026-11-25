package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.Date;
import java.util.List;

/**
 * A shared study resource ({@code resources/{resourceId}}): notes, a PDF, a slide deck or a
 * previous-year question paper.
 *
 * <p>The aggregate counters ({@code downloadCount}, {@code bookmarkCount}, {@code ratingSum},
 * {@code ratingCount}) are display state maintained by client transactions. Security rules bound
 * every one of those moves; the rating itself is a per-user document
 * ({@code resources/{id}/ratings/{uid}}) so nobody can rate the same resource twice.
 */
public class AcademicResource {

    public static final String TYPE_NOTES = "notes";
    public static final String TYPE_PDF = "pdf";
    public static final String TYPE_SLIDES = "slides";
    public static final String TYPE_QUESTION_PAPER = "questionPaper";

    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_REMOVED = "removed";

    @Exclude
    private String id;

    private String title;
    private String description;
    private String type;
    private String subject;
    private String subjectLower;
    private String department;
    private long semester;
    private String college;
    private List<String> tags;

    private String fileName;
    private String fileUrl;
    private String filePath;
    private long fileSize;
    private String mimeType;

    private String uploaderId;
    private String uploaderName;
    private String uploaderPhotoUrl;

    private long downloadCount;
    private long bookmarkCount;
    private long ratingSum;
    private long ratingCount;

    private String status = STATUS_PUBLISHED;
    private Date createdAt;
    private Date updatedAt;

    public AcademicResource() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubjectLower() {
        return subjectLower;
    }

    public void setSubjectLower(String subjectLower) {
        this.subjectLower = subjectLower;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public long getSemester() {
        return semester;
    }

    public void setSemester(long semester) {
        this.semester = semester;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
    }

    public String getUploaderPhotoUrl() {
        return uploaderPhotoUrl;
    }

    public void setUploaderPhotoUrl(String uploaderPhotoUrl) {
        this.uploaderPhotoUrl = uploaderPhotoUrl;
    }

    public long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(long downloadCount) {
        this.downloadCount = downloadCount;
    }

    public long getBookmarkCount() {
        return bookmarkCount;
    }

    public void setBookmarkCount(long bookmarkCount) {
        this.bookmarkCount = bookmarkCount;
    }

    public long getRatingSum() {
        return ratingSum;
    }

    public void setRatingSum(long ratingSum) {
        this.ratingSum = ratingSum;
    }

    public long getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(long ratingCount) {
        this.ratingCount = ratingCount;
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

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Exclude
    public String getTypeOrDefault() {
        return type != null ? type : TYPE_NOTES;
    }

    @Exclude
    public boolean isRemoved() {
        return STATUS_REMOVED.equals(status);
    }

    /** Mean of the loaded ratings, or 0 when nobody rated the resource yet. */
    @Exclude
    public float getRatingAverage() {
        return ratingCount > 0 ? (float) ratingSum / ratingCount : 0f;
    }

    @Exclude
    public String getSemesterLabel() {
        return semester > 0 ? "Semester " + semester : "";
    }
}
