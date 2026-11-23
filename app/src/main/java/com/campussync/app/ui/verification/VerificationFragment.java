package com.campussync.app.ui.verification;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentVerificationBinding;
import com.campussync.app.model.UserProfile;
import com.campussync.app.model.VerificationRequest;
import com.campussync.app.util.ProfileValidator;
import com.google.android.material.snackbar.Snackbar;

/**
 * Verification centre: shows the current status and lets the user submit a student or faculty
 * verification request with their college ID number.
 */
public class VerificationFragment extends Fragment {

    private FragmentVerificationBinding binding;
    private VerificationViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(VerificationViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVerificationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.btnSubmit.setOnClickListener(v -> submit());

        viewModel.getProfile().observe(getViewLifecycleOwner(), this::bindStatus);
        viewModel.getRequest().observe(getViewLifecycleOwner(), request -> {
            bindStatus(viewModel.getProfile().getValue());
            if (request != null && !request.isPending()) {
                bindReviewNote(request);
            }
        });

        viewModel.getSubmitting().observe(getViewLifecycleOwner(), submitting -> {
            boolean busy = Boolean.TRUE.equals(submitting);
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnSubmit.setEnabled(!busy);
        });

        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                viewModel.clearMessage();
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.startListening();
    }

    private void bindStatus(UserProfile profile) {
        if (profile == null || binding == null) return;

        binding.tvRole.setText(profile.isFaculty()
                ? R.string.verification_faculty_hint
                : R.string.verification_student_hint);
        binding.tvAccount.setText(profile.getEmail() != null ? profile.getEmail() : "");

        boolean verified = profile.isVerified();
        boolean pending = profile.isVerificationPending();

        binding.cardStatus.setVisibility(View.VISIBLE);
        if (verified) {
            binding.tvStatus.setText(R.string.verification_status_verified);
            binding.tvStatusDetail.setText(profile.isFaculty()
                    ? R.string.verification_status_verified_faculty
                    : R.string.verification_status_verified_student);
            binding.tvStatus.setBackgroundResource(R.drawable.bg_badge_verified);
        } else if (pending) {
            binding.tvStatus.setText(R.string.verification_status_pending);
            binding.tvStatusDetail.setText(R.string.verification_status_pending_detail);
            binding.tvStatus.setBackgroundResource(R.drawable.bg_badge_pending);
        } else {
            binding.tvStatus.setText(R.string.verification_status_unverified);
            binding.tvStatusDetail.setText(R.string.verification_status_unverified_detail);
            binding.tvStatus.setBackgroundResource(R.drawable.bg_badge_neutral);
        }

        int formVisibility = (verified || pending) ? View.GONE : View.VISIBLE;
        binding.formContainer.setVisibility(formVisibility);
        binding.tvRole.setVisibility(formVisibility);
    }

    private void bindReviewNote(VerificationRequest request) {
        String note = request.getReviewNote();
        if (note == null || note.trim().isEmpty()) {
            binding.tvReviewNote.setVisibility(View.GONE);
            return;
        }
        binding.tvReviewNote.setVisibility(View.VISIBLE);
        binding.tvReviewNote.setText(getString(R.string.verification_review_note, note.trim()));
    }

    private void submit() {
        String idNumber = binding.etIdNumber.getText() != null
                ? binding.etIdNumber.getText().toString().trim() : "";
        String note = binding.etNote.getText() != null
                ? binding.etNote.getText().toString().trim() : "";

        binding.tilIdNumber.setError(null);
        int error = ProfileValidator.idNumberError(idNumber);
        if (error != 0) {
            binding.tilIdNumber.setError(getString(error));
            return;
        }
        viewModel.submit(idNumber, note);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
