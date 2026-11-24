package com.campussync.app.ui.events;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.ItemRegistrationBinding;
import com.campussync.app.model.EventRegistration;

import java.util.Objects;

/**
 * Participant rows for the organizer's participant list: registration state and attendance.
 * Only organizers/administrators ever see this list (enforced by the security rules).
 */
public class RegistrationAdapter
        extends ListAdapter<EventRegistration, RegistrationAdapter.RegistrationViewHolder> {

    private static final DiffUtil.ItemCallback<EventRegistration> DIFF =
            new DiffUtil.ItemCallback<EventRegistration>() {
                @Override
                public boolean areItemsTheSame(@NonNull EventRegistration oldItem,
                                               @NonNull EventRegistration newItem) {
                    return Objects.equals(oldItem.getUid(), newItem.getUid());
                }

                @Override
                public boolean areContentsTheSame(@NonNull EventRegistration oldItem,
                                                  @NonNull EventRegistration newItem) {
                    return Objects.equals(oldItem.getName(), newItem.getName())
                            && Objects.equals(oldItem.getCollege(), newItem.getCollege())
                            && Objects.equals(oldItem.getStatus(), newItem.getStatus())
                            && oldItem.isAttended() == newItem.isAttended()
                            && Objects.equals(oldItem.getCheckedInAt(), newItem.getCheckedInAt());
                }
            };

    public RegistrationAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public RegistrationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RegistrationViewHolder(ItemRegistrationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RegistrationViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class RegistrationViewHolder extends RecyclerView.ViewHolder {

        private final ItemRegistrationBinding binding;

        RegistrationViewHolder(ItemRegistrationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(EventRegistration registration) {
            binding.tvName.setText(registration.getName() != null
                    ? registration.getName() : "Participant");

            StringBuilder detail = new StringBuilder();
            if (registration.getCollege() != null) detail.append(registration.getCollege());
            if (registration.isAttended()) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(binding.getRoot().getContext().getString(R.string.status_checked_in));
                if (registration.getCheckedInAt() != null) {
                    detail.append(' ').append(EventUi.formatDateTime(registration.getCheckedInAt()));
                }
            } else if (!registration.isRegistered()) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(binding.getRoot().getContext().getString(R.string.status_cancelled));
            }
            binding.tvDetail.setText(detail);

            int label;
            if (registration.isAttended()) {
                label = R.string.status_checked_in;
            } else if (!registration.isRegistered()) {
                label = R.string.status_cancelled;
            } else {
                label = R.string.status_registered;
            }
            binding.chipStatus.setText(label);
            binding.chipStatus.setVisibility(View.VISIBLE);
        }
    }
}
