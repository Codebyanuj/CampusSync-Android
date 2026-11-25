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

import com.campussync.app.R;
import com.campussync.app.databinding.DialogReviewSessionBinding;
import com.campussync.app.databinding.FragmentTutoringRequestsBinding;
import com.campussync.app.model.TutoringRequest;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** Every tutoring request the signed-in user is part of, with the action their role allows. */
public class TutoringRequestsFragment extends Fragment
        implements TutoringRequestAdapter.Actions {

    private FragmentTutoringRequestsBinding binding;
    private TutoringRequestsViewModel viewModel;
    private TutoringRequestAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TutoringRequestsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTutoringRequestsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new TutoringRequestAdapter(viewModel.getMyUid(), this);
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRequests.setAdapter(adapter);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        viewModel.getRequests().observe(getViewLifecycleOwner(), this::bindRequests);
        viewModel.getBusy().observe(getViewLifecycleOwner(), busy ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(busy)
                        ? View.VISIBLE : View.GONE));
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        });

        viewModel.start();
    }

    private void bindRequests(List<TutoringRequest> requests) {
        adapter.submitList(requests);
        boolean empty = requests == null || requests.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvRequests.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onAccept(TutoringRequest request) {
        viewModel.respond(request, true);
    }

    @Override
    public void onDecline(TutoringRequest request) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tutoring_decline_title)
                .setMessage(R.string.tutoring_decline_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.tutoring_decline, (dialog, which) ->
                        viewModel.respond(request, false))
                .show();
    }

    @Override
    public void onCancel(TutoringRequest request) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tutoring_cancel_title)
                .setMessage(R.string.tutoring_cancel_message)
                .setNegativeButton(R.string.btn_keep_event, null)
                .setPositiveButton(R.string.tutoring_cancel, (dialog, which) ->
                        viewModel.cancel(request))
                .show();
    }

    @Override
    public void onReview(TutoringRequest request) {
        if (!isAdded()) return;
        DialogReviewSessionBinding dialog = DialogReviewSessionBinding.inflate(getLayoutInflater());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tutoring_review_title)
                .setView(dialog.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.tutoring_review_send, (d, which) ->
                        viewModel.completeAndReview(request, Math.round(dialog.rbStars.getRating()),
                                dialog.etComment.getText() != null
                                        ? dialog.etComment.getText().toString().trim() : ""))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
