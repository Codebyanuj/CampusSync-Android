package com.campussync.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentProfileBinding;
import com.campussync.app.model.Event;
import com.campussync.app.ui.events.EventAdapter;
import com.campussync.app.ui.groups.GroupAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Profile tab: identity, activity counts, joined events and joined groups.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private EventAdapter eventsAdapter;
    private GroupAdapter groupsAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        eventsAdapter = new EventAdapter(event -> openEvent(view, event));
        groupsAdapter = new GroupAdapter(group -> openGroup(view, group));

        binding.rvJoinedEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvJoinedEvents.setAdapter(eventsAdapter);
        binding.rvJoinedGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvJoinedGroups.setAdapter(groupsAdapter);

        binding.btnSettings.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.settingsFragment));

        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

        binding.tvDisplayName.setText(viewModel.getDisplayName());
        binding.tvEmail.setText(viewModel.getEmail());

        viewModel.getJoinedEvents().observe(getViewLifecycleOwner(), events -> {
            eventsAdapter.submitList(events);
            int count = events != null ? events.size() : 0;
            binding.tvEventsCount.setText(String.valueOf(count));
            boolean empty = count == 0;
            binding.tvEmptyEvents.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvJoinedEvents.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getJoinedGroups().observe(getViewLifecycleOwner(), groups -> {
            groupsAdapter.submitList(groups);
            int count = groups != null ? groups.size() : 0;
            binding.tvGroupsCount.setText(String.valueOf(count));
            boolean empty = count == 0;
            binding.tvEmptyGroups.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvJoinedGroups.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.startListening();
    }

    private boolean onMenuItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Navigation.findNavController(requireView()).navigate(R.id.settingsFragment);
            return true;
        }
        if (item.getItemId() == R.id.action_sign_out) {
            confirmSignOut();
            return true;
        }
        return false;
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.btn_sign_out)
                .setMessage(R.string.confirm_sign_out)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_sign_out, (dialog, which) -> viewModel.signOut())
                .show();
    }

    private void openEvent(View view, Event event) {
        if (event.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("eventId", event.getId());
        Navigation.findNavController(view).navigate(R.id.eventDetailFragment, args);
    }

    private void openGroup(View view, com.campussync.app.model.Group group) {
        if (group.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("groupId", group.getId());
        Navigation.findNavController(view).navigate(R.id.groupDetailFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
