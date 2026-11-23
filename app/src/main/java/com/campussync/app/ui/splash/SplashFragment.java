package com.campussync.app.ui.splash;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentSplashBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.util.SessionPrefs;
import com.google.firebase.auth.FirebaseUser;

/**
 * Splash screen and session router.
 *
 * FirebaseAuth persists the session, so this screen only has to decide where to go:
 * first run -> onboarding, signed out -> auth, unverified -> verification gate, else home.
 */
public class SplashFragment extends Fragment {

    /** Short hold so the branded screen is visible without delaying real startup. */
    private static final long SPLASH_DELAY_MS = 700L;

    private FragmentSplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable routeRunnable = this::route;
    private boolean routed;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSplashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        handler.postDelayed(routeRunnable, SPLASH_DELAY_MS);
    }

    private void route() {
        if (routed || binding == null || !isAdded()) return;
        routed = true;

        FirebaseUser user = null;
        try {
            user = AuthManager.getInstance().getCurrentUser();
        } catch (IllegalStateException notConfigured) {
            // Firebase is not configured in this build; the auth screen explains what is missing.
        }

        int destination;
        if (!SessionPrefs.isOnboardingComplete(requireContext())) {
            destination = R.id.onboardingFragment;
        } else if (user == null) {
            destination = R.id.authFragment;
        } else if (!user.isEmailVerified()) {
            destination = R.id.verifyEmailFragment;
        } else {
            destination = R.id.homeFragment;
        }

        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.splashFragment, true)
                .setEnterAnim(android.R.anim.fade_in)
                .setExitAnim(android.R.anim.fade_out)
                .build();
        Navigation.findNavController(requireView()).navigate(destination, null, options);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(routeRunnable);
        binding = null;
    }
}
