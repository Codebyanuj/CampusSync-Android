package com.campussync.app.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.campussync.app.R;
import com.campussync.app.data.Colleges;
import com.campussync.app.databinding.DialogForgotPasswordBinding;
import com.campussync.app.databinding.FragmentAuthBinding;
import com.campussync.app.firebase.AuthManager.AuthState;
import com.campussync.app.model.UserProfile;
import com.campussync.app.ui.MainActivity;
import com.campussync.app.util.ProfileValidator;
import com.campussync.app.util.SessionPrefs;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Login / registration screen.
 *
 * One form switches between sign-in and sign-up. Registration collects the academic profile
 * (student or faculty) that becomes the initial Firestore user document.
 */
public class AuthFragment extends Fragment {

    private FragmentAuthBinding binding;
    private AuthViewModel viewModel;
    private boolean isRegisterMode;
    private String role = UserProfile.ROLE_STUDENT;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Activity-scoped so the state survives configuration changes and is shared with MainActivity.
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAuthBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupDropdowns();

        binding.btnSubmit.setOnClickListener(v -> submit());
        binding.tvLink.setOnClickListener(v -> toggleMode());
        binding.tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        binding.roleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            role = checkedId == R.id.btnRoleFaculty
                    ? UserProfile.ROLE_FACULTY
                    : UserProfile.ROLE_STUDENT;
            updateRoleFields();
        });

        if (!viewModel.isConfigured()) {
            // No google-services.json at build time: make the blocking reason explicit.
            binding.btnSubmit.setEnabled(false);
        }

        String lastEmail = SessionPrefs.getLastEmail(requireContext());
        if (!lastEmail.isEmpty() && text(binding.etEmail).isEmpty()) {
            binding.etEmail.setText(lastEmail);
        }

        viewModel.getLoading().observe(getViewLifecycleOwner(), this::setLoading);

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                showError(message);
            } else {
                hideError();
            }
        });

        viewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
            if (state == AuthState.AUTHENTICATED) {
                // Navigation to the main tabs is driven by MainActivity's state observer so that
                // deep links and restored sessions behave identically.
                setLoading(false);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).requestNotificationPermission();
                }
            }
        });

        updateModeUi();
        updateRoleFields();
    }

    private void setupDropdowns() {
        binding.etCollege.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, Colleges.collegeNames()));
        binding.etCollege.setOnItemClickListener((parent, view, position, id) -> {
            binding.etCollege.setError(null);
            updateDepartments(String.valueOf(parent.getItemAtPosition(position)));
        });
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

    private void toggleMode() {
        isRegisterMode = !isRegisterMode;
        clearFieldErrors();
        hideError();
        viewModel.clearError();
        updateModeUi();
    }

    private void updateModeUi() {
        if (binding == null) return;
        if (isRegisterMode) {
            binding.tvTitle.setText(R.string.title_register);
            binding.tvSubtitle.setText(R.string.subtitle_register);
            binding.tvLink.setText(R.string.link_sign_in);
            binding.btnSubmit.setText(R.string.btn_register);
            binding.tvForgotPassword.setVisibility(View.GONE);
        } else {
            binding.tvTitle.setText(R.string.title_login);
            binding.tvSubtitle.setText(R.string.subtitle_login);
            binding.tvLink.setText(R.string.link_register);
            binding.btnSubmit.setText(R.string.btn_sign_in);
            binding.tvForgotPassword.setVisibility(View.VISIBLE);
        }
        int visibility = isRegisterMode ? View.VISIBLE : View.GONE;
        binding.roleGroup.setVisibility(visibility);
        binding.tilDisplayName.setVisibility(visibility);
        binding.tilConfirmPassword.setVisibility(visibility);
        binding.tilCollege.setVisibility(visibility);
        binding.tilDepartment.setVisibility(visibility);
        binding.tilAcademicYear.setVisibility(visibility);
        updateRoleFields();
    }

    /** Students pick a course and semester; faculty pick a designation. */
    private void updateRoleFields() {
        if (binding == null) return;
        boolean faculty = UserProfile.ROLE_FACULTY.equals(role);
        binding.tilCourse.setVisibility(faculty ? View.GONE : View.VISIBLE);
        binding.tilSemester.setVisibility(faculty ? View.GONE : View.VISIBLE);
        binding.tilDesignation.setVisibility(faculty ? View.VISIBLE : View.GONE);
    }

    private void submit() {
        String email = text(binding.etEmail);
        String password = text(binding.etPassword);
        clearFieldErrors();
        hideError();

        if (!isRegisterMode) {
            int emailError = ProfileValidator.emailError(email);
            if (emailError != 0) {
                binding.tilEmail.setError(getString(emailError));
                return;
            }
            int passwordError = ProfileValidator.passwordError(password, password);
            if (passwordError != 0) {
                binding.tilPassword.setError(getString(passwordError));
                return;
            }
            SessionPrefs.setLastEmail(requireContext(), email);
            hideKeyboard();
            viewModel.signIn(email, password);
            return;
        }

        RegistrationInput input = readRegistrationInput();
        int error = input.validate();
        if (error != 0) {
            showFieldError(error);
            return;
        }
        SessionPrefs.setLastEmail(requireContext(), input.email);
        hideKeyboard();
        viewModel.register(input);
    }

    private RegistrationInput readRegistrationInput() {
        RegistrationInput input = new RegistrationInput();
        input.fullName = text(binding.etDisplayName);
        input.email = text(binding.etEmail);
        input.password = text(binding.etPassword);
        input.confirmPassword = text(binding.etConfirmPassword);
        input.role = role;
        input.college = text(binding.etCollege);
        input.department = text(binding.etDepartment);
        input.course = text(binding.etCourse);
        input.semester = text(binding.etSemester);
        input.academicYear = text(binding.etAcademicYear);
        input.designation = text(binding.etDesignation);
        return input;
    }

    private void showFieldError(int stringRes) {
        TextInputLayout target;
        if (stringRes == R.string.error_name_required
                || stringRes == R.string.error_name_short
                || stringRes == R.string.error_name_long) {
            target = binding.tilDisplayName;
        } else if (stringRes == R.string.error_email_required
                || stringRes == R.string.error_invalid_email) {
            target = binding.tilEmail;
        } else if (stringRes == R.string.error_password_required
                || stringRes == R.string.error_password_short) {
            target = binding.tilPassword;
        } else if (stringRes == R.string.error_password_mismatch) {
            target = binding.tilConfirmPassword;
        } else if (stringRes == R.string.error_college_required) {
            target = binding.tilCollege;
        } else if (stringRes == R.string.error_department_required) {
            target = binding.tilDepartment;
        } else if (stringRes == R.string.error_course_required) {
            target = binding.tilCourse;
        } else if (stringRes == R.string.error_semester_required) {
            target = binding.tilSemester;
        } else if (stringRes == R.string.error_academic_year_required) {
            target = binding.tilAcademicYear;
        } else if (stringRes == R.string.error_designation_required) {
            target = binding.tilDesignation;
        } else {
            showError(getString(stringRes));
            return;
        }
        target.setError(getString(stringRes));
        target.requestFocus();
    }

    private void showForgotPasswordDialog() {
        hideKeyboard();
        DialogForgotPasswordBinding dialogBinding =
                DialogForgotPasswordBinding.inflate(getLayoutInflater());
        String currentEmail = text(binding.etEmail);
        if (!currentEmail.isEmpty()) dialogBinding.etResetEmail.setText(currentEmail);

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext())
                .setTitle(R.string.title_forgot_password)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_send_reset_link, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String email = dialogBinding.etResetEmail.getText() != null
                            ? dialogBinding.etResetEmail.getText().toString().trim() : "";
                    int error = ProfileValidator.emailError(email);
                    if (error != 0) {
                        dialogBinding.tilResetEmail.setError(getString(error));
                        return;
                    }
                    dialog.dismiss();
                    viewModel.sendPasswordReset(email);
                }));
        dialog.show();
    }

    private void clearFieldErrors() {
        for (TextInputLayout layout : new TextInputLayout[]{
                binding.tilEmail, binding.tilPassword, binding.tilConfirmPassword,
                binding.tilDisplayName, binding.tilCollege, binding.tilDepartment,
                binding.tilCourse, binding.tilSemester, binding.tilAcademicYear,
                binding.tilDesignation}) {
            layout.setError(null);
        }
    }

    private String text(android.widget.EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void showError(String message) {
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        binding.tvError.setText(null);
        binding.tvError.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSubmit.setEnabled(!loading);
        binding.btnSubmit.setText(loading
                ? getString(R.string.please_wait)
                : getString(isRegisterMode ? R.string.btn_register : R.string.btn_sign_in));
        for (View view : new View[]{
                binding.etEmail, binding.etPassword, binding.etConfirmPassword,
                binding.etDisplayName, binding.etCollege, binding.etDepartment,
                binding.etCourse, binding.etSemester, binding.etAcademicYear,
                binding.etDesignation, binding.tvLink, binding.tvForgotPassword,
                binding.roleGroup}) {
            view.setEnabled(!loading);
        }
    }

    private void hideKeyboard() {
        View view = getView();
        if (view == null) return;
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
