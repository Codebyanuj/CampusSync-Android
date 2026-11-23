package com.campussync.app.ui.events;

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
import com.campussync.app.databinding.FragmentEventsBinding;
import com.campussync.app.model.Event;

/**
 * Events tab. Renders a live list of events with pull-to-refresh and explicit
 * loading / empty / error states.
 */
public class EventsFragment extends Fragment {

    private FragmentEventsBinding binding;
    private EventsViewModel viewModel;
    private EventAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(EventsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEventsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new EventAdapter(this::openDetail);
        binding.rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvEvents.setAdapter(adapter);
        binding.swipeRefresh.setColorSchemeResources(R.color.primary);
        binding.swipeRefresh.setOnRefreshListener(viewModel::refresh);

        binding.btnRetry.setOnClickListener(v -> viewModel.retry());
        binding.btnRetryError.setOnClickListener(v -> viewModel.retry());

        observe();
        viewModel.startListening();
    }

    private void observe() {
        viewModel.getEvents().observe(getViewLifecycleOwner(), list -> adapter.submitList(list));

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            binding.loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.swipeRefresh.setRefreshing(false);
        });

        viewModel.getEmpty().observe(getViewLifecycleOwner(), empty -> {
            boolean isEmpty = Boolean.TRUE.equals(empty);
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.rvEvents.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            boolean hasError = message != null && !message.isEmpty();
            binding.errorBanner.setVisibility(hasError ? View.VISIBLE : View.GONE);
            if (hasError) binding.tvError.setText(message);
        });
    }

    private void openDetail(Event event) {
        if (event.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("eventId", event.getId());
        Navigation.findNavController(requireView()).navigate(R.id.eventDetailFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
