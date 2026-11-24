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
import com.campussync.app.databinding.ItemUserPickBinding;
import com.campussync.app.model.UserProfile;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** People list used for starting a direct chat, adding group members and building a new group. */
public class UserPickerAdapter extends ListAdapter<UserProfile, UserPickerAdapter.Holder> {

    public interface Listener {
        void onPick(UserProfile user);
        void onSelectionChanged(int selectedCount);
    }

    private static final DiffUtil.ItemCallback<UserProfile> DIFF =
            new DiffUtil.ItemCallback<UserProfile>() {
                @Override
                public boolean areItemsTheSame(@NonNull UserProfile oldItem,
                                               @NonNull UserProfile newItem) {
                    return Objects.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull UserProfile oldItem,
                                                  @NonNull UserProfile newItem) {
                    return Objects.equals(oldItem.getDisplayName(), newItem.getDisplayName())
                            && Objects.equals(oldItem.getPhotoUrl(), newItem.getPhotoUrl())
                            && Objects.equals(oldItem.getDepartment(), newItem.getDepartment());
                }
            };

    private final Listener listener;
    private final boolean multiSelect;
    private final Set<String> selected = new HashSet<>();

    public UserPickerAdapter(Listener listener, boolean multiSelect) {
        super(DIFF);
        this.listener = listener;
        this.multiSelect = multiSelect;
    }

    public Set<String> getSelectedIds() {
        return new HashSet<>(selected);
    }

    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemUserPickBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), multiSelect, selected, listener);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final ItemUserPickBinding binding;

        Holder(ItemUserPickBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final UserProfile user, final boolean multiSelect,
                  final Set<String> selected, final Listener listener) {
            binding.tvName.setText(user.getDisplayName() != null
                    ? user.getDisplayName() : "CampusSync user");

            String subtitle = buildSubtitle(user);
            binding.tvSubtitle.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            binding.tvSubtitle.setText(subtitle);

            Glide.with(binding.ivAvatar)
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivAvatar);

            binding.cbSelect.setVisibility(multiSelect ? View.VISIBLE : View.GONE);
            if (multiSelect) {
                binding.cbSelect.setChecked(user.getId() != null
                        && selected.contains(user.getId()));
            }

            binding.getRoot().setOnClickListener(v -> {
                if (multiSelect) {
                    if (user.getId() == null) return;
                    boolean nowSelected;
                    if (selected.contains(user.getId())) {
                        selected.remove(user.getId());
                        nowSelected = false;
                    } else {
                        selected.add(user.getId());
                        nowSelected = true;
                    }
                    binding.cbSelect.setChecked(nowSelected);
                    listener.onSelectionChanged(selected.size());
                } else {
                    listener.onPick(user);
                }
            });
        }

        private String buildSubtitle(UserProfile user) {
            StringBuilder builder = new StringBuilder();
            if (user.getDepartment() != null && !user.getDepartment().trim().isEmpty()) {
                builder.append(user.getDepartment().trim());
            }
            if (user.getCourse() != null && !user.getCourse().trim().isEmpty()) {
                if (builder.length() > 0) builder.append(" · ");
                builder.append(user.getCourse().trim());
            }
            if (user.getDesignation() != null && !user.getDesignation().trim().isEmpty()) {
                if (builder.length() > 0) builder.append(" · ");
                builder.append(user.getDesignation().trim());
            }
            return builder.toString();
        }
    }
}
