package com.campussync.app.ui.messages;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemMessageBinding;
import com.campussync.app.model.Message;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * Chat bubbles. Outgoing messages are aligned to the end with the primary container colour and
 * carry a read receipt; incoming messages show the sender name in group chats.
 */
public class MessageAdapter extends ListAdapter<Message, MessageAdapter.Holder> {

    public interface Listener {
        void onAttachment(Message message);
        void onLongPress(Message message, View anchor);
    }

    private static final DiffUtil.ItemCallback<Message> DIFF =
            new DiffUtil.ItemCallback<Message>() {
                @Override
                public boolean areItemsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                    return Objects.equals(oldItem.getText(), newItem.getText())
                            && Objects.equals(oldItem.getAttachmentUrl(), newItem.getAttachmentUrl())
                            && Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt())
                            && oldItem.getReadBy() != null && newItem.getReadBy() != null
                            && oldItem.getReadBy().size() == newItem.getReadBy().size();
                }
            };

    private final String currentUserId;
    private final boolean groupConversation;
    private final Listener listener;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    public MessageAdapter(String currentUserId, boolean groupConversation, Listener listener) {
        super(DIFF);
        this.currentUserId = currentUserId;
        this.groupConversation = groupConversation;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), currentUserId, groupConversation, listener, timeFormat);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final ItemMessageBinding binding;

        Holder(ItemMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Message message, final String uid, final boolean group,
                  final Listener listener, final SimpleDateFormat timeFormat) {
            boolean own = uid != null && uid.equals(message.getSenderId());

            binding.rowMessage.setGravity(own ? Gravity.END : Gravity.START);
            binding.layoutBubble.setBackgroundResource(own
                    ? R.drawable.bg_bubble_own : R.drawable.bg_bubble_other);

            boolean showSender = group && !own && message.getSenderName() != null;
            binding.tvSender.setVisibility(showSender ? View.VISIBLE : View.GONE);
            if (showSender) binding.tvSender.setText(message.getSenderName());

            boolean hasText = message.hasText();
            binding.tvText.setVisibility(hasText ? View.VISIBLE : View.GONE);
            binding.tvText.setText(hasText ? message.getText() : "");

            boolean isImage = message.isImage() && message.getAttachmentUrl() != null;
            binding.ivImage.setVisibility(isImage ? View.VISIBLE : View.GONE);
            if (isImage) {
                Glide.with(binding.ivImage)
                        .load(message.getAttachmentUrl())
                        .placeholder(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(binding.ivImage);
            }

            boolean isFile = message.isDocument() && message.getAttachmentUrl() != null;
            binding.layoutFile.setVisibility(isFile ? View.VISIBLE : View.GONE);
            if (isFile) {
                binding.tvFileName.setText(message.getAttachmentName() != null
                        ? message.getAttachmentName() : "");
                binding.tvFileSize.setText(formatSize(message.getAttachmentSize()));
            }

            Date created = message.getCreatedAt();
            binding.tvTime.setText(created != null ? timeFormat.format(created) : "…");

            if (isImage || isFile) {
                View.OnClickListener open = v -> listener.onAttachment(message);
                binding.ivImage.setOnClickListener(open);
                binding.layoutFile.setOnClickListener(open);
            } else {
                binding.ivImage.setOnClickListener(null);
                binding.layoutFile.setOnClickListener(null);
            }

            boolean showReceipt = own;
            binding.layoutReceipt.setVisibility(showReceipt ? View.VISIBLE : View.GONE);
            if (showReceipt) {
                boolean read = message.getReadBy() != null && message.getReadBy().size() > 1;
                binding.layoutReceipt.setGravity(Gravity.END);
                binding.tvReceipt.setText(read
                        ? R.string.read_receipt_read : R.string.read_receipt_delivered);
            }

            binding.layoutBubble.setOnLongClickListener(v -> {
                listener.onLongPress(message, binding.layoutBubble);
                return true;
            });
        }

        private String formatSize(long bytes) {
            if (bytes <= 0) return "";
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
