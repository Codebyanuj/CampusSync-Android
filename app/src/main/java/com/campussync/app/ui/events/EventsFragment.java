package com.campussync.app.ui.events;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * Event radar tab: college notices entry point, upcoming events, search and category/date/college
 * filters with explicit loading, empty and error states.
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

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_host_event) {
                Navigation.findNavController(view).navigate(R.id.createEventFragment);
                return true;
            }
            if (id == R.id.action_my_events) {
                Navigation.findNavController(view).navigate(R.id.myEventsFragment);
                return true;
            }
            if (id == R.id.action_notices) {
                Navigation.findNavController(view).navigate(R.id.noticesFragment);
                return true;
            }
            return false;
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setQuery(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.chipCategories.setOnCheckedStateChangeListener(this::onCategoryChanged);
        binding.chipDates.setOnCheckedStateChangeListener(this::onDateChanged);

        observe();
        viewModel.startListening();
    }

    private void onCategoryChanged(ChipGroup group, List<Integer> checkedIds) {
        int id = checkedIds.isEmpty() ? R.id.chipAllCategories : checkedIds.get(0);
        viewModel.setCategory(categoryForChip(id));
    }

    private void onDateChanged(ChipGroup group, List<Integer> checkedIds) {
        int id = checkedIds.isEmpty() ? R.id.chipAnyDate : checkedIds.get(0);
        if (id == R.id.chipMyCollege) {
            String college = viewModel.getCollege().getValue();
            if (college == null || college.isEmpty()) {
                Snackbar.make(binding.getRoot(), R.string.error_college_required,
                        Snackbar.LENGTH_SHORT).show();
                binding.chipMyCollege.setChecked(false);
                return;
            }
            viewModel.setMyCollegeOnly(true);
            viewModel.setDateFilter(EventsViewModel.DATE_ANY);
            return;
        }
        viewModel.setMyCollegeOnly(false);
        if (id == R.id.chipToday) {
            viewModel.setDateFilter(EventsViewModel.DATE_TODAY);
        } else if (id == R.id.chipThisWeek) {
            viewModel.setDateFilter(EventsViewModel.DATE_WEEK);
        } else if (id == R.id.chipThisMonth) {
            viewModel.setDateFilter(EventsViewModel.DATE_MONTH);
        } else {
            viewModel.setDateFilter(EventsViewModel.DATE_ANY);
        }
    }

    private static String categoryForChip(int id) {
        if (id == R.id.chipAcademic) return Event.CATEGORY_ACADEMIC;
        if (id == R.id.chipCultural) return Event.CATEGORY_CULTURAL;
        if (id == R.id.chipSports) return Event.CATEGORY_SPORTS;
        if (id == R.id.chipWorkshop) return Event.CATEGORY_WORKSHOP;
        if (id == R.id.chipTech) return Event.CATEGORY_TECH;
        if (id == R.id.chipSocial) return Event.CATEGORY_SOCIAL;
        if (id == R.id.chipOther) return Event.CATEGORY_OTHER;
        return null;
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

        viewModel.getCollege().observe(getViewLifecycleOwner(), college ->
                binding.chipMyCollege.setEnabled(college != null && !college.isEmpty()));
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
