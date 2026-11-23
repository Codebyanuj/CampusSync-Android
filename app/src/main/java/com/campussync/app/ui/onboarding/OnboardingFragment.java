package com.campussync.app.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentOnboardingBinding;
import com.campussync.app.util.SessionPrefs;

/**
 * First-run introduction. Shown once; the flag is stored in SharedPreferences so the splash
 * screen can skip it on later launches.
 */
public class OnboardingFragment extends Fragment {

    private FragmentOnboardingBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnGetStarted.setOnClickListener(v -> finishOnboarding());
        binding.tvSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        SessionPrefs.setOnboardingComplete(requireContext());
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.onboardingFragment, true)
                .build();
        Navigation.findNavController(requireView()).navigate(R.id.authFragment, null, options);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
