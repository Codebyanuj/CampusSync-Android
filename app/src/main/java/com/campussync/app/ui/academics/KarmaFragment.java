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
import com.campussync.app.databinding.FragmentKarmaBinding;
import com.campussync.app.firebase.KarmaRepository;
import com.campussync.app.model.KarmaEntry;

import java.util.List;

/** Karma balance and the contributions that earned it. */
public class KarmaFragment extends Fragment {

    private FragmentKarmaBinding binding;
    private KarmaViewModel viewModel;
    private KarmaAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(KarmaViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentKarmaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new KarmaAdapter();
        binding.rvKarma.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvKarma.setAdapter(adapter);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.btnRetry.setOnClickListener(v -> viewModel.retry());

        viewModel.getBalance().observe(getViewLifecycleOwner(), balance ->
                binding.tvBalance.setText(String.valueOf(balance != null ? balance : 0L)));

        viewModel.getEntries().observe(getViewLifecycleOwner(), this::bindEntries);

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            boolean hasError = message != null && !message.isEmpty();
            binding.tvError.setVisibility(hasError ? View.VISIBLE : View.GONE);
            if (hasError) binding.tvError.setText(message);
        });

        viewModel.start();
    }

    private void bindEntries(List<KarmaEntry> entries) {
        adapter.submitList(entries);
        boolean empty = entries == null || entries.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvKarma.setVisibility(empty ? View.GONE : View.VISIBLE);
        // The balance is the sum of the loaded page, so say when the ledger is longer.
        binding.tvHistoryCap.setVisibility(entries != null
                && entries.size() >= KarmaRepository.HISTORY_LIMIT ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
