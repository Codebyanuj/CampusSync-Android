package com.campussync.app.ui.events;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.EventRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.Event;
import com.campussync.app.model.UserProfile;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Event radar: live feed plus the search/category/date/college filters.
 *
 * <p>The Firestore query only orders by start date so it never needs a composite index; the
 * filters run over the loaded window on the client.
 */
public class EventsViewModel extends ViewModel {

    public static final int DATE_ANY = 0;
    public static final int DATE_TODAY = 1;
    public static final int DATE_WEEK = 2;
    public static final int DATE_MONTH = 3;

    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long MONTH_MS = 31L * 24 * 60 * 60 * 1000;

    private final EventRepository repository = EventRepository.getInstance();
    private final UserRepository userRepository = UserRepository.getInstance();

    private final MutableLiveData<List<Event>> events = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> empty = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> college = new MutableLiveData<>();

    private final List<Event> source = new ArrayList<>();
    private String query = "";
    private String category;
    private int dateFilter = DATE_ANY;
    private boolean myCollegeOnly;
    private ListenerRegistration registration;

    public LiveData<List<Event>> getEvents() {
        return events;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getEmpty() {
        return empty;
    }

    public LiveData<String> getError() {
        return error;
    }

    /** The signed-in student's college, used by the "My college" filter. */
    public LiveData<String> getCollege() {
        return college;
    }

    public boolean isMyCollegeOnly() {
        return myCollegeOnly;
    }

    public void startListening() {
        if (registration != null) return;
        loading.setValue(true);
        error.setValue(null);
        registration = repository.listenToRadar((list, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load events. Check your connection and retry.");
                return;
            }
            source.clear();
            if (list != null) source.addAll(list);
            applyFilters();
        });
        loadCollege();
    }

    private void loadCollege() {
        String uid = EventRepository.currentUidOrNull();
        if (uid == null) return;
        userRepository.getProfile(uid, new UserRepository.ProfileListener() {
            @Override
            public void onProfile(UserProfile profile, Exception exception) {
                if (profile == null) return;
                college.setValue(profile.getCollege());
                if (myCollegeOnly) applyFilters();
            }
        });
    }

    public void setQuery(String query) {
        this.query = query != null ? query : "";
        applyFilters();
    }

    public void setCategory(String category) {
        this.category = category;
        applyFilters();
    }

    public void setDateFilter(int filter) {
        this.dateFilter = filter;
        applyFilters();
    }

    public void setMyCollegeOnly(boolean only) {
        this.myCollegeOnly = only;
        applyFilters();
    }

    public void retry() {
        stopListening();
        startListening();
    }

    public void refresh() {
        error.setValue(null);
        retry();
    }

    private void applyFilters() {
        long now = System.currentTimeMillis();
        long startOfToday = startOfToday();
        String needle = query.trim().toLowerCase(Locale.getDefault());
        String myCollege = college.getValue();
        List<Event> filtered = new ArrayList<>();

        for (Event event : source) {
            if (event.isCancelled()) continue;
            long end = event.getEndDate() != null ? event.getEndDate().getTime()
                    : event.getStartDate() != null ? event.getStartDate().getTime() : 0L;
            if (end > 0 && end < startOfToday) continue;

            if (category != null && !category.equals(event.getCategoryOrDefault())) continue;

            if (dateFilter != DATE_ANY) {
                long start = event.getStartDate() != null ? event.getStartDate().getTime() : 0L;
                if (start <= 0) continue;
                if (dateFilter == DATE_TODAY) {
                    if (!sameDay(start, now)) continue;
                } else if (dateFilter == DATE_WEEK) {
                    if (start < startOfToday || start > now + WEEK_MS) continue;
                } else if (dateFilter == DATE_MONTH) {
                    if (start < startOfToday || start > now + MONTH_MS) continue;
                }
            }

            if (myCollegeOnly) {
                if (myCollege == null || event.getCollege() == null
                        || !myCollege.equalsIgnoreCase(event.getCollege())) {
                    continue;
                }
            }

            if (!needle.isEmpty() && !matches(event, needle)) continue;
            filtered.add(event);
        }

        Collections.sort(filtered, new Comparator<Event>() {
            @Override
            public int compare(Event left, Event right) {
                long a = left.getStartDate() != null ? left.getStartDate().getTime() : Long.MAX_VALUE;
                long b = right.getStartDate() != null ? right.getStartDate().getTime() : Long.MAX_VALUE;
                if (a == b) return 0;
                return a < b ? -1 : 1;
            }
        });

        events.setValue(filtered);
        empty.setValue(filtered.isEmpty());
    }

    private boolean matches(Event event, String needle) {
        return contains(event.getTitle(), needle)
                || contains(event.getDescription(), needle)
                || contains(event.getLocation(), needle)
                || contains(event.getOrganizerName(), needle)
                || contains(event.getCollege(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(needle);
    }

    private static boolean sameDay(long first, long second) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(first);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(second);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static long startOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void stopListening() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }
}
