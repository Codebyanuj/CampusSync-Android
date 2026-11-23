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

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentGroupsBinding;
import com.campussync.app.model.Group;

/**
 * Groups tab: list of campus groups with join state.
 */
public class GroupsFragment extends Fragment {

    private FragmentGroupsBinding binding;
    private GroupsViewModel viewModel;
    private GroupAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(GroupsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGroupsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new GroupAdapter(this::openDetail);
        binding.rvGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvGroups.setAdapter(adapter);
        binding.swipeRefresh.setColorSchemeResources(R.color.primary);
        binding.swipeRefresh.setOnRefreshListener(viewModel::refresh);

        binding.btnRetry.setOnClickListener(v -> viewModel.retry());
        binding.btnRetryError.setOnClickListener(v -> viewModel.retry());

        viewModel.getGroups().observe(getViewLifecycleOwner(), list -> adapter.submitList(list));

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            binding.loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.swipeRefresh.setRefreshing(false);
        });

        viewModel.getEmpty().observe(getViewLifecycleOwner(), empty -> {
            boolean isEmpty = Boolean.TRUE.equals(empty);
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.rvGroups.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            boolean hasError = message != null && !message.isEmpty();
            binding.errorBanner.setVisibility(hasError ? View.VISIBLE : View.GONE);
            if (hasError) binding.tvError.setText(message);
        });

        viewModel.startListening();
    }

    private void openDetail(Group group) {
        if (group.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("groupId", group.getId());
        Navigation.findNavController(requireView()).navigate(R.id.groupDetailFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
