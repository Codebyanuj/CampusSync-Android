package com.campussync.app.ui.messages;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemConversationBinding;
import com.campussync.app.model.Conversation;
import com.campussync.app.util.TimeAgo;

import java.util.Objects;

/** Renders the conversation list with last message, timestamp and unread badge. */
public class ConversationAdapter extends ListAdapter<Conversation, ConversationAdapter.Holder> {

    public interface Listener {
        void onOpen(Conversation conversation);
    }

    private static final DiffUtil.ItemCallback<Conversation> DIFF =
            new DiffUtil.ItemCallback<Conversation>() {
                @Override
                public boolean areItemsTheSame(@NonNull Conversation oldItem,
                                               @NonNull Conversation newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Conversation oldItem,
                                                  @NonNull Conversation newItem) {
                    return Objects.equals(oldItem.getDisplayTitle(), newItem.getDisplayTitle())
                            && Objects.equals(oldItem.getLastMessage(), newItem.getLastMessage())
                            && Objects.equals(oldItem.getActivityAt(), newItem.getActivityAt())
                            && oldItem.getUnreadCount() == newItem.getUnreadCount()
                            && oldItem.isMuted() == newItem.isMuted()
                            && Objects.equals(oldItem.getDisplayPhotoUrl(),
                            newItem.getDisplayPhotoUrl());
                }
            };

    private final Listener listener;

    public ConversationAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final ItemConversationBinding binding;

        Holder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Conversation conversation, final Listener listener) {
            binding.tvName.setText(conversation.getDisplayTitle() != null
                    ? conversation.getDisplayTitle() : "");

            boolean hasPreview = conversation.getLastMessage() != null
                    && !conversation.getLastMessage().isEmpty();
            binding.tvLastMessage.setVisibility(hasPreview ? View.VISIBLE : View.GONE);
            binding.tvLastMessage.setText(hasPreview ? conversation.getLastMessage() : "");

            boolean hasTime = conversation.getActivityAt() != null;
            binding.tvTime.setVisibility(hasTime ? View.VISIBLE : View.GONE);
            if (hasTime) binding.tvTime.setText(TimeAgo.format(conversation.getActivityAt()));

            long unread = conversation.getUnreadCount();
            binding.tvUnread.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            binding.tvUnread.setText(unread > 99 ? "99+" : String.valueOf(unread));
            binding.ivMuted.setVisibility(conversation.isMuted() ? View.VISIBLE : View.GONE);

            if (conversation.isGroup()) {
                Glide.with(binding.ivAvatar)
                        .load(conversation.getDisplayPhotoUrl())
                        .placeholder(R.drawable.ic_group)
                        .error(R.drawable.ic_group)
                        .circleCrop()
                        .into(binding.ivAvatar);
            } else {
                Glide.with(binding.ivAvatar)
                        .load(conversation.getDisplayPhotoUrl())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(binding.ivAvatar);
            }

            binding.getRoot().setOnClickListener(v -> listener.onOpen(conversation));
        }
    }
}
