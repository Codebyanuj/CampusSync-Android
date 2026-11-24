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
import com.campussync.app.databinding.ItemMemberBinding;
import com.campussync.app.model.ConversationMember;

import java.util.Objects;

/** Member list of a group chat with the admin-only action menu. */
public class MemberAdapter extends ListAdapter<ConversationMember, MemberAdapter.Holder> {

    public interface Listener {
        void onMore(ConversationMember member, View anchor);
    }

    private static final DiffUtil.ItemCallback<ConversationMember> DIFF =
            new DiffUtil.ItemCallback<ConversationMember>() {
                @Override
                public boolean areItemsTheSame(@NonNull ConversationMember oldItem,
                                               @NonNull ConversationMember newItem) {
                    return Objects.equals(oldItem.getUid(), newItem.getUid());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ConversationMember oldItem,
                                                  @NonNull ConversationMember newItem) {
                    return Objects.equals(oldItem.getName(), newItem.getName())
                            && Objects.equals(oldItem.getRole(), newItem.getRole())
                            && Objects.equals(oldItem.getPhotoUrl(), newItem.getPhotoUrl());
                }
            };

    private final Listener listener;
    private final String currentUserId;
    private boolean canManage;

    public MemberAdapter(String currentUserId, Listener listener) {
        super(DIFF);
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    public void setCanManage(boolean canManage) {
        this.canManage = canManage;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMemberBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), currentUserId, canManage, listener);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final ItemMemberBinding binding;

        Holder(ItemMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final ConversationMember member, final String uid, final boolean canManage,
                  final Listener listener) {
            String name = member.getName() != null ? member.getName() : "CampusSync user";
            if (uid != null && uid.equals(member.getUid())) {
                name = name + " (" + binding.getRoot().getContext()
                        .getString(R.string.you) + ")";
            }
            binding.tvMemberName.setText(name);
            binding.tvMemberRole.setText(member.isAdmin()
                    ? R.string.group_admin : R.string.group_member);

            Glide.with(binding.ivAvatar)
                    .load(member.getPhotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivAvatar);

            boolean showMenu = canManage && uid != null && !uid.equals(member.getUid());
            binding.btnMemberMore.setVisibility(showMenu ? View.VISIBLE : View.GONE);
            binding.btnMemberMore.setOnClickListener(v -> listener.onMore(member,
                    binding.btnMemberMore));
        }
    }
}
