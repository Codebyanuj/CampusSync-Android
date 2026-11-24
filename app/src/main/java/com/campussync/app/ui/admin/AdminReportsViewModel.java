package com.campussync.app.ui.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.R;
import com.campussync.app.firebase.SocialRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.ContentReport;
import com.campussync.app.model.UserProfile;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Administrator queue for content reports. The "reveal author" action reads the restricted
 * attribution document, which only administrators may access.
 */
public class AdminReportsViewModel extends ViewModel {

    private final SocialRepository repository = SocialRepository.getInstance();

    private final MutableLiveData<List<ContentReport>> reports =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();

    private ListenerRegistration registration;

    public LiveData<List<ContentReport>> getReports() {
        return reports;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Integer> getMessageRes() {
        return messageRes;
    }

    public void start() {
        if (registration != null) return;
        registration = repository.listenToOpenReports((list, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load reports.");
                return;
            }
            reports.setValue(list != null ? list : new ArrayList<ContentReport>());
        });
    }

    public void resolve(ContentReport report) {
        update(report, ContentReport.STATUS_RESOLVED, R.string.report_resolved);
    }

    public void dismiss(ContentReport report) {
        update(report, ContentReport.STATUS_DISMISSED, R.string.report_dismissed);
    }

    private void update(ContentReport report, String status, final int message) {
        if (report == null || report.getId() == null) return;
        repository.updateReportStatus(report.getId(), status, exception -> {
            if (exception == null) {
                messageRes.setValue(message);
            } else {
                error.setValue("Could not update the report.");
            }
        });
    }

    /** Reads the restricted attribution and resolves the author's display name. */
    public void reveal(final ContentReport report) {
        if (report == null || report.getTargetId() == null) return;
        repository.getAnonymousAuthor(report.getTargetId(), (snapshot, exception) -> {
            if (exception != null || snapshot == null || !snapshot.exists()) {
                messageRes.setValue(R.string.author_not_attributed);
                return;
            }
            String ownerId = snapshot.getString("ownerId");
            if (ownerId == null) {
                messageRes.setValue(R.string.author_not_attributed);
                return;
            }
            report.setAttributedAuthorId(ownerId);
            report.setAttributedAuthorName(ownerId);
            publish();
            UserRepository.getInstance().getProfile(ownerId, (profile, profileError) -> {
                if (profile != null) {
                    report.setAttributedAuthorName(displayName(profile));
                    publish();
                }
            });
        });
    }

    private static String displayName(UserProfile profile) {
        if (profile.getDisplayName() != null && !profile.getDisplayName().isEmpty()) {
            return profile.getDisplayName();
        }
        if (profile.getEmail() != null) return profile.getEmail();
        return profile.getId() != null ? profile.getId() : "Unknown";
    }

    private void publish() {
        List<ContentReport> current = reports.getValue();
        reports.setValue(current != null
                ? new ArrayList<>(current) : new ArrayList<ContentReport>());
    }

    public void clearMessage() {
        messageRes.setValue(null);
    }

    public void clearError() {
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
