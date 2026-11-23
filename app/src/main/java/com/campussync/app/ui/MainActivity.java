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
import com.campussync.app.ui.auth.AuthViewModel;

/**
 * Single-activity host. Owns the NavController, bottom navigation and the auth gate:
 * signed-out users only see the auth screen; signed-in users see the main tabs.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private boolean notificationPermissionRequested;

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

        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                updateBottomNavVisibility(destination.getId()));

        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        authViewModel.getAuthState().observe(this, state -> {
            if (state == null) return;
            switch (state) {
                case AUTHENTICATED:
                    showMainContent();
                    break;
                case UNAUTHENTICATED:
                    showAuthScreen();
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
                || destinationId == R.id.eventsFragment
                || destinationId == R.id.groupsFragment
                || destinationId == R.id.profileFragment;
    }

    private void updateBottomNavVisibility(int destinationId) {
        binding.bottomNavigation.setVisibility(isMainDestination(destinationId)
                ? View.VISIBLE
                : View.GONE);
    }

    private void showMainContent() {
        if (navController.getCurrentDestination() == null) return;
        if (navController.getCurrentDestination().getId() == R.id.authFragment) {
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .setLaunchSingleTop(true)
                    .build();
            navController.navigate(R.id.homeFragment, null, options);
        }
        requestNotificationPermission();
    }

    private void showAuthScreen() {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.authFragment) {
            return;
        }
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build();
        navController.navigate(R.id.authFragment, null, options);
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
        if (screen == null || navController.getCurrentDestination() == null) return;
        if (navController.getCurrentDestination().getId() == R.id.authFragment) return;
        int destination = R.id.homeFragment;
        if ("events".equals(screen)) destination = R.id.eventsFragment;
        else if ("groups".equals(screen)) destination = R.id.groupsFragment;
        else if ("profile".equals(screen)) destination = R.id.profileFragment;
        navController.navigate(destination);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }
}
