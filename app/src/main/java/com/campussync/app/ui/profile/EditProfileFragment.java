package com.campussync.app.ui.profile;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.data.Colleges;
import com.campussync.app.databinding.FragmentEditProfileBinding;
import com.campussync.app.model.UserProfile;
import com.campussync.app.util.ProfileValidator;
import com.campussync.app.util.Tags;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * Edit profile: name, academic details, bio, skills/interests and the profile photo.
 */
public class EditProfileFragment extends Fragment {

    private FragmentEditProfileBinding binding;
    private EditProfileViewModel viewModel;
    private UserProfile currentProfile;
    private boolean fieldsPopulated;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) viewModel.uploadPhoto(uri);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(EditProfileViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEditProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.btnChangePhoto.setOnClickListener(v -> imagePicker.launch("image/*"));
        binding.btnSave.setOnClickListener(v -> save());

        setupDropdowns();

        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile == null) return;
            currentProfile = profile;
            bindAvatar(profile.getPhotoUrl());
            if (!fieldsPopulated) {
                populate(profile);
                fieldsPopulated = true;
            }
            updateRoleFields(profile);
        });

        viewModel.getSaving().observe(getViewLifecycleOwner(), saving -> {
            boolean busy = Boolean.TRUE.equals(saving);
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnSave.setEnabled(!busy);
            binding.btnChangePhoto.setEnabled(!busy);
        });

        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
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

        viewModel.startListening();
    }

    private void setupDropdowns() {
        binding.etCollege.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, Colleges.collegeNames()));
        binding.etCollege.setOnItemClickListener((parent, v, position, id) ->
                updateDepartments(String.valueOf(parent.getItemAtPosition(position))));
        binding.etDepartment.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, Colleges.departmentsFor(null)));
        binding.etCourse.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, Colleges.courses()));
        binding.etSemester.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, Colleges.semesters()));
        binding.etAcademicYear.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, Colleges.academicYears()));
        binding.etDesignation.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, Colleges.designations()));
    }

    private void updateDepartments(String college) {
        binding.etDepartment.setText("", false);
        binding.etDepartment.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, Colleges.departmentsFor(college)));
    }

    private void bindAvatar(String photoUrl) {
        Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.ivAvatar);
    }

    private void populate(UserProfile profile) {
        binding.etDisplayName.setText(profile.getDisplayName());
        binding.etCollege.setText(profile.getCollege(), false);
        binding.etDepartment.setText(profile.getDepartment(), false);
        binding.etCourse.setText(profile.getCourse(), false);
        if (profile.getSemester() > 0) {
            binding.etSemester.setText(String.valueOf(profile.getSemester()), false);
        }
        binding.etAcademicYear.setText(profile.getAcademicYear(), false);
        binding.etDesignation.setText(profile.getDesignation(), false);
        binding.etBio.setText(profile.getBio());
        binding.etSkills.setText(Tags.join(profile.getSkills()));
        binding.etInterests.setText(Tags.join(profile.getInterests()));
    }

    private void updateRoleFields(UserProfile profile) {
        boolean faculty = profile != null && profile.isFaculty();
        binding.tilCourse.setVisibility(faculty ? View.GONE : View.VISIBLE);
        binding.tilSemester.setVisibility(faculty ? View.GONE : View.VISIBLE);
        binding.tilDesignation.setVisibility(faculty ? View.VISIBLE : View.GONE);
        binding.tvRoleLocked.setText(faculty
                ? R.string.edit_role_locked_faculty
                : R.string.edit_role_locked_student);
    }

    private void save() {
        String role = currentProfile != null ? currentProfile.getRole() : UserProfile.ROLE_STUDENT;
        String name = text(binding.etDisplayName);
        String college = text(binding.etCollege);
        String department = text(binding.etDepartment);
        String course = text(binding.etCourse);
        String semester = text(binding.etSemester);
        String academicYear = text(binding.etAcademicYear);
        String designation = text(binding.etDesignation);
        String bio = text(binding.etBio);

        clearErrors();
        int error = ProfileValidator.nameError(name);
        if (error == 0) error = ProfileValidator.collegeError(college);
        if (error == 0) error = ProfileValidator.departmentError(department);
        if (error == 0) error = ProfileValidator.courseError(course, role);
        if (error == 0) {
            error = ProfileValidator.semesterError(ProfileValidator.parseSemester(semester), role);
        }
        if (error == 0) error = ProfileValidator.academicYearError(academicYear);
        if (error == 0) error = ProfileValidator.designationError(designation, role);
        if (error == 0) error = ProfileValidator.bioError(bio);

        if (error != 0) {
            showFieldError(error);
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("displayName", name);
        fields.put("college", college);
        fields.put("department", department);
        fields.put("academicYear", academicYear);
        fields.put("bio", bio.trim());
        fields.put("skills", Tags.parse(text(binding.etSkills)));
        fields.put("interests", Tags.parse(text(binding.etInterests)));
        if (UserProfile.ROLE_FACULTY.equals(role)) {
            fields.put("designation", designation);
            fields.put("course", "");
            fields.put("semester", 0);
        } else {
            fields.put("course", course);
            fields.put("semester", ProfileValidator.parseSemester(semester));
            fields.put("designation", "");
        }
        hideKeyboard();
        viewModel.save(fields);
    }

    private void showFieldError(int stringRes) {
        TextInputLayout target;
        if (stringRes == R.string.error_college_required) target = binding.tilCollege;
        else if (stringRes == R.string.error_department_required) target = binding.tilDepartment;
        else if (stringRes == R.string.error_course_required) target = binding.tilCourse;
        else if (stringRes == R.string.error_semester_required) target = binding.tilSemester;
        else if (stringRes == R.string.error_academic_year_required) target = binding.tilAcademicYear;
        else if (stringRes == R.string.error_designation_required) target = binding.tilDesignation;
        else if (stringRes == R.string.error_bio_long) target = binding.tilBio;
        else target = binding.tilDisplayName;
        target.setError(getString(stringRes));
        target.requestFocus();
    }

    private void clearErrors() {
        for (TextInputLayout layout : new TextInputLayout[]{
                binding.tilDisplayName, binding.tilCollege, binding.tilDepartment,
                binding.tilCourse, binding.tilSemester, binding.tilAcademicYear,
                binding.tilDesignation, binding.tilBio}) {
            layout.setError(null);
        }
    }

    private String text(android.widget.EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && getView() != null) {
            imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
