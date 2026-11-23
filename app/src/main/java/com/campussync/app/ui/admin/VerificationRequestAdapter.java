package com.campussync.app.ui.admin;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.ItemVerificationRequestBinding;
import com.campussync.app.model.UserProfile;
import com.campussync.app.model.VerificationRequest;

/**
 * Pending verification requests, each with approve / reject actions.
 */
public class VerificationRequestAdapter
        extends ListAdapter<VerificationRequest, VerificationRequestAdapter.ViewHolder> {

    public interface OnReviewListener {
        void onReview(VerificationRequest request, boolean approve);
    }

    private static final DiffUtil.ItemCallback<VerificationRequest> DIFF =
            new DiffUtil.ItemCallback<VerificationRequest>() {
                @Override
                public boolean areItemsTheSame(@NonNull VerificationRequest oldItem,
                                               @NonNull VerificationRequest newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull VerificationRequest oldItem,
                                                  @NonNull VerificationRequest newItem) {
                    return oldItem.getStatus() != null
                            && oldItem.getStatus().equals(newItem.getStatus());
                }
            };

    private final OnReviewListener listener;

    public VerificationRequestAdapter(OnReviewListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVerificationRequestBinding binding = ItemVerificationRequestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemVerificationRequestBinding binding;

        ViewHolder(ItemVerificationRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(VerificationRequest request) {
            binding.tvName.setText(request.getName() != null ? request.getName() : "Unnamed");
            binding.tvRole.setText(UserProfile.ROLE_FACULTY.equals(request.getRole())
                    ? R.string.role_faculty : R.string.role_student);
            binding.tvCollege.setText(request.getCollege() != null ? request.getCollege() : "");
            binding.tvDepartment.setText(request.getDepartment() != null
                    ? request.getDepartment() : "");
            binding.tvIdNumber.setText(request.getIdNumber() != null
                    ? request.getIdNumber() : "");

            boolean hasNote = request.getNote() != null && !request.getNote().trim().isEmpty();
            binding.tvNote.setVisibility(hasNote ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.tvNote.setText(hasNote ? request.getNote().trim() : "");

            binding.btnApprove.setOnClickListener(v -> {
                if (listener != null) listener.onReview(request, true);
            });
            binding.btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReview(request, false);
            });
        }
    }
}
