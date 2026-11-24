package com.campussync.app.ui.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemPostBinding;
import com.campussync.app.model.Post;
import com.campussync.app.util.TimeAgo;

import java.util.Objects;

/** Renders feed posts, including ghost posts which simply show an anonymous byline. */
public class PostAdapter extends ListAdapter<Post, PostAdapter.PostViewHolder> {

    public interface Listener {
        void onLike(Post post);
        void onComment(Post post);
        void onShare(Post post);
        void onBookmark(Post post);
        void onMore(Post post, View anchor);
        void onAuthor(Post post);
    }

    private static final DiffUtil.ItemCallback<Post> DIFF = new DiffUtil.ItemCallback<Post>() {
        @Override
        public boolean areItemsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
            return Objects.equals(oldItem.getContent(), newItem.getContent())
                    && Objects.equals(oldItem.getImageUrls(), newItem.getImageUrls())
                    && Objects.equals(oldItem.getAuthorName(), newItem.getAuthorName())
                    && Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt())
                    && oldItem.getLikeCount() == newItem.getLikeCount()
                    && oldItem.getCommentCount() == newItem.getCommentCount()
                    && oldItem.isLikedByMe() == newItem.isLikedByMe()
                    && oldItem.isBookmarkedByMe() == newItem.isBookmarkedByMe()
                    && oldItem.isEdited() == newItem.isEdited()
                    && oldItem.isOwnedByMe() == newItem.isOwnedByMe();
        }
    };

    private final Listener listener;

    public PostAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPostBinding binding = ItemPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PostViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {

        private final ItemPostBinding binding;

        PostViewHolder(ItemPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Post post, final Listener listener) {
            boolean anonymous = post.isAnonymous();
            binding.tvAuthor.setText(anonymous
                    ? binding.getRoot().getContext().getString(R.string.anonymous_user)
                    : (post.getAuthorName() != null ? post.getAuthorName() : ""));

            String time = TimeAgo.format(post.getCreatedAt());
            if (post.isEdited()) time = time + " · " + binding.getRoot().getContext()
                    .getString(R.string.action_edit).toLowerCase();
            binding.tvTime.setText(time);

            binding.ivGhost.setVisibility(anonymous ? View.VISIBLE : View.GONE);
            if (anonymous) {
                binding.ivAvatar.setPadding(10, 10, 10, 10);
                binding.ivAvatar.setImageResource(R.drawable.ic_ghost);
            } else {
                binding.ivAvatar.setPadding(0, 0, 0, 0);
                Glide.with(binding.ivAvatar)
                        .load(post.getAuthorPhotoUrl())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(binding.ivAvatar);
            }

            boolean hasText = post.hasText();
            binding.tvContent.setVisibility(hasText ? View.VISIBLE : View.GONE);
            binding.tvContent.setText(hasText ? post.getContent() : "");

            String image = post.getFirstImage();
            if (image != null && !image.isEmpty()) {
                binding.ivPostImage.setVisibility(View.VISIBLE);
                Glide.with(binding.ivPostImage)
                        .load(image)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(binding.ivPostImage);
            } else {
                binding.ivPostImage.setVisibility(View.GONE);
            }

            int extra = post.getImageUrls() != null ? post.getImageUrls().size() - 1 : 0;
            binding.tvImageCount.setVisibility(extra > 0 ? View.VISIBLE : View.GONE);
            if (extra > 0) {
                binding.tvImageCount.setText("+" + extra + " images");
            }

            binding.tvLikeCount.setText(String.valueOf(post.getLikeCount()));
            binding.tvCommentCount.setText(String.valueOf(post.getCommentCount()));

            int inactive = ContextCompat.getColor(binding.getRoot().getContext(),
                    R.color.on_surface_variant);
            int active = ContextCompat.getColor(binding.getRoot().getContext(), R.color.primary);
            binding.btnLike.setColorFilter(post.isLikedByMe() ? active : inactive);
            binding.btnBookmark.setColorFilter(post.isBookmarkedByMe() ? active : inactive);

            binding.btnLike.setOnClickListener(v -> listener.onLike(post));
            binding.btnComment.setOnClickListener(v -> listener.onComment(post));
            binding.btnShare.setOnClickListener(v -> listener.onShare(post));
            binding.btnBookmark.setOnClickListener(v -> listener.onBookmark(post));
            binding.btnMore.setOnClickListener(v -> listener.onMore(post, binding.btnMore));
            binding.getRoot().setOnClickListener(v -> listener.onComment(post));
            binding.ivAvatar.setOnClickListener(v -> {
                if (!post.isAnonymous()) listener.onAuthor(post);
            });
            binding.tvAuthor.setOnClickListener(v -> {
                if (!post.isAnonymous()) listener.onAuthor(post);
            });
        }
    }
}
