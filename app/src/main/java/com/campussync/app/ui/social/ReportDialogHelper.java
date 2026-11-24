package com.campussync.app.ui.social;

import androidx.fragment.app.Fragment;

import com.campussync.app.R;
import com.campussync.app.firebase.SocialRepository;
import com.campussync.app.model.ContentReport;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/** Shared "report this content" flow used by every social surface. */
public final class ReportDialogHelper {

    private ReportDialogHelper() {
    }

    public static void show(final Fragment fragment, final String targetType,
                            final String targetId, final String targetLabel) {
        if (fragment == null || !fragment.isAdded() || targetId == null) return;
        final String[] reasons = {
                fragment.getString(R.string.report_reason_spam),
                fragment.getString(R.string.report_reason_harassment),
                fragment.getString(R.string.report_reason_inappropriate),
                fragment.getString(R.string.report_reason_misinformation),
                fragment.getString(R.string.report_reason_other)
        };
        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.report_title)
                .setItems(reasons, (dialog, which) -> {
                    ContentReport report = new ContentReport();
                    report.setTargetType(targetType);
                    report.setTargetId(targetId);
                    report.setTargetLabel(targetLabel != null ? targetLabel : "");
                    report.setReason(reasons[which]);
                    report.setDetails("");
                    SocialRepository.getInstance().report(report, error -> {
                        if (!fragment.isAdded() || fragment.getView() == null) return;
                        Snackbar.make(fragment.requireView(),
                                error == null ? R.string.report_sent : R.string.error_generic,
                                Snackbar.LENGTH_LONG).show();
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** First 60 characters, used as the report's human readable target label. */
    public static String label(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        return trimmed.length() > 60 ? trimmed.substring(0, 60) + "…" : trimmed;
    }
}
