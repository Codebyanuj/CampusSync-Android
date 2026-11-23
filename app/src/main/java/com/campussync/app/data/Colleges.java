package com.campussync.app.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference data used by registration and profile editing.
 *
 * Kept in one place (instead of duplicated string arrays per screen) so the registration form and
 * the edit-profile form always offer the same colleges, departments, courses and academic years.
 */
public final class Colleges {

    public static final String OTHER = "Other / Not listed";

    private static final Map<String, List<String>> DEPARTMENTS = new LinkedHashMap<>();

    static {
        DEPARTMENTS.put("Delhi Technological University", Arrays.asList(
                "Computer Science & Engineering", "Information Technology", "Electronics & Communication",
                "Mechanical Engineering", "Civil Engineering", "Applied Mathematics"));
        DEPARTMENTS.put("University of Delhi", Arrays.asList(
                "Computer Science", "Commerce", "Economics", "English", "Physics", "Law"));
        DEPARTMENTS.put("Indian Institute of Technology, Delhi", Arrays.asList(
                "Computer Science & Engineering", "Electrical Engineering", "Mechanical Engineering",
                "Chemical Engineering", "Management Studies"));
        DEPARTMENTS.put("National Institute of Technology, Trichy", Arrays.asList(
                "Computer Science & Engineering", "Electronics & Communication", "Production Engineering",
                "Civil Engineering", "Architecture"));
        DEPARTMENTS.put("Anna University", Arrays.asList(
                "Information Technology", "Computer Science & Engineering", "Biotechnology",
                "Mechanical Engineering", "Business Administration"));
        DEPARTMENTS.put("Amity University", Arrays.asList(
                "Computer Science & Engineering", "Journalism & Mass Communication", "Law",
                "Management", "Psychology", "Architecture"));
        DEPARTMENTS.put(OTHER, Arrays.asList(
                "Arts & Science", "Commerce", "Engineering", "Management", "Medicine", "Law", "Education"));
    }

    private static final List<String> COURSES = Collections.unmodifiableList(Arrays.asList(
            "B.Tech", "B.E.", "B.Sc", "BCA", "B.Com", "BBA", "BA", "B.Arch",
            "M.Tech", "M.Sc", "MCA", "MBA", "MA", "Ph.D"));

    private static final List<String> DESIGNATIONS = Collections.unmodifiableList(Arrays.asList(
            "Professor", "Associate Professor", "Assistant Professor", "Lecturer",
            "Teaching Assistant", "Lab Instructor", "Research Scholar"));

    private Colleges() {
    }

    public static List<String> collegeNames() {
        return Collections.unmodifiableList(new ArrayList<>(DEPARTMENTS.keySet()));
    }

    /** Departments offered by {@code college}; falls back to the "Other" list. */
    public static List<String> departmentsFor(String college) {
        List<String> departments = college != null ? DEPARTMENTS.get(college) : null;
        return departments != null ? departments : DEPARTMENTS.get(OTHER);
    }

    public static List<String> courses() {
        return COURSES;
    }

    public static List<String> semesters() {
        List<String> semesters = new ArrayList<>(8);
        for (int i = 1; i <= 8; i++) {
            semesters.add(String.valueOf(i));
        }
        return semesters;
    }

    /** Academic years around the current one, newest first, e.g. {@code 2026-27}. */
    public static List<String> academicYears() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        List<String> years = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            years.add(formatAcademicYear(year + i));
        }
        return years;
    }

    public static String formatAcademicYear(int startYear) {
        int endYear = (startYear + 1) % 100;
        return startYear + "-" + (endYear < 10 ? "0" + endYear : String.valueOf(endYear));
    }

    public static List<String> designations() {
        return DESIGNATIONS;
    }
}
