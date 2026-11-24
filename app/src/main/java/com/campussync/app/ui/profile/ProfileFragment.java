package com.campussync.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
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
import com.campussync.app.databinding.FragmentProfileBinding;
import com.campussync.app.model.Event;
import com.campussync.app.model.UserProfile;
import com.campussync.app.ui.events.EventAdapter;
import com.campussync.app.ui.groups.GroupAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Profile tab: identity, verification badge, academic details, bio, skills/interests,
 * activity counts and the user's joined events and groups.
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
        binding.btnEditProfile.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.editProfileFragment));
        binding.btnVerification.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.verificationFragment));

        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

        viewModel.getProfile().observe(getViewLifecycleOwner(), this::bindProfile);
        viewModel.getAdmin().observe(getViewLifecycleOwner(), this::updateAdminMenu);

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

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                com.google.android.material.snackbar.Snackbar.make(
                        binding.getRoot(), message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
            }
        });

        viewModel.startListening();
    }

    private void bindProfile(UserProfile profile) {
        if (profile == null) return;

        binding.tvDisplayName.setText(profile.getDisplayName() != null
                ? profile.getDisplayName() : "CampusSync user");
        binding.tvEmail.setText(profile.getEmail() != null ? profile.getEmail() : "");

        String role = profile.isFaculty()
                ? getString(R.string.role_faculty)
                : getString(R.string.role_student);
        binding.tvRoleChip.setText(role);

        bindVerificationBadge(profile);

        Glide.with(this)
                .load(profile.getPhotoUrl())
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.ivAvatar);

        binding.tvCollege.setText(value(profile.getCollege(), R.string.profile_no_college));
        binding.tvDepartment.setText(value(profile.getDepartment(), R.string.profile_no_department));

        if (profile.isFaculty()) {
            binding.tvCourse.setText(profile.getDesignation() != null
                    && !profile.getDesignation().isEmpty()
                    ? profile.getDesignation() : getString(R.string.profile_no_course));
        } else {
            StringBuilder course = new StringBuilder();
            if (profile.getCourse() != null && !profile.getCourse().isEmpty()) {
                course.append(profile.getCourse());
            }
            if (profile.getSemester() > 0) {
                if (course.length() > 0) course.append(" · ");
                course.append(getString(R.string.profile_semester_value, profile.getSemester()));
            }
            binding.tvCourse.setText(course.length() > 0
                    ? course.toString() : getString(R.string.profile_no_course));
        }

        binding.tvAcademicYear.setText(value(profile.getAcademicYear(),
                R.string.profile_no_academic_year));

        String bio = profile.getBio();
        boolean hasBio = bio != null && !bio.trim().isEmpty();
        binding.tvBio.setVisibility(hasBio ? View.VISIBLE : View.GONE);
        binding.tvBio.setText(hasBio ? bio.trim() : "");
        binding.tvBioEmpty.setVisibility(hasBio ? View.GONE : View.VISIBLE);

        bindTags(binding.tvSkills, profile.getSkills());
        bindTags(binding.tvInterests, profile.getInterests());

        if (profile.isSuspended()) {
            binding.tvAccountStatus.setVisibility(View.VISIBLE);
            binding.tvAccountStatus.setText(R.string.account_suspended);
        } else {
            binding.tvAccountStatus.setVisibility(View.GONE);
        }

        binding.btnVerification.setText(profile.isVerified()
                ? R.string.verification_badge_verified
                : R.string.verification_manage);
    }

    private void bindVerificationBadge(UserProfile profile) {
        if (profile.isVerified()) {
            binding.tvVerificationChip.setText(profile.isFaculty()
                    ? R.string.verification_badge_faculty
                    : R.string.verification_badge_student);
            binding.tvVerificationChip.setBackgroundResource(R.drawable.bg_badge_verified);
        } else if (profile.isVerificationPending()) {
            binding.tvVerificationChip.setText(R.string.verification_badge_pending);
            binding.tvVerificationChip.setBackgroundResource(R.drawable.bg_badge_pending);
        } else {
            binding.tvVerificationChip.setText(R.string.verification_badge_unverified);
            binding.tvVerificationChip.setBackgroundResource(R.drawable.bg_badge_neutral);
        }
    }

    private void bindTags(android.widget.TextView target, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            target.setVisibility(View.GONE);
            return;
        }
        target.setVisibility(View.VISIBLE);
        target.setText(android.text.TextUtils.join(" · ", tags));
    }

    private String value(String raw, int emptyRes) {
        return raw != null && !raw.trim().isEmpty() ? raw.trim() : getString(emptyRes);
    }

    private void updateAdminMenu(Boolean isAdmin) {
        if (binding == null) return;
        Menu menu = binding.toolbar.getMenu();
        MenuItem item = menu.findItem(R.id.action_admin_requests);
        if (item != null) item.setVisible(Boolean.TRUE.equals(isAdmin));
        MenuItem reports = menu.findItem(R.id.action_admin_reports);
        if (reports != null) reports.setVisible(Boolean.TRUE.equals(isAdmin));
    }

    private boolean onMenuItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Navigation.findNavController(requireView()).navigate(R.id.settingsFragment);
            return true;
        }
        if (item.getItemId() == R.id.action_edit_profile) {
            Navigation.findNavController(requireView()).navigate(R.id.editProfileFragment);
            return true;
        }
        if (item.getItemId() == R.id.action_verification) {
            Navigation.findNavController(requireView()).navigate(R.id.verificationFragment);
            return true;
        }
        if (item.getItemId() == R.id.action_admin_requests) {
            Navigation.findNavController(requireView())
                    .navigate(R.id.adminVerificationsFragment);
            return true;
        }
        if (item.getItemId() == R.id.action_admin_reports) {
            Navigation.findNavController(requireView()).navigate(R.id.adminReportsFragment);
            return true;
        }
        if (item.getItemId() == R.id.action_view_public_profile) {
            openPublicProfile();
            return true;
        }
        if (item.getItemId() == R.id.action_sign_out) {
            confirmSignOut();
            return true;
        }
        return false;
    }

    private void openPublicProfile() {
        String uid = com.campussync.app.firebase.AuthManager.getInstance().getCurrentUserId();
        if (uid == null) return;
        Bundle args = new Bundle();
        args.putString("uid", uid);
        Navigation.findNavController(requireView()).navigate(R.id.userProfileFragment, args);
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
