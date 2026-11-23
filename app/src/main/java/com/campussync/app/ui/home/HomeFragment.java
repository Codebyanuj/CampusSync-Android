package com.campussync.app.ui.home;

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
import com.campussync.app.databinding.FragmentHomeBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.ui.events.EventAdapter;

/**
 * Home tab: greeting plus the signed-in user's joined events.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private EventAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new EventAdapter(this::openEvent);
        binding.rvUpcomingEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUpcomingEvents.setAdapter(adapter);
        binding.swipeRefresh.setColorSchemeResources(R.color.primary);

        binding.swipeRefresh.setOnRefreshListener(viewModel::retry);

        if (getArguments() != null && "events".equals(getArguments().getString("screen"))) {
            Navigation.findNavController(view).navigate(R.id.eventsFragment);
        }

        observe();
        viewModel.startListening();
        viewModel.setGreeting(AuthManager.getInstance().getCurrentUser());
    }

    private void observe() {
        viewModel.getJoinedEvents().observe(getViewLifecycleOwner(), events -> {
            adapter.submitList(events);
            boolean empty = events == null || events.isEmpty();
            binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvUpcomingEvents.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.tvJoinedEventsTitle.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(loading)));

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                com.google.android.material.snackbar.Snackbar.make(
                        binding.getRoot(), message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getGreeting().observe(getViewLifecycleOwner(), greeting ->
                binding.tvWelcomeSub.setText(greeting));
    }

    private void openEvent(com.campussync.app.model.Event event) {
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
