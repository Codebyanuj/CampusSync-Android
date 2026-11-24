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
import com.campussync.app.databinding.FragmentEventParticipantsBinding;
import com.campussync.app.model.EventRegistration;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * Organizer-only participant list with registration and attendance state. Firestore rules only
 * let the organizer (or an administrator) read the registrations subcollection.
 */
public class EventParticipantsFragment extends Fragment {

    private FragmentEventParticipantsBinding binding;
    private EventParticipantsViewModel viewModel;
    private RegistrationAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(EventParticipantsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEventParticipantsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        adapter = new RegistrationAdapter();
        binding.rvParticipants.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvParticipants.setAdapter(adapter);

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getRegistrations().observe(getViewLifecycleOwner(), this::render);

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });

        String eventId = getArguments() != null ? getArguments().getString("eventId") : null;
        viewModel.load(eventId);
    }

    private void render(List<EventRegistration> registrations) {
        adapter.submitList(registrations);
        int total = registrations != null ? registrations.size() : 0;
        int checkedIn = 0;
        if (registrations != null) {
            for (EventRegistration registration : registrations) {
                if (registration.isAttended()) checkedIn++;
            }
        }
        binding.tvSummary.setText(getString(R.string.participants_summary, total, checkedIn));
        boolean empty = total == 0;
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvParticipants.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
