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

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.FragmentEventDetailBinding;
import com.campussync.app.model.Event;
import com.google.android.material.snackbar.Snackbar;

/**
 * Event details screen: image, schedule, description, attendee list and RSVP toggle.
 */
public class EventDetailFragment extends Fragment {

    private FragmentEventDetailBinding binding;
    private EventDetailViewModel viewModel;
    private AttendeeAdapter attendeeAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(EventDetailViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEventDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        attendeeAdapter = new AttendeeAdapter();
        binding.rvAttendees.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAttendees.setAdapter(attendeeAdapter);

        binding.btnJoin.setOnClickListener(v -> viewModel.toggleJoin());

        observe();

        String eventId = getArguments() != null ? getArguments().getString("eventId") : null;
        if (eventId == null) {
            binding.btnJoin.setEnabled(false);
            Snackbar.make(binding.getRoot(), "Missing event id", Snackbar.LENGTH_LONG).show();
            return;
        }
        viewModel.load(eventId);
    }

    private void observe() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));

        viewModel.getEvent().observe(getViewLifecycleOwner(), this::bindEvent);

        viewModel.getJoined().observe(getViewLifecycleOwner(), joined -> {
            boolean isJoined = Boolean.TRUE.equals(joined);
            binding.btnJoin.setText(isJoined ? R.string.btn_leave_event : R.string.btn_join_event);
        });

        viewModel.getAttendeeNames().observe(getViewLifecycleOwner(), names -> {
            attendeeAdapter.submit(names);
            int count = names != null ? names.size() : 0;
            binding.tvAttendeesTitle.setText("Attendees (" + count + ")");
        });

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

    private void bindEvent(Event event) {
        if (event == null) return;
        binding.tvEventTitle.setText(event.getTitle() != null ? event.getTitle() : "");
        binding.tvEventDescription.setText(event.getDescription() != null ? event.getDescription() : "");
        binding.tvEventLocation.setText(event.getLocation() != null ? event.getLocation() : "");

        String date = event.getShortDate();
        String time = event.getShortTime();
        if (date != null && time != null) {
            binding.tvEventDate.setText(date + " at " + time);
        } else if (date != null) {
            binding.tvEventDate.setText(date);
        } else {
            binding.tvEventDate.setText("Date to be announced");
        }

        if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(event.getImageUrl())
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(binding.ivEventImage);
        } else {
            binding.ivEventImage.setImageResource(R.drawable.ic_events);
        }
        binding.btnJoin.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
