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
import com.campussync.app.databinding.FragmentAdminReportsBinding;
import com.campussync.app.model.ContentReport;
import com.google.android.material.snackbar.Snackbar;

/**
 * Administrator review queue. Firestore rules make reports and anonymous attribution readable
 * by admins only, so this screen cannot be reached by a normal account.
 */
public class AdminReportsFragment extends Fragment {

    private FragmentAdminReportsBinding binding;
    private AdminReportsViewModel viewModel;
    private ReportAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AdminReportsViewModel.class);
        viewModel.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ReportAdapter(new ReportAdapter.Listener() {
            @Override
            public void onResolve(ContentReport report) {
                viewModel.resolve(report);
            }

            @Override
            public void onDismiss(ContentReport report) {
                viewModel.dismiss(report);
            }

            @Override
            public void onReveal(ContentReport report) {
                viewModel.reveal(report);
            }
        });
        binding.rvReports.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvReports.setAdapter(adapter);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        viewModel.getReports().observe(getViewLifecycleOwner(), reports -> {
            adapter.submitList(reports);
            binding.tvEmpty.setVisibility(reports == null || reports.isEmpty()
                    ? View.VISIBLE : View.GONE);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progress.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));
        viewModel.getMessageRes().observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            Snackbar.make(requireView(), res, Snackbar.LENGTH_SHORT).show();
            viewModel.clearMessage();
        });
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
