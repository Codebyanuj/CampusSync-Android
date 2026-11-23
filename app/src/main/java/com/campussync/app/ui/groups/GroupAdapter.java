package com.campussync.app.ui.groups;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemGroupCardBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.model.Group;

/**
 * RecyclerView adapter for group cards.
 */
public class GroupAdapter extends ListAdapter<Group, GroupAdapter.GroupViewHolder> {

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
    }

    private static final DiffUtil.ItemCallback<Group> DIFF_CALLBACK = new DiffUtil.ItemCallback<Group>() {
        @Override
        public boolean areItemsTheSame(@NonNull Group oldItem, @NonNull Group newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Group oldItem, @NonNull Group newItem) {
            return java.util.Objects.equals(oldItem.getName(), newItem.getName())
                    && java.util.Objects.equals(oldItem.getDescription(), newItem.getDescription())
                    && java.util.Objects.equals(oldItem.getMemberIds(), newItem.getMemberIds());
        }
    };

    private final OnGroupClickListener listener;

    public GroupAdapter(OnGroupClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGroupCardBinding binding = ItemGroupCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new GroupViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {

        private final ItemGroupCardBinding binding;

        GroupViewHolder(ItemGroupCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Group group, OnGroupClickListener listener) {
            binding.tvGroupName.setText(group.getName() != null ? group.getName() : "");
            binding.tvGroupDescription.setText(group.getDescription() != null ? group.getDescription() : "");
            binding.tvMemberCount.setText(group.getMemberCount() == 1
                    ? "1 member"
                    : group.getMemberCount() + " members");

            String uid = AuthManager.getInstance().getCurrentUserId();
            boolean member = uid != null && group.getMemberIds() != null
                    && group.getMemberIds().contains(uid);
            binding.chipMember.setVisibility(member
                    ? android.view.View.VISIBLE
                    : android.view.View.GONE);

            if (group.getImageUrl() != null && !group.getImageUrl().isEmpty()) {
                Glide.with(binding.ivGroupImage)
                        .load(group.getImageUrl())
                        .placeholder(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(binding.ivGroupImage);
            } else {
                binding.ivGroupImage.setImageResource(R.drawable.ic_groups);
            }

            if (listener != null) {
                binding.getRoot().setOnClickListener(v -> listener.onGroupClick(group));
            }
        }
    }
}
