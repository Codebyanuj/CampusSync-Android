package com.campussync.app.ui.academics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.ItemDoubtBinding;
import com.campussync.app.model.Doubt;

/** Doubt cards: subject, title, a body preview, answers and the solved badge. */
public class DoubtAdapter extends ListAdapter<Doubt, DoubtAdapter.DoubtViewHolder> {

    public interface OnDoubtClick {
        void onDoubt(Doubt doubt);
    }

    private static final DiffUtil.ItemCallback<Doubt> DIFF =
            new DiffUtil.ItemCallback<Doubt>() {
                @Override
                public boolean areItemsTheSame(@NonNull Doubt left, @NonNull Doubt right) {
                    return left.getId() != null && left.getId().equals(right.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Doubt left, @NonNull Doubt right) {
                    return left.getAnswerCount() == right.getAnswerCount()
                            && left.isSolved() == right.isSolved()
                            && left.getTitle() != null && left.getTitle().equals(right.getTitle())
                            && left.getBody() != null && left.getBody().equals(right.getBody());
                }
            };

    private final OnDoubtClick listener;

    public DoubtAdapter(OnDoubtClick listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public DoubtViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new DoubtViewHolder(ItemDoubtBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull DoubtViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class DoubtViewHolder extends RecyclerView.ViewHolder {

        private final ItemDoubtBinding binding;

        DoubtViewHolder(ItemDoubtBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Doubt doubt, OnDoubtClick listener) {
            binding.tvDoubtTitle.setText(doubt.getTitle() != null ? doubt.getTitle() : "");
            binding.tvDoubtBody.setText(doubt.getBody() != null ? doubt.getBody() : "");

            String subject = doubt.getSubject() != null ? doubt.getSubject() : "";
            binding.chipDoubtSubject.setText(subject);
            binding.chipDoubtSubject.setVisibility(subject.isEmpty() ? View.GONE : View.VISIBLE);

            binding.chipDoubtSolved.setVisibility(doubt.isSolved() ? View.VISIBLE : View.GONE);

            String meta = binding.getRoot().getContext().getString(
                    R.string.doubt_answers, doubt.getAnswerCount());
            if (doubt.getAuthorName() != null && !doubt.getAuthorName().isEmpty()) {
                meta = meta + " · " + doubt.getAuthorName();
            }
            binding.tvDoubtMeta.setText(meta);

            binding.getRoot().setOnClickListener(view -> {
                if (listener != null) listener.onDoubt(doubt);
            });
        }
    }
}
