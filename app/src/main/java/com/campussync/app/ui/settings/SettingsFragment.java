package com.campussync.app.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.campussync.app.R;
import com.campussync.app.databinding.DialogChangePasswordBinding;
import com.campussync.app.databinding.FragmentSettingsBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.FirestoreManager;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.util.NotificationPrefs;
import com.campussync.app.util.ProfileValidator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/**
 * Settings screen: profile actions (edit profile, verification, password), notification
 * preference, cache clearing and app version.
 */
public class SettingsFragment extends Fragment {

    private static final String PREFS = "campus_sync_prefs";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    private SharedPreferences preferences;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferences = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        binding.itemEditProfile.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.editProfileFragment));
        binding.itemVerification.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.verificationFragment));
        binding.itemChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        binding.btnSettingsSignOut.setOnClickListener(v -> confirmSignOut());

        binding.switchNotifications.setChecked(NotificationPrefs.pushEnabled(requireContext()));
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, checked) -> {
            NotificationPrefs.setPushEnabled(requireContext(), checked);
            persistPushPreference("notifyAll", checked);
        });

        binding.switchMessages.setChecked(NotificationPrefs.messagesEnabled(requireContext()));
        binding.switchMessages.setOnCheckedChangeListener((buttonView, checked) -> {
            NotificationPrefs.setMessagesEnabled(requireContext(), checked);
            persistPushPreference("notifyMessages", checked);
        });

        binding.switchGroupInvites.setChecked(NotificationPrefs.groupsEnabled(requireContext()));
        binding.switchGroupInvites.setOnCheckedChangeListener((buttonView, checked) -> {
            NotificationPrefs.setGroupsEnabled(requireContext(), checked);
            persistPushPreference("notifyGroups", checked);
        });

        binding.tvVersion.setText(getString(R.string.settings_version, versionName()));

        View.OnClickListener clearCache = v -> {
            FirestoreManager.getInstance().clearLocalCache();
            binding.tvCacheToast.setVisibility(View.VISIBLE);
        };
        binding.btnClearCache.setOnClickListener(clearCache);
        binding.itemCache.setOnClickListener(clearCache);

        viewModel.getSaving().observe(getViewLifecycleOwner(), saving ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(saving)
                        ? View.VISIBLE : View.GONE));

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
    }

    /** Mirrors a toggle on the user document so the Cloud Functions backend can respect it. */
    private void persistPushPreference(String field, boolean value) {
        java.util.Map<String, Object> fields = new java.util.HashMap<>();
        fields.put(field, value);
        UserRepository.getInstance().saveProfile(fields, error -> {
            if (error != null) {
                android.util.Log.w("SettingsFragment", "notification preference not saved", error);
            }
        });
    }

    private void showChangePasswordDialog() {
        DialogChangePasswordBinding dialogBinding =
                DialogChangePasswordBinding.inflate(getLayoutInflater());

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_change_password)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_change_password, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String current = text(dialogBinding.etCurrentPassword);
                    String next = text(dialogBinding.etNewPassword);
                    String confirm = text(dialogBinding.etConfirmNewPassword);

                    dialogBinding.tilCurrentPassword.setError(null);
                    dialogBinding.tilNewPassword.setError(null);
                    dialogBinding.tilConfirmNewPassword.setError(null);

                    if (current.isEmpty()) {
                        dialogBinding.tilCurrentPassword.setError(
                                getString(R.string.error_current_password_required));
                        return;
                    }
                    int error = ProfileValidator.passwordError(next, confirm);
                    if (error == R.string.error_password_mismatch) {
                        dialogBinding.tilConfirmNewPassword.setError(getString(error));
                        return;
                    }
                    if (error != 0) {
                        dialogBinding.tilNewPassword.setError(getString(error));
                        return;
                    }
                    dialog.dismiss();
                    viewModel.changePassword(current, next);
                }));
        dialog.show();
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.btn_sign_out)
                .setMessage(R.string.confirm_sign_out)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_sign_out, (dialog, which) -> viewModel.signOut())
                .show();
    }

    private String text(android.widget.EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private String versionName() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
