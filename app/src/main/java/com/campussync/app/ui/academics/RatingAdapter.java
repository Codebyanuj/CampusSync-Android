package com.campussync.app.ui.academics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.databinding.ItemRatingBinding;
import com.campussync.app.model.ResourceRating;

/** Written feedback under a resource. */
public class RatingAdapter extends ListAdapter<ResourceRating, RatingAdapter.RatingViewHolder> {

    private static final DiffUtil.ItemCallback<ResourceRating> DIFF =
            new DiffUtil.ItemCallback<ResourceRating>() {
                @Override
                public boolean areItemsTheSame(@NonNull ResourceRating left,
                                               @NonNull ResourceRating right) {
                    return left.getUid() != null && left.getUid().equals(right.getUid());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ResourceRating left,
                                                  @NonNull ResourceRating right) {
                    return left.getStars() == right.getStars()
                            && left.getFeedback() != null
                            && left.getFeedback().equals(right.getFeedback());
                }
            };

    public RatingAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public RatingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RatingViewHolder(ItemRatingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RatingViewHolder holder, int position) {
        ResourceRating rating = getItem(position);
        holder.binding.rbRating.setRating(rating.getStars());
        holder.binding.tvRaterName.setText(rating.getRaterName() != null
                && !rating.getRaterName().isEmpty()
                ? rating.getRaterName()
                : holder.binding.getRoot().getContext()
                        .getString(com.campussync.app.R.string.anonymous_student));
        String feedback = rating.getFeedback();
        boolean hasFeedback = feedback != null && !feedback.trim().isEmpty();
        holder.binding.tvFeedback.setVisibility(hasFeedback ? View.VISIBLE : View.GONE);
        holder.binding.tvFeedback.setText(hasFeedback ? feedback.trim() : "");
        holder.binding.tvRatingDate.setText(AcademicUi.dateTime(rating.getCreatedAt()));
    }

    static class RatingViewHolder extends RecyclerView.ViewHolder {

        private final ItemRatingBinding binding;

        RatingViewHolder(ItemRatingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
