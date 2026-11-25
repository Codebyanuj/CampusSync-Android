package com.campussync.app.ui.academics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.DialogRequestTutoringBinding;
import com.campussync.app.databinding.FragmentTutorProfileBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.model.TutorProfile;
import com.campussync.app.model.TutoringRequest;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Locale;

/** A tutor's profile: subjects, rating, reviews, request a session or open a chat. */
public class TutorProfileFragment extends Fragment {

    private FragmentTutorProfileBinding binding;
    private TutorProfileViewModel viewModel;
    private TutorReviewAdapter reviewAdapter;
    private String tutorId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TutorProfileViewModel.class);
        tutorId = getArguments() != null ? getArguments().getString("tutorId") : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTutorProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        reviewAdapter = new TutorReviewAdapter();
        binding.rvReviews.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvReviews.setAdapter(reviewAdapter);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.btnRequestTutoring.setOnClickListener(v -> showRequestDialog());
        binding.btnMessageTutor.setOnClickListener(v -> viewModel.openChat());

        viewModel.getTutor().observe(getViewLifecycleOwner(), this::bindTutor);
        viewModel.getReviews().observe(getViewLifecycleOwner(), this::bindReviews);
        viewModel.getBusy().observe(getViewLifecycleOwner(), busy ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(busy)
                        ? View.VISIBLE : View.GONE));
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearMessage();
        });
        viewModel.getConversationId().observe(getViewLifecycleOwner(), id -> {
            if (id == null) return;
            viewModel.clearConversation();
            Bundle args = new Bundle();
            args.putString("conversationId", id);
            Navigation.findNavController(requireView()).navigate(R.id.chatFragment, args);
        });

        viewModel.start(tutorId);
    }

    private void bindTutor(TutorProfile tutor) {
        if (tutor == null) return;
        binding.tvTutorName.setText(tutor.getDisplayName() != null
                ? tutor.getDisplayName() : "CampusSync tutor");
        binding.tvTutorSubjects.setText(tutor.getSubjectsLabel().isEmpty()
                ? getString(R.string.tutor_no_subjects) : tutor.getSubjectsLabel());

        StringBuilder details = new StringBuilder();
        if (tutor.getDepartment() != null && !tutor.getDepartment().isEmpty()) {
            details.append(tutor.getDepartment());
        }
        if (tutor.getSemester() > 0) {
            if (details.length() > 0) details.append(" · ");
            details.append(getString(R.string.profile_semester_value, tutor.getSemester()));
        }
        binding.tvTutorDetails.setText(details.toString());

        if (tutor.getRatingCount() > 0) {
            binding.tvTutorRating.setText(String.format(Locale.getDefault(), "★ %.1f · %d reviews",
                    tutor.getRatingAverage(), tutor.getRatingCount()));
        } else {
            binding.tvTutorRating.setText(R.string.tutor_new);
        }
        binding.tvTutorSessions.setText(getString(R.string.tutor_sessions,
                tutor.getCompletedSessions()));

        String bio = tutor.getBio();
        boolean hasBio = bio != null && !bio.trim().isEmpty();
        binding.tvTutorBio.setVisibility(hasBio ? View.VISIBLE : View.GONE);
        binding.tvTutorBio.setText(hasBio ? bio.trim() : "");

        Glide.with(this)
                .load(tutor.getPhotoUrl())
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.ivTutorAvatar);

        String uid = AuthManager.getInstance().getCurrentUserId();
        boolean isMe = uid != null && uid.equals(tutor.getId());
        binding.btnRequestTutoring.setEnabled(!isMe);
        binding.btnMessageTutor.setEnabled(!isMe);
        binding.tvOwnListing.setVisibility(isMe ? View.VISIBLE : View.GONE);
    }

    private void bindReviews(List<TutoringRequest> reviews) {
        reviewAdapter.submitList(reviews);
        boolean empty = reviews == null || reviews.isEmpty();
        binding.tvEmptyReviews.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvReviews.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showRequestDialog() {
        TutorProfile tutor = viewModel.getTutor().getValue();
        if (tutor == null || !isAdded()) return;
        DialogRequestTutoringBinding dialog = DialogRequestTutoringBinding.inflate(getLayoutInflater());
        if (tutor.getSubjects() != null && !tutor.getSubjects().isEmpty()) {
            dialog.etSubject.setText(tutor.getSubjects().get(0));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tutoring_request_title)
                .setView(dialog.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.tutoring_request_send, (d, which) -> {
                    String subject = dialog.etSubject.getText() != null
                            ? dialog.etSubject.getText().toString().trim() : "";
                    if (subject.isEmpty()) {
                        Snackbar.make(binding.getRoot(), R.string.error_subject_required,
                                Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    viewModel.requestSession(subject,
                            dialog.etMessage.getText() != null
                                    ? dialog.etMessage.getText().toString().trim() : "",
                            dialog.etTime.getText() != null
                                    ? dialog.etTime.getText().toString().trim() : "");
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
