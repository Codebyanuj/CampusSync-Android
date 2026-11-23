package com.campussync.app.util;

import com.campussync.app.R;
import com.campussync.app.data.Colleges;
import com.campussync.app.model.UserProfile;

import java.util.regex.Pattern;

/**
 * Single source of validation rules for registration and profile editing.
 *
 * Each method returns a string resource id for the first problem found, or {@code 0} when the
 * value is valid, so the fragment and the unit tests exercise exactly the same logic.
 */
public final class ProfileValidator {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 60;
    private static final int BIO_MAX = 300;
    private static final int PASSWORD_MIN = 6;

    private ProfileValidator() {
    }

    public static int nameError(String name) {
        String trimmed = name != null ? name.trim() : "";
        if (trimmed.isEmpty()) return R.string.error_name_required;
        if (trimmed.length() < NAME_MIN) return R.string.error_name_short;
        if (trimmed.length() > NAME_MAX) return R.string.error_name_long;
        return 0;
    }

    public static int emailError(String email) {
        String trimmed = email != null ? email.trim() : "";
        if (trimmed.isEmpty()) return R.string.error_email_required;
        if (!EMAIL.matcher(trimmed).matches()) return R.string.error_invalid_email;
        return 0;
    }

    public static int passwordError(String password, String confirm) {
        if (password == null || password.isEmpty()) return R.string.error_password_required;
        if (password.length() < PASSWORD_MIN) return R.string.error_password_short;
        if (confirm != null && !password.equals(confirm)) return R.string.error_password_mismatch;
        return 0;
    }

    public static int roleError(String role) {
        if (UserProfile.ROLE_STUDENT.equals(role) || UserProfile.ROLE_FACULTY.equals(role)) {
            return 0;
        }
        return R.string.error_role_required;
    }

    public static int collegeError(String college) {
        if (college == null || college.trim().isEmpty()) return R.string.error_college_required;
        return 0;
    }

    public static int departmentError(String department) {
        if (department == null || department.trim().isEmpty()) {
            return R.string.error_department_required;
        }
        return 0;
    }

    /** Course and semester are required for students only. */
    public static int courseError(String course, String role) {
        if (UserProfile.ROLE_FACULTY.equals(role)) return 0;
        if (course == null || course.trim().isEmpty()) return R.string.error_course_required;
        return 0;
    }

    public static int semesterError(int semester, String role) {
        if (UserProfile.ROLE_FACULTY.equals(role)) return 0;
        if (semester < 1 || semester > 8) return R.string.error_semester_required;
        return 0;
    }

    public static int academicYearError(String academicYear) {
        if (academicYear == null || academicYear.trim().isEmpty()) {
            return R.string.error_academic_year_required;
        }
        return 0;
    }

    /** Designation is required for faculty only. */
    public static int designationError(String designation, String role) {
        if (!UserProfile.ROLE_FACULTY.equals(role)) return 0;
        if (designation == null || designation.trim().isEmpty()) {
            return R.string.error_designation_required;
        }
        return 0;
    }

    public static int bioError(String bio) {
        if (bio != null && bio.trim().length() > BIO_MAX) return R.string.error_bio_long;
        return 0;
    }

    public static int idNumberError(String idNumber) {
        if (idNumber == null || idNumber.trim().isEmpty()) return R.string.error_id_number_required;
        if (idNumber.trim().length() < 4) return R.string.error_id_number_short;
        return 0;
    }

    /** Parses a semester choice (1-8) and returns 0 when it is not a valid number. */
    public static int parseSemester(String value) {
        try {
            return Integer.parseInt(value != null ? value.trim() : "");
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** True when the college is one of the known entries, used by the editable dropdowns. */
    public static boolean isKnownCollege(String college) {
        return Colleges.collegeNames().contains(college);
    }
}
