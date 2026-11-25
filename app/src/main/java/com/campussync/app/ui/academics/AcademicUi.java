package com.campussync.app.ui.academics;

import android.content.Context;

import com.campussync.app.R;
import com.campussync.app.model.AcademicResource;
import com.campussync.app.model.TutoringRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Formatting shared by the knowledge-sharing and peer tutoring screens. */
public final class AcademicUi {

    public static final String[] TYPE_KEYS = {
            AcademicResource.TYPE_NOTES,
            AcademicResource.TYPE_PDF,
            AcademicResource.TYPE_SLIDES,
            AcademicResource.TYPE_QUESTION_PAPER
    };

    private AcademicUi() {
    }

    public static String typeLabel(Context context, String key) {
        if (context == null || key == null) return "";
        switch (key) {
            case AcademicResource.TYPE_PDF:
                return context.getString(R.string.resource_type_pdf);
            case AcademicResource.TYPE_SLIDES:
                return context.getString(R.string.resource_type_slides);
            case AcademicResource.TYPE_QUESTION_PAPER:
                return context.getString(R.string.resource_type_paper);
            default:
                return context.getString(R.string.resource_type_notes);
        }
    }

    /** "Data Structures · Computer Science · Semester 3" with the empty parts dropped. */
    public static String organisationLine(AcademicResource resource) {
        if (resource == null) return "";
        StringBuilder builder = new StringBuilder();
        append(builder, resource.getSubject());
        append(builder, resource.getDepartment());
        if (resource.getSemester() > 0) append(builder, "Semester " + resource.getSemester());
        return builder.toString();
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append(" · ");
        builder.append(value.trim());
    }

    public static String statusLabel(Context context, TutoringRequest request) {
        if (context == null || request == null) return "";
        switch (request.getStatus() != null ? request.getStatus() : "") {
            case TutoringRequest.STATUS_ACCEPTED:
                return context.getString(R.string.tutoring_status_accepted);
            case TutoringRequest.STATUS_DECLINED:
                return context.getString(R.string.tutoring_status_declined);
            case TutoringRequest.STATUS_COMPLETED:
                return context.getString(R.string.tutoring_status_completed);
            case TutoringRequest.STATUS_CANCELLED:
                return context.getString(R.string.tutoring_status_cancelled);
            default:
                return context.getString(R.string.tutoring_status_pending);
        }
    }

    public static String dateTime(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date);
    }

    /** "Maths, Physics" from the comma separated tutor form, limited to five subjects. */
    public static java.util.List<String> parseSubjects(String raw) {
        java.util.List<String> subjects = new java.util.ArrayList<>();
        if (raw == null) return subjects;
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            if (!subjects.contains(value)) subjects.add(value);
            if (subjects.size() >= 5) break;
        }
        return subjects;
    }

    public static String joinSubjects(List<String> subjects) {
        return subjects != null ? android.text.TextUtils.join(", ", subjects) : "";
    }
}
