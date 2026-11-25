package com.campussync.app.firebase;

import android.util.Log;

import com.campussync.app.model.KarmaEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the karma ledger ({@code karmaAwards}).
 *
 * <p>Karma is <em>derived</em>, never a mutable balance: the award documents are the only record,
 * their ids are derived from the contribution that earned them, and only the trusted backend can
 * write them. A duplicate reward is therefore impossible (the document id already exists) and a
 * client cannot award itself karma (the rules refuse every client write). The balance shown in
 * the UI is the sum of the rows this account is allowed to read.
 */
public class KarmaRepository {

    private static final String TAG = "KarmaRepository";

    public static final String COLLECTION_KARMA_AWARDS = "karmaAwards";

    /**
     * One page of ledger rows: enough for an academic year of contributions. The screen says so
     * when the page is full, because the balance is the sum of these rows.
     */
    public static final int HISTORY_LIMIT = 200;

    private static volatile KarmaRepository instance;

    private final FirebaseFirestore db;

    private KarmaRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static KarmaRepository getInstance() {
        if (instance == null) {
            synchronized (KarmaRepository.class) {
                if (instance == null) instance = new KarmaRepository();
            }
        }
        return instance;
    }

    public static String currentUidOrNull() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    /** Live balance and history for one account; rules deliver only that account's rows. */
    public ListenerRegistration listenToKarma(String uid, final KarmaListener listener) {
        if (uid == null) {
            if (listener != null) listener.onKarma(0, new ArrayList<>(), null);
            return null;
        }
        return db.collection(COLLECTION_KARMA_AWARDS)
                .whereEqualTo("recipientId", uid)
                .limit(HISTORY_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "listenToKarma: failure", error);
                        if (listener != null) listener.onKarma(0, null, error);
                        return;
                    }
                    List<KarmaEntry> entries = new ArrayList<>();
                    long total = 0L;
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            KarmaEntry entry = document.toObject(KarmaEntry.class);
                            if (entry == null) continue;
                            entry.setId(document.getId());
                            entries.add(entry);
                            total += entry.getAmount();
                        }
                    }
                    Collections.sort(entries, new Comparator<KarmaEntry>() {
                        @Override
                        public int compare(KarmaEntry left, KarmaEntry right) {
                            long a = left.getCreatedAt() != null
                                    ? left.getCreatedAt().getTime() : 0L;
                            long b = right.getCreatedAt() != null
                                    ? right.getCreatedAt().getTime() : 0L;
                            if (a == b) return 0;
                            return a > b ? -1 : 1;
                        }
                    });
                    if (listener != null) listener.onKarma(total, entries, null);
                });
    }

    public interface KarmaListener {
        void onKarma(long balance, List<KarmaEntry> entries, Exception error);
    }
}
