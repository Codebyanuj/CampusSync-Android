package com.campussync.app.ui.academics;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.databinding.ItemKarmaBinding;
import com.campussync.app.model.KarmaEntry;

/** Karma history rows: what earned it, when, and how much. */
public class KarmaAdapter extends ListAdapter<KarmaEntry, KarmaAdapter.KarmaViewHolder> {

    private static final DiffUtil.ItemCallback<KarmaEntry> DIFF =
            new DiffUtil.ItemCallback<KarmaEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull KarmaEntry left,
                                               @NonNull KarmaEntry right) {
                    return left.getId() != null && left.getId().equals(right.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull KarmaEntry left,
                                                  @NonNull KarmaEntry right) {
                    return left.getAmount() == right.getAmount()
                            && left.getNote() != null && left.getNote().equals(right.getNote());
                }
            };

    public KarmaAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public KarmaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new KarmaViewHolder(ItemKarmaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull KarmaViewHolder holder, int position) {
        KarmaEntry entry = getItem(position);
        holder.binding.tvKarmaNote.setText(entry.getNote() != null
                && !entry.getNote().isEmpty() ? entry.getNote() : entry.getSourceType());
        holder.binding.tvKarmaAmount.setText("+" + entry.getAmount());
        holder.binding.tvKarmaDate.setText(AcademicUi.dateTime(entry.getCreatedAt()));
    }

    static class KarmaViewHolder extends RecyclerView.ViewHolder {

        private final ItemKarmaBinding binding;

        KarmaViewHolder(ItemKarmaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
