package com.campussync.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.List;

/**
 * Representation of a CampusSync user profile from Firestore ({@code users/{uid}}).
 *
 * The role and verification fields are owned by the server: the client can display them, but
 * Firestore security rules reject client writes to them (see {@code firestore.rules}), so they
 * must never be used on their own to grant access.
 */
public class UserProfile {

    public static final String ROLE_STUDENT = "student";
    public static final String ROLE_FACULTY = "faculty";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_SUSPENDED = "suspended";

    public static final String VERIFICATION_UNVERIFIED = "unverified";
    public static final String VERIFICATION_PENDING = "pending";
    public static final String VERIFICATION_VERIFIED = "verified";
    public static final String VERIFICATION_REJECTED = "rejected";

    @Exclude
    private String id;

    private String email;
    private String displayName;
    private String photoUrl;
    private String college;
    private String department;
    private String course;
    private long semester;
    private String academicYear;
    private String designation;
    private String bio;
    private List<String> skills;
    private List<String> interests;
    private String role = ROLE_STUDENT;
    private String accountStatus = STATUS_ACTIVE;
    private String verificationStatus = VERIFICATION_UNVERIFIED;
    private String verifiedRole;
    private Boolean emailVerified;
    private List<String> joinedEventIds;
    private List<String> joinedGroupIds;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;

    /** True when this user has an {@code admins/{uid}} document. Excluded from Firestore mapping. */
    @Exclude
    private boolean admin;

    public UserProfile() {
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

    @Exclude
    public boolean isAdmin() {
        return admin;
    }

    @Exclude
    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public long getSemester() {
        return semester;
    }

    public void setSemester(long semester) {
        this.semester = semester;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getVerifiedRole() {
        return verifiedRole;
    }

    public void setVerifiedRole(String verifiedRole) {
        this.verifiedRole = verifiedRole;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public List<String> getJoinedEventIds() {
        return joinedEventIds;
    }

    public void setJoinedEventIds(List<String> joinedEventIds) {
        this.joinedEventIds = joinedEventIds;
    }

    public List<String> getJoinedGroupIds() {
        return joinedGroupIds;
    }

    public void setJoinedGroupIds(List<String> joinedGroupIds) {
        this.joinedGroupIds = joinedGroupIds;
    }

    public java.util.Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.util.Date createdAt) {
        this.createdAt = createdAt;
    }

    public java.util.Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.util.Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Exclude
    public boolean isStudent() {
        return !ROLE_FACULTY.equals(role);
    }

    @Exclude
    public boolean isFaculty() {
        return ROLE_FACULTY.equals(role);
    }

    @Exclude
    public boolean isVerified() {
        return VERIFICATION_VERIFIED.equals(verificationStatus);
    }

    @Exclude
    public boolean isVerificationPending() {
        return VERIFICATION_PENDING.equals(verificationStatus);
    }

    @Exclude
    public boolean isSuspended() {
        return STATUS_SUSPENDED.equals(accountStatus);
    }

    @Exclude
    public int getJoinedEventsCount() {
        return joinedEventIds != null ? joinedEventIds.size() : 0;
    }

    @Exclude
    public int getJoinedGroupsCount() {
        return joinedGroupIds != null ? joinedGroupIds.size() : 0;
    }
}
