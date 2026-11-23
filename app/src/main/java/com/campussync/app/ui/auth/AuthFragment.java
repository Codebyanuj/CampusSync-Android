package com.campussync.app.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentAuthBinding;
import com.campussync.app.firebase.AuthManager.AuthState;
import com.campussync.app.ui.MainActivity;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Login / registration screen. The form swaps between modes in place rather than using
 * two separate screens, keeping a single source of validation logic.
 */
public class AuthFragment extends Fragment {

    private FragmentAuthBinding binding;
    private AuthViewModel viewModel;
    private boolean isRegisterMode;

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

        binding.btnSubmit.setOnClickListener(v -> submit());
        binding.tvLink.setOnClickListener(v -> toggleMode());

        if (!viewModel.isConfigured()) {
            // No google-services.json at build time: make the blocking reason explicit.
            binding.btnSubmit.setEnabled(false);
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
    }

    private void toggleMode() {
        isRegisterMode = !isRegisterMode;
        clearFieldErrors();
        hideError();
        updateModeUi();
    }

    private void updateModeUi() {
        if (binding == null) return;
        if (isRegisterMode) {
            binding.tvTitle.setText(R.string.title_register);
            binding.tvLink.setText(R.string.link_sign_in);
            binding.btnSubmit.setText(R.string.btn_register);
            binding.tilConfirmPassword.setVisibility(View.VISIBLE);
            binding.tilDisplayName.setVisibility(View.VISIBLE);
        } else {
            binding.tvTitle.setText(R.string.title_login);
            binding.tvLink.setText(R.string.link_register);
            binding.btnSubmit.setText(R.string.btn_sign_in);
            binding.tilConfirmPassword.setVisibility(View.GONE);
            binding.tilDisplayName.setVisibility(View.GONE);
        }
    }

    private void submit() {
        String email = text(binding.etEmail);
        String password = text(binding.etPassword);

        if (!validateCommon(email, password)) return;

        if (isRegisterMode) {
            String confirm = text(binding.etConfirmPassword);
            String displayName = text(binding.etDisplayName);
            if (displayName.isEmpty()) {
                binding.tilDisplayName.setError(getString(R.string.error_name_required));
                return;
            }
            if (!password.equals(confirm)) {
                binding.tilConfirmPassword.setError(getString(R.string.error_password_mismatch));
                return;
            }
            hideKeyboard();
            viewModel.register(email, password, displayName);
        } else {
            hideKeyboard();
            viewModel.signIn(email, password);
        }

    }

    private boolean validateCommon(String email, String password) {
        clearFieldErrors();
        if (email.isEmpty()) {
            binding.tilEmail.setError(getString(R.string.error_email_required));
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_invalid_email));
            return false;
        }
        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.error_password_required));
            return false;
        }
        if (password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.error_password_short));
            return false;
        }
        return true;
    }

    private void clearFieldErrors() {
        for (TextInputLayout layout : new TextInputLayout[]{
                binding.tilEmail, binding.tilPassword,
                binding.tilConfirmPassword, binding.tilDisplayName}) {
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
        binding.etEmail.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
        binding.etConfirmPassword.setEnabled(!loading);
        binding.etDisplayName.setEnabled(!loading);
        binding.tvLink.setEnabled(!loading);
    }

    private void hideKeyboard() {
        View view = getView();
        if (view == null) return;
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
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
