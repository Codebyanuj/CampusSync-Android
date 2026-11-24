package com.campussync.app.ui.events;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.DialogTextFormBinding;
import com.campussync.app.databinding.DialogTicketBinding;
import com.campussync.app.databinding.FragmentEventDetailBinding;
import com.campussync.app.model.Event;
import com.campussync.app.model.EventRegistration;
import com.campussync.app.model.EventUpdate;
import com.campussync.app.util.Countdown;
import com.campussync.app.util.QrCode;
import com.google.android.material.snackbar.Snackbar;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.List;

/**
 * Event detail: schedule, countdown, capacity, registration with a personal QR ticket,
 * organizer updates, and the organizer's attendance scanner.
 */
public class EventDetailFragment extends Fragment {

    private static final long TICK_MS = 30_000L;

    private FragmentEventDetailBinding binding;
    private EventDetailViewModel viewModel;
    private EventUpdateAdapter updatesAdapter;
    private final Handler ticker = new Handler(Looper.getMainLooper());

    private final Runnable tickAction = new Runnable() {
        @Override
        public void run() {
            Event event = viewModel != null ? viewModel.getEvent().getValue() : null;
            if (binding != null && event != null) {
                binding.tvCountdown.setText(event.isCancelled()
                        ? getString(R.string.event_cancelled_status)
                        : Countdown.label(event.getStartDate(), event.getEndDate()));
                ticker.postDelayed(this, TICK_MS);
            }
        }
    };

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    viewModel.checkIn(result.getContents());
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    startScan();
                } else {
                    Snackbar.make(binding.getRoot(), R.string.msg_camera_permission,
                            Snackbar.LENGTH_LONG).show();
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(EventDetailViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEventDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

        updatesAdapter = new EventUpdateAdapter();
        binding.rvUpdates.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUpdates.setAdapter(updatesAdapter);

        binding.btnRegister.setOnClickListener(v -> viewModel.register());
        binding.btnCancelRegistration.setOnClickListener(v -> confirmCancelRegistration());
        binding.btnViewTicket.setOnClickListener(v -> showTicket());

        observe();

        String eventId = getArguments() != null ? getArguments().getString("eventId") : null;
        viewModel.load(eventId);
        if (eventId == null) {
            binding.btnRegister.setEnabled(false);
            Snackbar.make(binding.getRoot(), "Missing event id", Snackbar.LENGTH_LONG).show();
        }
    }

    private boolean onMenuItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_scan_qr) {
            ensureCameraThenScan();
            return true;
        }
        if (id == R.id.action_participants) {
            openParticipants();
            return true;
        }
        if (id == R.id.action_post_update) {
            showPostUpdateDialog();
            return true;
        }
        if (id == R.id.action_cancel_event) {
            confirmCancelEvent();
            return true;
        }
        return false;
    }

    private void observe() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressLoading.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            bindEvent(event);
            updateMenu(event);
        });

        viewModel.getRegistration().observe(getViewLifecycleOwner(), registration -> {
            Event event = viewModel.getEvent().getValue();
            if (event != null) bindRegistrationState(event, registration);
        });

        viewModel.getUpdates().observe(getViewLifecycleOwner(), this::bindUpdates);

        viewModel.getBusy().observe(getViewLifecycleOwner(), busy -> {
            boolean inProgress = Boolean.TRUE.equals(busy);
            binding.btnRegister.setEnabled(!inProgress);
            binding.btnCancelRegistration.setEnabled(!inProgress);
        });

        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
            viewModel.clearMessage();
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });

        viewModel.getScanResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            Snackbar.make(binding.getRoot(), result.message,
                    result.success ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG).show();
            viewModel.clearScanResult();
        });
    }

    private void bindEvent(Event event) {
        binding.tvTitle.setText(event.getTitle() != null ? event.getTitle() : "");
        binding.tvDescription.setText(event.getDescription() != null ? event.getDescription() : "");
        binding.tvEventLocation.setText(event.getLocation() != null ? event.getLocation() : "");
        binding.tvEventDate.setText(EventUi.formatRange(event.getStartDate(), event.getEndDate()));

        binding.chipCategory.setText(EventUi.categoryLabel(requireContext(),
                event.getCategoryOrDefault()));
        if (event.getCollege() != null && !event.getCollege().isEmpty()) {
            binding.chipCollege.setText(event.isIntercollege()
                    ? event.getCollege() + " · intercollege" : event.getCollege());
            binding.chipCollege.setVisibility(View.VISIBLE);
        } else {
            binding.chipCollege.setVisibility(View.GONE);
        }

        String organizer = event.getOrganizerName();
        if (viewModel.isOrganizer()) {
            binding.tvOrganizer.setText("Hosted by you");
        } else {
            binding.tvOrganizer.setText(organizer != null ? "Hosted by " + organizer : "");
        }

        binding.tvStatus.setVisibility(event.isCancelled() ? View.VISIBLE : View.GONE);
        binding.tvCountdown.setText(event.isCancelled()
                ? getString(R.string.event_cancelled_status)
                : Countdown.label(event.getStartDate(), event.getEndDate()));

        if (event.getCapacity() > 0) {
            binding.tvCapacity.setText(getString(R.string.seats_left,
                    event.getRegistrationCount(), event.getCapacity()));
            binding.progressCapacity.setVisibility(View.VISIBLE);
            int progress = (int) Math.min(100,
                    (event.getRegistrationCount() * 100) / Math.max(1, event.getCapacity()));
            binding.progressCapacity.setProgress(progress);
        } else {
            binding.tvCapacity.setText(event.getRegistrationCount() + " registered");
            binding.progressCapacity.setVisibility(View.GONE);
        }

        if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(event.getImageUrl())
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(binding.ivEventImage);
        } else {
            binding.ivEventImage.setImageResource(R.drawable.ic_events);
        }

        binding.btnRegister.setVisibility(viewModel.isOrganizer() ? View.GONE : View.VISIBLE);
        bindRegistrationState(event, viewModel.getRegistration().getValue());

        ticker.removeCallbacks(tickAction);
        if (!event.hasEnded() && !event.isCancelled()) {
            ticker.postDelayed(tickAction, TICK_MS);
        }
    }

    private void bindRegistrationState(Event event, EventRegistration registration) {
        boolean registered = registration != null && registration.isRegistered();
        String uid = com.campussync.app.firebase.EventRepository.currentUidOrNull();
        boolean viaCounter = uid != null && event.isRegistered(uid);

        boolean showRegistered = registered || viaCounter;
        boolean showTicket = registered && registration.getQrToken() != null;

        if (viewModel.isOrganizer()) {
            binding.btnRegister.setVisibility(View.GONE);
            binding.btnViewTicket.setVisibility(View.GONE);
            binding.btnCancelRegistration.setVisibility(View.GONE);
            binding.tvRegistrationStatus.setVisibility(View.GONE);
            return;
        }

        binding.btnRegister.setVisibility(showRegistered ? View.GONE : View.VISIBLE);
        binding.btnViewTicket.setVisibility(showTicket ? View.VISIBLE : View.GONE);
        binding.btnCancelRegistration.setVisibility(showRegistered ? View.VISIBLE : View.GONE);
        binding.tvRegistrationStatus.setVisibility(showRegistered ? View.VISIBLE : View.GONE);
        binding.tvRegistrationStatus.setText(R.string.event_registered_status);

        if (event.isCancelled()) {
            binding.btnRegister.setVisibility(View.GONE);
            binding.tvRegistrationStatus.setVisibility(View.GONE);
        } else if (showRegistered) {
            // already handled above
        } else if (event.hasEnded()) {
            binding.btnRegister.setEnabled(false);
            binding.btnRegister.setText(R.string.event_ended_status);
        } else if (event.isFull()) {
            binding.btnRegister.setEnabled(false);
            binding.btnRegister.setText(R.string.event_full_status);
        } else {
            binding.btnRegister.setEnabled(true);
            binding.btnRegister.setText(R.string.btn_register_event);
        }
    }

    private void bindUpdates(List<EventUpdate> updates) {
        boolean hasUpdates = updates != null && !updates.isEmpty();
        binding.tvUpdatesTitle.setVisibility(hasUpdates ? View.VISIBLE : View.GONE);
        binding.rvUpdates.setVisibility(hasUpdates ? View.VISIBLE : View.GONE);
        updatesAdapter.submitList(updates);
    }

    private void updateMenu(Event event) {
        Menu menu = binding.toolbar.getMenu();
        boolean organizer = viewModel.isOrganizer();
        boolean cancelled = event.isCancelled();

        MenuItem scan = menu.findItem(R.id.action_scan_qr);
        MenuItem participants = menu.findItem(R.id.action_participants);
        MenuItem update = menu.findItem(R.id.action_post_update);
        MenuItem cancel = menu.findItem(R.id.action_cancel_event);

        if (scan != null) scan.setVisible(organizer && !cancelled);
        if (participants != null) participants.setVisible(organizer);
        if (update != null) update.setVisible(organizer && !cancelled);
        if (cancel != null) cancel.setVisible(organizer && !cancelled);
    }

    private void showTicket() {
        EventRegistration registration = viewModel.getRegistration().getValue();
        Event event = viewModel.getEvent().getValue();
        if (registration == null || registration.getQrToken() == null || event == null) {
            Snackbar.make(binding.getRoot(), "Your ticket is still being prepared",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        DialogTicketBinding ticket = DialogTicketBinding.inflate(getLayoutInflater());
        ticket.tvTicketEvent.setText(event.getTitle() != null ? event.getTitle() : "");
        String payload = QrCode.buildPayload(event.getId(), registration.getUid(),
                registration.getQrToken());
        android.graphics.Bitmap bitmap = QrCode.toBitmap(payload, 600);
        if (bitmap != null) ticket.ivQr.setImageBitmap(bitmap);
        ticket.tvTicketCode.setText(QrCode.shortCode(registration.getQrToken()));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setView(ticket.getRoot())
                .setPositiveButton(R.string.btn_close, null)
                .show();
    }

    private void ensureCameraThenScan() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startScan();
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        }
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.action_scan_attendance));
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        try {
            scanLauncher.launch(options);
        } catch (Exception error) {
            Snackbar.make(binding.getRoot(), R.string.error_camera, Snackbar.LENGTH_LONG).show();
        }
    }

    private void openParticipants() {
        if (viewModel.getEventId() == null) return;
        Bundle args = new Bundle();
        args.putString("eventId", viewModel.getEventId());
        Navigation.findNavController(requireView())
                .navigate(R.id.eventParticipantsFragment, args);
    }

    private void showPostUpdateDialog() {
        DialogTextFormBinding form = DialogTextFormBinding.inflate(getLayoutInflater());
        form.tilTitle.setHint(R.string.hint_form_title);
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext())
                .setTitle(R.string.action_post_update)
                .setView(form.getRoot())
                .setPositiveButton(R.string.btn_post, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String title = form.etTitle.getText() != null
                            ? form.etTitle.getText().toString().trim() : "";
                    String body = form.etBody.getText() != null
                            ? form.etBody.getText().toString().trim() : "";
                    if (title.isEmpty()) {
                        form.tilTitle.setError(getString(R.string.error_title_required));
                        return;
                    }
                    if (body.isEmpty()) {
                        form.tilBody.setError(getString(R.string.error_description_required));
                        return;
                    }
                    viewModel.postUpdate(title, body);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void confirmCancelRegistration() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.btn_cancel_registration)
                .setMessage(R.string.cancel_event_confirm_message)
                .setPositiveButton(R.string.btn_cancel_registration,
                        (dialog, which) -> viewModel.cancelRegistration())
                .setNegativeButton(R.string.btn_keep_event, null)
                .show();
    }

    private void confirmCancelEvent() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cancel_event_confirm_title)
                .setMessage(R.string.cancel_event_confirm_message)
                .setPositiveButton(R.string.action_cancel_event,
                        (dialog, which) -> viewModel.cancelEvent())
                .setNegativeButton(R.string.btn_keep_event, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ticker.removeCallbacks(tickAction);
        binding = null;
    }
}
