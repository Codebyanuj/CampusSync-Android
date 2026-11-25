package com.campussync.app.ui.academics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.campussync.app.firebase.KarmaRepository;
import com.campussync.app.model.KarmaEntry;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Balance and history, both derived from the read-only karma ledger. */
public class KarmaViewModel extends ViewModel {

    private final KarmaRepository repository = KarmaRepository.getInstance();

    private final MutableLiveData<Long> balance = new MutableLiveData<>(0L);
    private final MutableLiveData<List<KarmaEntry>> entries =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);

    private ListenerRegistration registration;

    public LiveData<Long> getBalance() {
        return balance;
    }

    public LiveData<List<KarmaEntry>> getEntries() {
        return entries;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public void start() {
        if (registration != null) return;
        String uid = KarmaRepository.currentUidOrNull();
        if (uid == null) {
            loading.setValue(false);
            return;
        }
        registration = repository.listenToKarma(uid, (sum, list, exception) -> {
            loading.setValue(false);
            if (exception != null) {
                error.setValue("Unable to load your karma. Check your connection.");
                return;
            }
            balance.setValue(sum);
            entries.setValue(list);
        });
    }

    public void retry() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        start();
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
