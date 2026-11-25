package com.campussync.app.ui.academics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.ItemTutoringRequestBinding;
import com.campussync.app.model.TutoringRequest;

/**
 * One tutoring request. Buttons follow the viewer's role: a tutor accepts or declines a pending
 * request, the student cancels an open one and reviews a completed session once.
 */
public class TutoringRequestAdapter extends
        ListAdapter<TutoringRequest, TutoringRequestAdapter.RequestViewHolder> {

    public interface Actions {
        void onAccept(TutoringRequest request);

        void onDecline(TutoringRequest request);

        void onCancel(TutoringRequest request);

        void onReview(TutoringRequest request);
    }

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
                    return left.getStatus() != null && left.getStatus().equals(right.getStatus())
                            && left.getReviewRating() == right.getReviewRating()
                            && left.getMessage() != null
                            && left.getMessage().equals(right.getMessage());
                }
            };

    private final String myUid;
    private final Actions actions;

    public TutoringRequestAdapter(String myUid, Actions actions) {
        super(DIFF);
        this.myUid = myUid;
        this.actions = actions;
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RequestViewHolder(ItemTutoringRequestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        holder.bind(getItem(position), myUid, actions);
    }

    static class RequestViewHolder extends RecyclerView.ViewHolder {

        private final ItemTutoringRequestBinding binding;

        RequestViewHolder(ItemTutoringRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TutoringRequest request, String myUid, Actions actions) {
            boolean isTutor = myUid != null && myUid.equals(request.getTutorId());
            boolean isStudent = myUid != null && myUid.equals(request.getStudentId());

            binding.tvRequestSubject.setText(request.getSubject() != null
                    ? request.getSubject() : "");
            binding.chipRequestStatus.setText(
                    AcademicUi.statusLabel(binding.getRoot().getContext(), request));

            String names;
            if (isTutor) {
                names = binding.getRoot().getContext().getString(R.string.tutoring_from,
                        request.getStudentName() != null && !request.getStudentName().isEmpty()
                                ? request.getStudentName()
                                : binding.getRoot().getContext()
                                        .getString(R.string.anonymous_student));
            } else {
                names = binding.getRoot().getContext().getString(R.string.tutoring_with,
                        request.getTutorName() != null && !request.getTutorName().isEmpty()
                                ? request.getTutorName() : "tutor");
            }
            binding.tvRequestNames.setText(names);

            String message = request.getMessage();
            boolean hasMessage = message != null && !message.trim().isEmpty();
            binding.tvRequestMessage.setVisibility(hasMessage ? View.VISIBLE : View.GONE);
            binding.tvRequestMessage.setText(hasMessage ? message.trim() : "");

            StringBuilder meta = new StringBuilder(AcademicUi.dateTime(request.getCreatedAt()));
            if (request.getPreferredTime() != null && !request.getPreferredTime().isEmpty()) {
                meta.append(" · ").append(request.getPreferredTime());
            }
            if (request.isReviewed()) {
                meta.append(" · ").append(binding.getRoot().getContext()
                        .getString(R.string.tutoring_reviewed, request.getReviewRating()));
            }
            binding.tvRequestMeta.setText(meta.toString());

            boolean tutorPending = isTutor && request.isPending();
            binding.btnAccept.setVisibility(tutorPending ? View.VISIBLE : View.GONE);
            binding.btnDecline.setVisibility(tutorPending ? View.VISIBLE : View.GONE);

            boolean studentOpen = isStudent && request.isOpen();
            binding.btnCancel.setVisibility(studentOpen ? View.VISIBLE : View.GONE);

            boolean canReview = isStudent && request.isCompleted() && !request.isReviewed();
            binding.btnReview.setVisibility(canReview ? View.VISIBLE : View.GONE);

            binding.btnAccept.setOnClickListener(v -> {
                if (actions != null) actions.onAccept(request);
            });
            binding.btnDecline.setOnClickListener(v -> {
                if (actions != null) actions.onDecline(request);
            });
            binding.btnCancel.setOnClickListener(v -> {
                if (actions != null) actions.onCancel(request);
            });
            binding.btnReview.setOnClickListener(v -> {
                if (actions != null) actions.onReview(request);
            });
        }
    }
}
