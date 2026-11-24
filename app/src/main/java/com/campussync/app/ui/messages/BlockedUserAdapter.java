package com.campussync.app.ui.messages;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemBlockedUserBinding;
import com.campussync.app.firebase.MessagingRepository;

import java.util.Objects;

/** Accounts the signed-in user has blocked. */
public class BlockedUserAdapter
        extends ListAdapter<MessagingRepository.BlockedUser, BlockedUserAdapter.Holder> {

    public interface Listener {
        void onUnblock(MessagingRepository.BlockedUser user);
    }

    private static final DiffUtil.ItemCallback<MessagingRepository.BlockedUser> DIFF =
            new DiffUtil.ItemCallback<MessagingRepository.BlockedUser>() {
                @Override
                public boolean areItemsTheSame(@NonNull MessagingRepository.BlockedUser oldItem,
                                               @NonNull MessagingRepository.BlockedUser newItem) {
                    return Objects.equals(oldItem.uid, newItem.uid);
                }

                @Override
                public boolean areContentsTheSame(@NonNull MessagingRepository.BlockedUser oldItem,
                                                  @NonNull MessagingRepository.BlockedUser newItem) {
                    return Objects.equals(oldItem.name, newItem.name);
                }
            };

    private final Listener listener;

    public BlockedUserAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemBlockedUserBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final ItemBlockedUserBinding binding;

        Holder(ItemBlockedUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final MessagingRepository.BlockedUser user, final Listener listener) {
            binding.tvName.setText(user.name);
            Glide.with(binding.ivAvatar)
                    .load((String) null)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivAvatar);
            binding.btnUnblock.setOnClickListener(v -> listener.onUnblock(user));
        }
    }
}
