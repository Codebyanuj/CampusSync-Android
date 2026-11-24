package com.campussync.app.ui.events;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentCreateEventBinding;
import com.campussync.app.model.Event;
import com.campussync.app.model.UserProfile;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Host an event: details, category, college, capacity, intercollege switch and schedule. */
public class CreateEventFragment extends Fragment {

    private FragmentCreateEventBinding binding;
    private CreateEventViewModel viewModel;

    private Date startDate;
    private Date endDate;
    private String categoryKey = Event.CATEGORY_OTHER;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CreateEventViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCreateEventBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        String[] labels = EventUi.categoryLabels(requireContext());
        binding.autoCategory.setSimpleItems(labels);
        binding.autoCategory.setText(EventUi.categoryLabel(requireContext(), categoryKey), false);
        binding.autoCategory.setOnItemClickListener((parent, selected, position, id) -> {
            if (position >= 0 && position < EventUi.CATEGORY_KEYS.length) {
                categoryKey = EventUi.CATEGORY_KEYS[position];
            }
        });

        binding.btnPickStart.setOnClickListener(v -> pickDate(true));
        binding.btnPickEnd.setOnClickListener(v -> pickDate(false));
        binding.btnCreateEvent.setOnClickListener(v -> publish());

        observe();
        viewModel.loadProfile();
    }

    private void observe() {
        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile == null) return;
            if (binding.etCollege.getText() == null || binding.etCollege.getText().length() == 0) {
                binding.etCollege.setText(profile.getCollege() != null ? profile.getCollege() : "");
            }
        });

        viewModel.getPublishing().observe(getViewLifecycleOwner(), publishing -> {
            boolean busy = Boolean.TRUE.equals(publishing);
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnCreateEvent.setEnabled(!busy);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });
    }

    private void pickDate(final boolean isStart) {
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isStart ? "Event start date" : "Event end date");
        Date current = isStart ? startDate : endDate;
        builder.setSelection(current != null ? current.getTime() : System.currentTimeMillis());
        MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) return;
            pickTime(isStart, new Date(selection));
        });
        picker.show(getChildFragmentManager(), isStart ? "startDate" : "endDate");
    }

    private void pickTime(final boolean isStart, final Date day) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(10)
                .setMinute(0)
                .setTitleText(isStart ? "Event start time" : "Event end time")
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(day);
            calendar.set(Calendar.HOUR_OF_DAY, picker.getHour());
            calendar.set(Calendar.MINUTE, picker.getMinute());
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date selection = calendar.getTime();
            if (isStart) {
                startDate = selection;
                binding.btnPickStart.setText("Starts " + EventUi.formatRange(startDate, null));
            } else {
                endDate = selection;
                binding.btnPickEnd.setText("Ends " + EventUi.formatRange(endDate, null));
            }
        });
        picker.show(getChildFragmentManager(), isStart ? "startTime" : "endTime");
    }

    private void publish() {
        String title = text(binding.etTitle.getText());
        String description = text(binding.etDescription.getText());
        String location = text(binding.etLocation.getText());
        String college = text(binding.etCollege.getText());
        String capacityText = text(binding.etCapacity.getText());

        if (title.isEmpty()) {
            binding.tilTitle.setError(getString(R.string.error_title_required));
            return;
        }
        binding.tilTitle.setError(null);
        if (description.isEmpty()) {
            binding.tilDescription.setError(getString(R.string.error_description_required));
            return;
        }
        binding.tilDescription.setError(null);
        if (college.isEmpty()) {
            binding.tilCollege.setError(getString(R.string.error_college_required));
            return;
        }
        binding.tilCollege.setError(null);

        long capacity = 0;
        if (!capacityText.isEmpty()) {
            try {
                capacity = Long.parseLong(capacityText);
            } catch (NumberFormatException error) {
                binding.tilCapacity.setError(getString(R.string.error_capacity));
                return;
            }
        }
        binding.tilCapacity.setError(null);

        if (startDate == null || endDate == null) {
            Snackbar.make(binding.getRoot(), R.string.error_select_time, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (startDate.getTime() < System.currentTimeMillis()) {
            Snackbar.make(binding.getRoot(), R.string.error_start_past, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (endDate.getTime() <= startDate.getTime()) {
            Snackbar.make(binding.getRoot(), R.string.error_end_before_start,
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        UserProfile profile = viewModel.currentProfile();
        Event event = new Event(title, description, location, null);
        event.setCategory(categoryKey);
        event.setCollege(college);
        event.setCapacity(capacity);
        event.setIntercollege(binding.switchIntercollege.isChecked());
        event.setStartDate(startDate);
        event.setEndDate(endDate);
        event.setOrganizerName(profile != null ? profile.getDisplayName() : null);

        viewModel.create(event, error -> {
            if (error != null) return;
            Toast.makeText(requireContext(), R.string.msg_event_created, Toast.LENGTH_SHORT).show();
            if (isAdded()) Navigation.findNavController(requireView()).navigateUp();
        });
    }

    private static String text(CharSequence value) {
        return value != null ? value.toString().trim() : "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
