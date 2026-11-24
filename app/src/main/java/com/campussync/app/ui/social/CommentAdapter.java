package com.campussync.app.ui.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemCommentBinding;
import com.campussync.app.model.Comment;
import com.campussync.app.util.TimeAgo;

import java.util.Objects;

/**
 * Comments and one level of replies, shared by post comments and board discussions.
 * Indentation is applied in code so the same row layout serves both.
 */
public class CommentAdapter extends ListAdapter<Comment, CommentAdapter.CommentViewHolder> {

    public interface Listener {
        void onReply(Comment comment);
        void onDelete(Comment comment);
    }

    private static final DiffUtil.ItemCallback<Comment> DIFF = new DiffUtil.ItemCallback<Comment>() {
        @Override
        public boolean areItemsTheSame(@NonNull Comment oldItem, @NonNull Comment newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Comment oldItem, @NonNull Comment newItem) {
            return Objects.equals(oldItem.getContent(), newItem.getContent())
                    && Objects.equals(oldItem.getAuthorName(), newItem.getAuthorName())
                    && Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt())
                    && oldItem.isOwnedByMe() == newItem.isOwnedByMe();
        }
    };

    private final Listener listener;

    public CommentAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCommentBinding binding = ItemCommentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CommentViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {

        private final ItemCommentBinding binding;

        CommentViewHolder(ItemCommentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Comment comment, final Listener listener) {
            int base = dp(binding.getRoot(), 8);
            int indent = comment.isReply() ? dp(binding.getRoot(), 36) : 0;
            binding.rootComment.setPaddingRelative(base + indent,
                    binding.rootComment.getPaddingTop(), base, 0);

            if (comment.isAnonymous()) {
                binding.ivAvatar.setPadding(6, 6, 6, 6);
                binding.ivAvatar.setImageResource(R.drawable.ic_ghost);
            } else {
                binding.ivAvatar.setPadding(0, 0, 0, 0);
                Glide.with(binding.ivAvatar)
                        .load(comment.getAuthorPhotoUrl())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(binding.ivAvatar);
            }

            binding.tvAuthor.setText(comment.isAnonymous()
                    ? binding.getRoot().getContext().getString(R.string.anonymous_user)
                    : comment.getAuthorName());
            binding.tvTime.setText(TimeAgo.format(comment.getCreatedAt()));
            binding.ivGhost.setVisibility(comment.isAnonymous() ? View.VISIBLE : View.GONE);
            binding.tvContent.setText(comment.getContent());

            // One level of replies keeps the thread readable.
            binding.btnReply.setVisibility(comment.isReply() ? View.GONE : View.VISIBLE);
            binding.btnReply.setOnClickListener(v -> listener.onReply(comment));

            binding.btnDeleteComment.setVisibility(comment.isOwnedByMe()
                    ? View.VISIBLE : View.GONE);
            binding.btnDeleteComment.setOnClickListener(v -> listener.onDelete(comment));
        }

        private static int dp(View view, int value) {
            return Math.round(value * view.getResources().getDisplayMetrics().density);
        }
    }
}
