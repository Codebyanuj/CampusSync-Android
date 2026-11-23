package com.campussync.app.ui.groups;

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
import com.campussync.app.databinding.FragmentGroupDetailBinding;
import com.campussync.app.model.Group;
import com.campussync.app.ui.events.AttendeeAdapter;
import com.google.android.material.snackbar.Snackbar;

/**
 * Group details screen with member list and join / leave action.
 */
public class GroupDetailFragment extends Fragment {

    private FragmentGroupDetailBinding binding;
    private GroupDetailViewModel viewModel;
    private AttendeeAdapter memberAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(GroupDetailViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGroupDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        memberAdapter = new AttendeeAdapter();
        binding.rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMembers.setAdapter(memberAdapter);

        binding.btnJoin.setOnClickListener(v -> viewModel.toggleMembership());

        observe();

        String groupId = getArguments() != null ? getArguments().getString("groupId") : null;
        if (groupId == null) {
            Snackbar.make(binding.getRoot(), "Missing group id", Snackbar.LENGTH_LONG).show();
            binding.btnJoin.setEnabled(false);
            return;
        }
        viewModel.load(groupId);
    }

    private void observe() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));

        viewModel.getGroup().observe(getViewLifecycleOwner(), this::bindGroup);

        viewModel.getMember().observe(getViewLifecycleOwner(), member ->
                binding.btnJoin.setText(Boolean.TRUE.equals(member)
                        ? R.string.btn_leave_group
                        : R.string.btn_join_group));

        viewModel.getMemberNames().observe(getViewLifecycleOwner(), names ->
                memberAdapter.submit(names));

        viewModel.getActionInProgress().observe(getViewLifecycleOwner(), inProgress ->
                binding.btnJoin.setEnabled(!Boolean.TRUE.equals(inProgress)));

        viewModel.getActionMessage().observe(getViewLifecycleOwner(), message -> {
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
    }

    private void bindGroup(Group group) {
        if (group == null) return;
        binding.tvGroupName.setText(group.getName() != null ? group.getName() : "");
        binding.tvGroupDescription.setText(group.getDescription() != null ? group.getDescription() : "");
        if (group.getImageUrl() != null && !group.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(group.getImageUrl())
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(binding.ivGroupImage);
        } else {
            binding.ivGroupImage.setImageResource(R.drawable.ic_groups);
        }
        binding.btnJoin.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
