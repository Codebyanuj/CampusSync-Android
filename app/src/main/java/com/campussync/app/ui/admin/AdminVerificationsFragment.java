package com.campussync.app.ui.admin;

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

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentAdminVerificationsBinding;
import com.campussync.app.model.VerificationRequest;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/**
 * Administrator queue: approve or reject student/faculty verification requests.
 *
 * Access is enforced by Firestore rules through the {@code admins} collection; hiding the menu
 * entry on the profile is only a convenience.
 */
public class AdminVerificationsFragment extends Fragment {

    private FragmentAdminVerificationsBinding binding;
    private AdminVerificationsViewModel viewModel;
    private VerificationRequestAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AdminVerificationsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminVerificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        adapter = new VerificationRequestAdapter(this::confirmReview);
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRequests.setAdapter(adapter);

        viewModel.getRequests().observe(getViewLifecycleOwner(), requests -> {
            adapter.submitList(requests);
            boolean empty = requests == null || requests.isEmpty();
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvRequests.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getReviewing().observe(getViewLifecycleOwner(), reviewing ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(reviewing)
                        ? View.VISIBLE : View.GONE));

        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
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

    private void confirmReview(VerificationRequest request, boolean approve) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(approve ? R.string.verify_approve_title : R.string.verify_reject_title)
                .setMessage(approve
                        ? getString(R.string.verify_approve_message, request.getName())
                        : getString(R.string.verify_reject_message, request.getName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(approve ? R.string.verify_approve : R.string.verify_reject,
                        (dialog, which) -> viewModel.review(request, approve, ""))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
