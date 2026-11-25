package com.campussync.app.ui.academics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.ItemReviewBinding;
import com.campussync.app.model.TutoringRequest;

/** Feedback left after a tutoring session. */
public class TutorReviewAdapter
        extends ListAdapter<TutoringRequest, TutorReviewAdapter.ReviewViewHolder> {

    private static final DiffUtil.ItemCallback<TutoringRequest> DIFF =
            new DiffUtil.ItemCallback<TutoringRequest>() {
                @Override
                public boolean areItemsTheSame(@NonNull TutoringRequest left,
                                               @NonNull TutoringRequest right) {
                    return left.getId() != null && left.getId().equals(right.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull TutoringRequest left,
                                                  @NonNull TutoringRequest right) {
                    return left.getReviewRating() == right.getReviewRating()
                            && left.getReviewComment() != null
                            && left.getReviewComment().equals(right.getReviewComment());
                }
            };

    public TutorReviewAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ReviewViewHolder(ItemReviewBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        TutoringRequest review = getItem(position);
        holder.binding.rbReviewStars.setRating(review.getReviewRating());
        holder.binding.tvReviewStudent.setText(review.getStudentName() != null
                && !review.getStudentName().isEmpty()
                ? review.getStudentName()
                : holder.binding.getRoot().getContext()
                        .getString(R.string.anonymous_student));
        String comment = review.getReviewComment();
        boolean hasComment = comment != null && !comment.trim().isEmpty();
        holder.binding.tvReviewComment.setVisibility(hasComment ? View.VISIBLE : View.GONE);
        holder.binding.tvReviewComment.setText(hasComment ? comment.trim() : "");
        holder.binding.tvReviewDate.setText(AcademicUi.dateTime(review.getCompletedAt()));
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {

        private final ItemReviewBinding binding;

        ReviewViewHolder(ItemReviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
