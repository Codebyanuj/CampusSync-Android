package com.campussync.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.FragmentUserProfileBinding;
import com.campussync.app.model.UserProfile;

import java.util.List;

/**
 * Read-only public profile: what other students and faculty can see.
 */
public class UserProfileFragment extends Fragment {

    private FragmentUserProfileBinding binding;
    private UserProfileViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(UserProfileViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUserProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getNotFound().observe(getViewLifecycleOwner(), notFound -> {
            if (Boolean.TRUE.equals(notFound)) {
                binding.tvNotFound.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getProfile().observe(getViewLifecycleOwner(), this::bind);

        viewModel.getIsSelf().observe(getViewLifecycleOwner(), isSelf ->
                binding.btnFollow.setVisibility(Boolean.TRUE.equals(isSelf)
                        ? View.GONE : View.VISIBLE));

        viewModel.getFollowing().observe(getViewLifecycleOwner(), follow -> {
            boolean isFollowing = Boolean.TRUE.equals(follow);
            binding.btnFollow.setText(isFollowing
                    ? R.string.btn_unfollow : R.string.btn_follow);
        });

        binding.btnFollow.setOnClickListener(v -> viewModel.toggleFollow(error -> {
            if (error != null) {
                com.google.android.material.snackbar.Snackbar.make(
                        requireView(), R.string.follow_failed,
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            }
        }));

        String uid = getArguments() != null ? getArguments().getString("uid") : null;
        if (uid == null) {
            binding.tvNotFound.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.GONE);
            return;
        }
        viewModel.load(uid);
    }

    private void bind(UserProfile profile) {
        if (profile == null) return;
        binding.tvDisplayName.setText(profile.getDisplayName() != null
                ? profile.getDisplayName() : "CampusSync user");
        binding.tvEmail.setText(profile.getEmail() != null ? profile.getEmail() : "");
        binding.tvRoleChip.setText(profile.isFaculty()
                ? R.string.role_faculty : R.string.role_student);

        if (profile.isVerified()) {
            binding.tvVerificationChip.setText(profile.isFaculty()
                    ? R.string.verification_badge_faculty
                    : R.string.verification_badge_student);
            binding.tvVerificationChip.setBackgroundResource(R.drawable.bg_badge_verified);
        } else {
            binding.tvVerificationChip.setText(R.string.verification_badge_unverified);
            binding.tvVerificationChip.setBackgroundResource(R.drawable.bg_badge_neutral);
        }

        Glide.with(this)
                .load(profile.getPhotoUrl())
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.ivAvatar);

        binding.tvCollege.setText(profile.getCollege() != null ? profile.getCollege() : "—");
        binding.tvDepartment.setText(profile.getDepartment() != null ? profile.getDepartment() : "—");

        String detail;
        if (profile.isFaculty()) {
            detail = profile.getDesignation() != null ? profile.getDesignation() : "";
        } else {
            detail = profile.getCourse() != null ? profile.getCourse() : "";
            if (profile.getSemester() > 0) {
                detail = detail + (detail.isEmpty() ? "" : " · ")
                        + getString(R.string.profile_semester_value, profile.getSemester());
            }
        }
        binding.tvCourse.setText(detail.isEmpty() ? "—" : detail);
        binding.tvAcademicYear.setText(profile.getAcademicYear() != null
                ? profile.getAcademicYear() : "—");

        boolean hasBio = profile.getBio() != null && !profile.getBio().trim().isEmpty();
        binding.tvBio.setVisibility(hasBio ? View.VISIBLE : View.GONE);
        binding.tvBio.setText(hasBio ? profile.getBio().trim() : "");
        binding.tvBioEmpty.setVisibility(hasBio ? View.GONE : View.VISIBLE);

        bindTags(binding.tvSkills, profile.getSkills());
        bindTags(binding.tvInterests, profile.getInterests());
    }

    private void bindTags(android.widget.TextView target, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            target.setVisibility(View.GONE);
            return;
        }
        target.setVisibility(View.VISIBLE);
        target.setText(android.text.TextUtils.join(" · ", tags));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
