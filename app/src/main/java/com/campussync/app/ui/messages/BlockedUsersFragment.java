package com.campussync.app.ui.messages;

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

import com.campussync.app.databinding.FragmentBlockedUsersBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

/** Accounts blocked from messaging, with a one-tap unblock. */
public class BlockedUsersFragment extends Fragment {

    private FragmentBlockedUsersBinding binding;
    private BlockedUsersViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BlockedUsersViewModel.class);
        viewModel.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBlockedUsersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        BlockedUserAdapter adapter = new BlockedUserAdapter(viewModel::unblock);
        binding.rvBlocked.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBlocked.setAdapter(adapter);

        viewModel.getBlocked().observe(getViewLifecycleOwner(), blocked -> {
            adapter.submitList(blocked != null ? blocked : new ArrayList<>());
            boolean empty = blocked == null || blocked.isEmpty();
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        viewModel.getMessageRes().observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            Snackbar.make(binding.getRoot(), res, Snackbar.LENGTH_SHORT).show();
            viewModel.clearMessage();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
