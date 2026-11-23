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
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentSettingsBinding;
import com.campussync.app.firebase.FirestoreManager;

/**
 * Settings screen: notification preference, cache clearing and app version.
 */
public class SettingsFragment extends Fragment {

    private static final String PREFS = "campus_sync_prefs";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";

    private FragmentSettingsBinding binding;
    private SharedPreferences preferences;

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

        binding.switchNotifications.setChecked(preferences.getBoolean(KEY_NOTIFICATIONS, true));
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, checked) ->
                preferences.edit().putBoolean(KEY_NOTIFICATIONS, checked).apply());

        binding.tvVersion.setText(getString(R.string.settings_version, versionName()));

        View.OnClickListener clearCache = v -> {
            FirestoreManager.getInstance().clearLocalCache();
            binding.tvCacheToast.setVisibility(View.VISIBLE);
        };
        binding.btnClearCache.setOnClickListener(clearCache);
        binding.itemCache.setOnClickListener(clearCache);
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
