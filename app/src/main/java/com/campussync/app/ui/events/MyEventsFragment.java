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

import com.campussync.app.databinding.FragmentMyEventsBinding;
import com.campussync.app.model.Event;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** "My events": hosted events and registrations, with access to tickets from the detail page. */
public class MyEventsFragment extends Fragment {

    private FragmentMyEventsBinding binding;
    private MyEventsViewModel viewModel;
    private EventAdapter hostedAdapter;
    private EventAdapter registeredAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MyEventsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMyEventsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        hostedAdapter = new EventAdapter(this::openDetail);
        registeredAdapter = new EventAdapter(this::openDetail);
        binding.rvHosted.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvHosted.setAdapter(hostedAdapter);
        binding.rvRegistered.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRegistered.setAdapter(registeredAdapter);

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getHosted().observe(getViewLifecycleOwner(), list -> {
            hostedAdapter.submitList(list);
            binding.tvEmptyHosted.setVisibility(isEmpty(list) ? View.VISIBLE : View.GONE);
        });

        viewModel.getRegistered().observe(getViewLifecycleOwner(), list -> {
            registeredAdapter.submitList(list);
            binding.tvEmptyRegistered.setVisibility(isEmpty(list) ? View.VISIBLE : View.GONE);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });

        viewModel.start();
    }

    private static boolean isEmpty(List<Event> list) {
        return list == null || list.isEmpty();
    }

    private void openDetail(Event event) {
        if (event.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("eventId", event.getId());
        Navigation.findNavController(requireView()).navigate(
                com.campussync.app.R.id.eventDetailFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
