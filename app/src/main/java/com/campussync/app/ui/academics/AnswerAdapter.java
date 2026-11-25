package com.campussync.app.ui.academics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.ItemAnswerBinding;
import com.campussync.app.model.DoubtAnswer;

/** Peer answers. The asker sees an Accept button until the doubt is solved. */
public class AnswerAdapter extends ListAdapter<DoubtAnswer, AnswerAdapter.AnswerViewHolder> {

    public interface OnAcceptClick {
        void onAccept(DoubtAnswer answer);
    }

    private static final DiffUtil.ItemCallback<DoubtAnswer> DIFF =
            new DiffUtil.ItemCallback<DoubtAnswer>() {
                @Override
                public boolean areItemsTheSame(@NonNull DoubtAnswer left,
                                               @NonNull DoubtAnswer right) {
                    return left.getId() != null && left.getId().equals(right.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DoubtAnswer left,
                                                  @NonNull DoubtAnswer right) {
                    return left.isAccepted() == right.isAccepted()
                            && left.getBody() != null && left.getBody().equals(right.getBody());
                }
            };

    private final OnAcceptClick listener;
    private boolean canAccept;

    public AnswerAdapter(OnAcceptClick listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** True only for the student who asked, while the doubt is still open. */
    public void setCanAccept(boolean canAccept) {
        if (this.canAccept == canAccept) return;
        this.canAccept = canAccept;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AnswerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AnswerViewHolder(ItemAnswerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AnswerViewHolder holder, int position) {
        holder.bind(getItem(position), canAccept, listener);
    }

    static class AnswerViewHolder extends RecyclerView.ViewHolder {

        private final ItemAnswerBinding binding;

        AnswerViewHolder(ItemAnswerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(DoubtAnswer answer, boolean canAccept, OnAcceptClick listener) {
            binding.tvAnswerBody.setText(answer.getBody() != null ? answer.getBody() : "");
            binding.tvAnswerAuthor.setText(answer.getAuthorName() != null
                    && !answer.getAuthorName().isEmpty()
                    ? answer.getAuthorName()
                    : binding.getRoot().getContext().getString(R.string.anonymous_student));
            binding.chipAccepted.setVisibility(answer.isAccepted() ? View.VISIBLE : View.GONE);

            boolean showButton = canAccept && !answer.isAccepted();
            binding.btnAccept.setVisibility(showButton ? View.VISIBLE : View.GONE);
            binding.btnAccept.setOnClickListener(view -> {
                if (listener != null) listener.onAccept(answer);
            });
        }
    }
}
