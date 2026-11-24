package com.campussync.app.ui.boards;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.ItemDiscussionBinding;
import com.campussync.app.model.Discussion;
import com.campussync.app.util.TimeAgo;

import java.util.Objects;

/** Discussion rows with upvote / downvote arrows coloured by the current user's vote. */
public class DiscussionAdapter extends ListAdapter<Discussion, DiscussionAdapter.DiscussionViewHolder> {

    public interface Listener {
        void onDiscussion(Discussion discussion);
        void onVote(Discussion discussion, long value);
    }

    private static final DiffUtil.ItemCallback<Discussion> DIFF =
            new DiffUtil.ItemCallback<Discussion>() {
                @Override
                public boolean areItemsTheSame(@NonNull Discussion oldItem,
                                               @NonNull Discussion newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Discussion oldItem,
                                                  @NonNull Discussion newItem) {
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                            && Objects.equals(oldItem.getBody(), newItem.getBody())
                            && Objects.equals(oldItem.getAuthorName(), newItem.getAuthorName())
                            && oldItem.getUpvoteCount() == newItem.getUpvoteCount()
                            && oldItem.getDownvoteCount() == newItem.getDownvoteCount()
                            && oldItem.getCommentCount() == newItem.getCommentCount()
                            && oldItem.getMyVote() == newItem.getMyVote();
                }
            };

    private final Listener listener;

    public DiscussionAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public DiscussionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDiscussionBinding binding = ItemDiscussionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DiscussionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DiscussionViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class DiscussionViewHolder extends RecyclerView.ViewHolder {

        private final ItemDiscussionBinding binding;

        DiscussionViewHolder(ItemDiscussionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Discussion discussion, final Listener listener) {
            binding.tvTitle.setText(discussion.getTitle());
            binding.tvScore.setText(String.valueOf(discussion.getScore()));

            String author = discussion.isAnonymous()
                    ? binding.getRoot().getContext().getString(R.string.anonymous_user)
                    : discussion.getAuthorName();
            String meta = (author != null ? author : "")
                    + " · " + TimeAgo.format(discussion.getCreatedAt())
                    + " · " + binding.getRoot().getContext().getString(
                    R.string.comments_short, discussion.getCommentCount());
            binding.tvMeta.setText(meta);

            boolean hasBody = discussion.getBody() != null && !discussion.getBody().trim().isEmpty();
            binding.tvBodyPreview.setVisibility(hasBody ? View.VISIBLE : View.GONE);
            binding.tvBodyPreview.setText(hasBody ? discussion.getBody() : "");

            int active = ContextCompat.getColor(binding.getRoot().getContext(), R.color.primary);
            int inactive = ContextCompat.getColor(binding.getRoot().getContext(),
                    R.color.on_surface_variant);
            binding.btnUpvote.setColorFilter(discussion.getMyVote() == 1 ? active : inactive);
            binding.btnDownvote.setColorFilter(discussion.getMyVote() == -1 ? active : inactive);

            binding.btnUpvote.setOnClickListener(v -> listener.onVote(discussion, 1L));
            binding.btnDownvote.setOnClickListener(v -> listener.onVote(discussion, -1L));
            binding.getRoot().setOnClickListener(v -> listener.onDiscussion(discussion));
        }
    }
}
