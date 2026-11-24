package com.campussync.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.campussync.app.R;
import com.campussync.app.databinding.ActivityMainBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.MessagingRepository;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.ui.auth.AuthViewModel;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Single-activity host. Owns the NavController, bottom navigation and session routing:
 * splash -> onboarding / auth / verification / main tabs, depending on the restored session.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private boolean notificationPermissionRequested;
    private boolean emailVerificationScreenVisible;
    private boolean accountStatusCheckInFlight;
    private boolean suspendedDialogShown;
    private ListenerRegistration unreadRegistration;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                notificationPermissionRequested = false;
                if (granted) {
                    AuthManager.getInstance().registerFcmToken(null);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment is missing from activity_main.xml");
        }
        navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            updateBottomNavVisibility(destination.getId());
            if (isMainDestination(destination.getId())) {
                // Covers both a fresh sign-in and a session restored from a previous launch.
                enforceAccountStatus();
            }
        });

        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        authViewModel.getAuthState().observe(this, state -> {
            if (state == null) return;
            switch (state) {
                case AUTHENTICATED:
                    onAuthenticated();
                    break;
                case UNAUTHENTICATED:
                    onUnauthenticated();
                    break;
                case LOADING:
                case ERROR:
                default:
                    break;
            }
        });

        handleNotificationIntent(getIntent());
    }

    /** True for destinations that are part of the signed-in tab bar. */
    private boolean isMainDestination(int destinationId) {
        return destinationId == R.id.homeFragment
                || destinationId == R.id.feedFragment
                || destinationId == R.id.conversationsFragment
                || destinationId == R.id.eventsFragment
                || destinationId == R.id.groupsFragment
                || destinationId == R.id.profileFragment;
    }

    /** Destinations that only make sense while signed in. */
    private boolean isSignedInDestination(int destinationId) {
        return isMainDestination(destinationId)
                || destinationId == R.id.eventDetailFragment
                || destinationId == R.id.createEventFragment
                || destinationId == R.id.eventParticipantsFragment
                || destinationId == R.id.myEventsFragment
                || destinationId == R.id.noticesFragment
                || destinationId == R.id.groupDetailFragment
                || destinationId == R.id.settingsFragment
                || destinationId == R.id.editProfileFragment
                || destinationId == R.id.userProfileFragment
                || destinationId == R.id.verificationFragment
                || destinationId == R.id.adminVerificationsFragment
                || destinationId == R.id.adminReportsFragment
                || destinationId == R.id.createPostFragment
                || destinationId == R.id.commentsFragment
                || destinationId == R.id.storyViewerFragment
                || destinationId == R.id.boardsFragment
                || destinationId == R.id.boardDetailFragment
                || destinationId == R.id.discussionDetailFragment
                || destinationId == R.id.chatFragment
                || destinationId == R.id.userPickerFragment
                || destinationId == R.id.createGroupFragment
                || destinationId == R.id.groupInfoFragment
                || destinationId == R.id.blockedUsersFragment
                || destinationId == R.id.verifyEmailFragment;
    }

    private void updateBottomNavVisibility(int destinationId) {
        binding.bottomNavigation.setVisibility(isMainDestination(destinationId)
                ? View.VISIBLE
                : View.GONE);
    }

    private int currentDestinationId() {
        return navController.getCurrentDestination() != null
                ? navController.getCurrentDestination().getId()
                : 0;
    }

    /**
     * Signed in: keep unverified accounts on the verification gate, otherwise send entry screens
     * to the main tabs. The splash screen routes itself and is left alone.
     */
    private void onAuthenticated() {
        startUnreadBadge();
        int current = currentDestinationId();
        if (current == 0 || current == R.id.splashFragment || current == R.id.onboardingFragment) {
            return;
        }
        boolean verified;
        try {
            verified = AuthManager.getInstance().isEmailVerified();
        } catch (IllegalStateException notConfigured) {
            return;
        }
        if (!verified) {
            if (current != R.id.verifyEmailFragment) {
                navigateTo(R.id.verifyEmailFragment);
            }
            return;
        }
        if (current == R.id.authFragment || current == R.id.verifyEmailFragment) {
            navigateTo(R.id.homeFragment);
        }
        requestNotificationPermission();
    }

    /** Signed out: leave any signed-in screen for the auth screen. */
    private void onUnauthenticated() {
        stopUnreadBadge();
        int current = currentDestinationId();
        if (current == 0) return;
        if (isSignedInDestination(current) || emailVerificationScreenVisible) {
            navigateTo(R.id.authFragment);
        }
    }

    private void navigateTo(int destinationId) {
        if (currentDestinationId() == destinationId) return;
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .setLaunchSingleTop(true)
                .build();
        navController.navigate(destinationId, null, options);
    }

    /**
     * Reads the profile once per main-screen visit and ends the session when the account has been
     * suspended by an administrator. The client only mirrors the decision; Firestore rules are the
     * authority for every write.
     */
    private void enforceAccountStatus() {
        if (accountStatusCheckInFlight || suspendedDialogShown) return;
        final String uid;
        try {
            uid = AuthManager.getInstance().getCurrentUserId();
        } catch (IllegalStateException notConfigured) {
            return;
        }
        if (uid == null) return;
        accountStatusCheckInFlight = true;
        UserRepository.getInstance().getProfile(uid, (profile, error) -> {
            accountStatusCheckInFlight = false;
            if (error != null || profile == null || !profile.isSuspended()) return;
            if (isFinishing() || isDestroyed() || suspendedDialogShown) return;
            suspendedDialogShown = true;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.account_suspended_title)
                    .setMessage(R.string.account_suspended)
                    .setCancelable(false)
                    .setPositiveButton(R.string.btn_sign_out, (dialog, which) -> {
                        suspendedDialogShown = false;
                        AuthManager.getInstance().signOut();
                    })
                    .show();
        });
    }

    /** Called by the verification gate so signing out returns to the auth screen. */
    public void setEmailVerificationScreenVisible(boolean visible) {
        emailVerificationScreenVisible = visible;
    }

    /** Asks for POST_NOTIFICATIONS on Android 13+ once the user is signed in. */
    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        AuthManager authManager;
        try {
            authManager = AuthManager.getInstance();
        } catch (IllegalStateException notConfigured) {
            return;
        }
        if (!authManager.isSignedIn()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            authManager.registerFcmToken(null);
            return;
        }
        if (!notificationPermissionRequested) {
            notificationPermissionRequested = true;
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null || !intent.hasExtra("screen")) return;
        String screen = intent.getStringExtra("screen");
        int current = currentDestinationId();
        if (screen == null || !isSignedInDestination(current)) return;
        if (current == R.id.verifyEmailFragment) return;
        if ("chat".equals(screen)) {
            String conversationId = intent.getStringExtra("conversationId");
            if (conversationId == null) return;
            Bundle args = new Bundle();
            args.putString("conversationId", conversationId);
            navController.navigate(R.id.chatFragment, args);
            return;
        }
        int destination = R.id.homeFragment;
        if ("events".equals(screen)) destination = R.id.eventsFragment;
        else if ("groups".equals(screen)) destination = R.id.groupsFragment;
        else if ("profile".equals(screen)) destination = R.id.profileFragment;
        if (current != destination) navController.navigate(destination);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    /**
     * Keeps the Chats tab badge in sync with the unread counters the messaging module writes on
     * each conversation document.
     */
    private void startUnreadBadge() {
        if (unreadRegistration != null) return;
        try {
            if (!AuthManager.getInstance().isSignedIn()) return;
        } catch (IllegalStateException notConfigured) {
            return;
        }
        unreadRegistration = MessagingRepository.getInstance()
                .listenToUnreadTotal(this::updateUnreadBadge);
    }

    private void stopUnreadBadge() {
        if (unreadRegistration != null) {
            unreadRegistration.remove();
            unreadRegistration = null;
        }
        if (binding != null) {
            binding.bottomNavigation.removeBadge(R.id.conversationsFragment);
        }
    }

    private void updateUnreadBadge(long total) {
        if (binding == null) return;
        if (total <= 0) {
            binding.bottomNavigation.removeBadge(R.id.conversationsFragment);
            return;
        }
        BadgeDrawable badge = binding.bottomNavigation
                .getOrCreateBadge(R.id.conversationsFragment);
        badge.setVisible(true);
        badge.setNumber((int) Math.min(total, 99));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUnreadBadge();
    }
}
