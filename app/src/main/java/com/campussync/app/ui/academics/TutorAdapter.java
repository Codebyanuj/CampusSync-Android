package com.campussync.app.ui.academics;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemTutorBinding;
import com.campussync.app.model.TutorProfile;

import java.util.Locale;

/** Tutor directory cards: identity, subjects, department and rating. */
public class TutorAdapter extends ListAdapter<TutorProfile, TutorAdapter.TutorViewHolder> {

    public interface OnTutorClick {
        void onTutor(TutorProfile tutor);
    }

    private static final DiffUtil.ItemCallback<TutorProfile> DIFF =
            new DiffUtil.ItemCallback<TutorProfile>() {
                @Override
                public boolean areItemsTheSame(@NonNull TutorProfile left,
                                               @NonNull TutorProfile right) {
                    return left.getId() != null && left.getId().equals(right.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull TutorProfile left,
                                                  @NonNull TutorProfile right) {
                    return left.getRatingSum() == right.getRatingSum()
                            && left.getRatingCount() == right.getRatingCount()
                            && left.getSubjectsLabel().equals(right.getSubjectsLabel())
                            && left.getBio() != null && left.getBio().equals(right.getBio());
                }
            };

    private final OnTutorClick listener;

    public TutorAdapter(OnTutorClick listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public TutorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TutorViewHolder(ItemTutorBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TutorViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class TutorViewHolder extends RecyclerView.ViewHolder {

        private final ItemTutorBinding binding;

        TutorViewHolder(ItemTutorBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TutorProfile tutor, OnTutorClick listener) {
            binding.tvTutorName.setText(tutor.getDisplayName() != null
                    ? tutor.getDisplayName() : "CampusSync tutor");
            binding.tvTutorSubjects.setText(tutor.getSubjectsLabel().isEmpty()
                    ? binding.getRoot().getContext().getString(R.string.tutor_no_subjects)
                    : tutor.getSubjectsLabel());

            String department = tutor.getDepartment() != null ? tutor.getDepartment() : "";
            binding.tvTutorDepartment.setText(department);

            if (tutor.getRatingCount() > 0) {
                binding.tvTutorRating.setText(String.format(Locale.getDefault(), "★ %.1f (%d)",
                        tutor.getRatingAverage(), tutor.getRatingCount()));
            } else {
                binding.tvTutorRating.setText(R.string.tutor_new);
            }

            Glide.with(binding.ivTutorAvatar)
                    .load(tutor.getPhotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivTutorAvatar);

            binding.getRoot().setOnClickListener(view -> {
                if (listener != null) listener.onTutor(tutor);
            });
        }
    }
}
