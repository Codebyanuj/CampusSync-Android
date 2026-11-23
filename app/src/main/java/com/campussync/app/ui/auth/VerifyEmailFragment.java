package com.campussync.app.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentVerifyEmailBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.ui.MainActivity;
import com.google.android.material.snackbar.Snackbar;

/**
 * Email verification gate: signed-in users who have not clicked the verification link stay here
 * until they verify, resend the mail, or sign out.
 */
public class VerifyEmailFragment extends Fragment {

    private FragmentVerifyEmailBinding binding;
    private AuthViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVerifyEmailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String email = AuthManager.getInstance().getCurrentUser() != null
                ? AuthManager.getInstance().getCurrentUser().getEmail()
                : null;
        binding.tvEmail.setText(email != null ? email : "");

        binding.btnRefresh.setOnClickListener(v -> viewModel.refreshVerificationStatus());
        binding.btnResend.setOnClickListener(v -> viewModel.resendVerificationEmail());
        binding.btnSignOut.setOnClickListener(v -> viewModel.signOut());

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnRefresh.setEnabled(!busy);
            binding.btnResend.setEnabled(!busy);
        });

        viewModel.getStatus().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                viewModel.clearStatus();
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
            // MainActivity performs the actual navigation once the account is verified.
            binding.progressBar.setVisibility(View.GONE);
        });

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setEmailVerificationScreenVisible(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setEmailVerificationScreenVisible(false);
        }
        binding = null;
    }
}
