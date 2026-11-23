package com.campussync.app.ui.auth;

import com.campussync.app.model.UserProfile;
import com.campussync.app.util.ProfileValidator;

import java.util.HashMap;
import java.util.Map;

/**
 * Values collected by the registration form.
 *
 * Keeping this out of the fragment makes the profile document shape explicit and lets the
 * validation rules be unit tested without an Activity.
 */
public class RegistrationInput {

    public String fullName;
    public String email;
    public String password;
    public String confirmPassword;
    public String role = UserProfile.ROLE_STUDENT;
    public String college;
    public String department;
    public String course;
    public String semester;
    public String academicYear;
    public String designation;

    public boolean isFaculty() {
        return UserProfile.ROLE_FACULTY.equals(role);
    }

    /** @return a string resource id for the first invalid field, or 0 when everything is valid. */
    public int validate() {
        int error = ProfileValidator.nameError(fullName);
        if (error != 0) return error;
        error = ProfileValidator.emailError(email);
        if (error != 0) return error;
        error = ProfileValidator.passwordError(password, confirmPassword);
        if (error != 0) return error;
        error = ProfileValidator.roleError(role);
        if (error != 0) return error;
        error = ProfileValidator.collegeError(college);
        if (error != 0) return error;
        error = ProfileValidator.departmentError(department);
        if (error != 0) return error;
        error = ProfileValidator.courseError(course, role);
        if (error != 0) return error;
        error = ProfileValidator.semesterError(ProfileValidator.parseSemester(semester), role);
        if (error != 0) return error;
        error = ProfileValidator.academicYearError(academicYear);
        if (error != 0) return error;
        return ProfileValidator.designationError(designation, role);
    }

    /**
     * Firestore fields for {@code users/{uid}} at registration.
     *
     * Role and status are written here because the account does not exist yet; security rules
     * only allow these exact initial values and forbid the owner from changing them later.
     */
    public Map<String, Object> toProfileMap() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("email", trim(email));
        profile.put("displayName", trim(fullName));
        profile.put("role", role);
        profile.put("college", trim(college));
        profile.put("department", trim(department));
        profile.put("academicYear", trim(academicYear));
        profile.put("accountStatus", UserProfile.STATUS_ACTIVE);
        profile.put("verificationStatus", UserProfile.VERIFICATION_UNVERIFIED);
        if (isFaculty()) {
            profile.put("designation", trim(designation));
            profile.put("course", "");
            profile.put("semester", 0);
        } else {
            profile.put("course", trim(course));
            profile.put("semester", ProfileValidator.parseSemester(semester));
            profile.put("designation", "");
        }
        return profile;
    }

    private static String trim(String value) {
        return value != null ? value.trim() : "";
    }
}
