package com.campussync.app.ui.academics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.ResourceRepository;
import com.campussync.app.firebase.TutoringRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.AcademicResource;
import com.campussync.app.model.Doubt;
import com.campussync.app.model.TutorProfile;
import com.campussync.app.model.UserProfile;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Academic hub: the shared resource library, the doubt board and the tutor directory.
 *
 * <p>Like the event radar, the Firestore queries only order/limit (no composite index) and every
 * filter — type, department, semester, saved-only, free text — runs over the loaded window.
 */
public class AcademicHubViewModel extends ViewModel {

    private final ResourceRepository resources = ResourceRepository.getInstance();
    private final TutoringRepository tutoring = TutoringRepository.getInstance();
    private final UserRepository users = UserRepository.getInstance();

    private final MutableLiveData<List<AcademicResource>> library =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Doubt>> doubts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<TutorProfile>> tutors =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<UserProfile> myProfile = new MutableLiveData<>();
    private final MutableLiveData<TutorProfile> myTutor = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    private final List<AcademicResource> librarySource = new ArrayList<>();
    private final List<Doubt> doubtSource = new ArrayList<>();
    private final List<TutorProfile> tutorSource = new ArrayList<>();
    private final Set<String> savedIds = new HashSet<>();

    private String query = "";
    private String type;
    private boolean myDepartmentOnly;
    private boolean mySemesterOnly;
    private boolean savedOnly;

    private ListenerRegistration libraryRegistration;
    private ListenerRegistration doubtsRegistration;
    private ListenerRegistration tutorsRegistration;
    private ListenerRegistration bookmarksRegistration;
    private ListenerRegistration myTutorRegistration;
    private boolean profileRequested;

    public LiveData<List<AcademicResource>> getLibrary() {
        return library;
    }

    public LiveData<List<Doubt>> getDoubts() {
        return doubts;
    }

    public LiveData<List<TutorProfile>> getTutors() {
        return tutors;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<UserProfile> getMyProfile() {
        return myProfile;
    }

    public LiveData<TutorProfile> getMyTutor() {
        return myTutor;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public void clearMessage() {
        message.setValue(null);
    }

    public void startListening() {
        String uid = ResourceRepository.currentUidOrNull();
        if (uid == null) {
            loading.setValue(false);
            return;
        }
        error.setValue(null);
        loading.setValue(true);

        if (libraryRegistration == null) {
            libraryRegistration = resources.listenToLibrary((list, exception) -> {
                loading.setValue(false);
                if (exception != null) {
                    error.setValue("Unable to load the resource library. Check your connection.");
                    return;
                }
                librarySource.clear();
                if (list != null) librarySource.addAll(list);
                applyResourceFilters();
            });
        }
        if (doubtsRegistration == null) {
            doubtsRegistration = tutoring.listenToDoubts((list, exception) -> {
                if (exception != null) {
                    error.setValue("Unable to load doubts. Check your connection.");
                    return;
                }
                doubtSource.clear();
                if (list != null) doubtSource.addAll(list);
                applyDoubtFilters();
            });
        }
        if (tutorsRegistration == null) {
            tutorsRegistration = tutoring.listenToTutors((list, exception) -> {
                if (exception != null) {
                    error.setValue("Unable to load tutors. Check your connection.");
                    return;
                }
                tutorSource.clear();
                if (list != null) tutorSource.addAll(list);
                applyTutorFilters();
            });
        }
        if (bookmarksRegistration == null) {
            bookmarksRegistration = resources.listenToBookmarks((ids, exception) -> {
                if (exception != null || ids == null) return;
                savedIds.clear();
                savedIds.addAll(ids);
                applyResourceFilters();
            });
        }
        if (myTutorRegistration == null) {
            myTutorRegistration = tutoring.listenToTutor(uid, (tutor, exception) -> {
                if (exception == null) myTutor.setValue(tutor);
            });
        }
        loadProfile(uid);
    }

    private void loadProfile(String uid) {
        if (profileRequested) return;
        profileRequested = true;
        users.getProfile(uid, (profile, exception) -> {
            if (exception != null || profile == null) {
                profileRequested = false;
                return;
            }
            myProfile.setValue(profile);
            applyResourceFilters();
        });
    }

    public void setQuery(String query) {
        this.query = query != null ? query.trim().toLowerCase(Locale.getDefault()) : "";
        applyResourceFilters();
        applyDoubtFilters();
        applyTutorFilters();
    }

    public void setType(String type) {
        this.type = type;
        applyResourceFilters();
    }

    public void setMyDepartmentOnly(boolean only) {
        myDepartmentOnly = only;
        applyResourceFilters();
    }

    public void setMySemesterOnly(boolean only) {
        mySemesterOnly = only;
        applyResourceFilters();
    }

    public void setSavedOnly(boolean only) {
        savedOnly = only;
        applyResourceFilters();
    }

    public boolean isMyDepartmentOnly() {
        return myDepartmentOnly;
    }

    public boolean isMySemesterOnly() {
        return mySemesterOnly;
    }

    public boolean isSavedOnly() {
        return savedOnly;
    }

    public void retry() {
        stopListening();
        profileRequested = false;
        startListening();
    }

    /** Creates or updates the signed-in user's tutor listing. */
    public void saveTutorListing(List<String> subjects, String bio) {
        UserProfile profile = myProfile.getValue();
        if (profile == null) {
            message.setValue("Your profile is still loading. Try again in a moment.");
            return;
        }
        TutorProfile existing = myTutor.getValue();
        TutorProfile tutor = new TutorProfile();
        tutor.setDisplayName(profile.getDisplayName());
        tutor.setPhotoUrl(profile.getPhotoUrl());
        tutor.setCollege(profile.getCollege());
        tutor.setDepartment(profile.getDepartment());
        tutor.setSemester(profile.getSemester());
        tutor.setSubjects(subjects);
        tutor.setBio(bio);
        if (existing != null) {
            tutor.setRatingSum(existing.getRatingSum());
            tutor.setRatingCount(existing.getRatingCount());
        }
        tutoring.saveTutorProfile(tutor, error -> {
            if (error != null) {
                message.setValue(error.getMessage() != null
                        ? error.getMessage() : "Could not save your tutor listing.");
                return;
            }
            message.setValue("Tutor listing saved");
        });
    }

    private void applyResourceFilters() {
        UserProfile profile = myProfile.getValue();
        List<AcademicResource> filtered = new ArrayList<>();
        for (AcademicResource resource : librarySource) {
            if (type != null && !type.equals(resource.getTypeOrDefault())) continue;
            if (myDepartmentOnly) {
                if (profile == null || profile.getDepartment() == null
                        || resource.getDepartment() == null
                        || !profile.getDepartment().equalsIgnoreCase(resource.getDepartment())) {
                    continue;
                }
            }
            if (mySemesterOnly) {
                if (profile == null || profile.getSemester() <= 0
                        || resource.getSemester() != profile.getSemester()) {
                    continue;
                }
            }
            if (savedOnly && !savedIds.contains(resource.getId())) continue;
            if (!query.isEmpty() && !resourceMatches(resource)) continue;
            filtered.add(resource);
        }
        library.setValue(filtered);
    }

    private boolean resourceMatches(AcademicResource resource) {
        if (contains(resource.getTitle()) || contains(resource.getSubject())
                || contains(resource.getDepartment()) || contains(resource.getUploaderName())) {
            return true;
        }
        if (resource.getTags() != null) {
            for (String tag : resource.getTags()) {
                if (contains(tag)) return true;
            }
        }
        return false;
    }

    private void applyDoubtFilters() {
        List<Doubt> filtered = new ArrayList<>();
        for (Doubt doubt : doubtSource) {
            if (!query.isEmpty()
                    && !contains(doubt.getTitle()) && !contains(doubt.getBody())
                    && !contains(doubt.getSubject()) && !contains(doubt.getDepartment())
                    && !contains(doubt.getAuthorName())) {
                continue;
            }
            filtered.add(doubt);
        }
        doubts.setValue(filtered);
    }

    private void applyTutorFilters() {
        List<TutorProfile> filtered = new ArrayList<>();
        for (TutorProfile tutor : tutorSource) {
            if (!query.isEmpty()
                    && !contains(tutor.getDisplayName()) && !contains(tutor.getDepartment())
                    && !contains(tutor.getBio()) && !contains(tutor.getSubjectsLabel())) {
                continue;
            }
            filtered.add(tutor);
        }
        tutors.setValue(filtered);
    }

    private boolean contains(String value) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }

    private void stopListening() {
        if (libraryRegistration != null) {
            libraryRegistration.remove();
            libraryRegistration = null;
        }
        if (doubtsRegistration != null) {
            doubtsRegistration.remove();
            doubtsRegistration = null;
        }
        if (tutorsRegistration != null) {
            tutorsRegistration.remove();
            tutorsRegistration = null;
        }
        if (bookmarksRegistration != null) {
            bookmarksRegistration.remove();
            bookmarksRegistration = null;
        }
        if (myTutorRegistration != null) {
            myTutorRegistration.remove();
            myTutorRegistration = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }
}
