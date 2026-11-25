package com.campussync.app.ui.academics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentCreateDoubtBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.UserProfile;
import com.google.android.material.snackbar.Snackbar;

/** Asks a peer: title, description, subject and the course it belongs to. */
public class CreateDoubtFragment extends Fragment {

    private FragmentCreateDoubtBinding binding;
    private CreateDoubtViewModel viewModel;
    private String college;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CreateDoubtViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCreateDoubtBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.btnPost.setOnClickListener(v -> post());

        viewModel.getPosting().observe(getViewLifecycleOwner(), posting -> {
            boolean busy = Boolean.TRUE.equals(posting);
            binding.btnPost.setEnabled(!busy);
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearMessage();
        });
        viewModel.getPosted().observe(getViewLifecycleOwner(), posted -> {
            if (!Boolean.TRUE.equals(posted)) return;
            Snackbar.make(binding.getRoot(), R.string.doubt_posted, Snackbar.LENGTH_LONG).show();
            Navigation.findNavController(view).navigateUp();
        });

        prefillFromProfile();
    }

    private void prefillFromProfile() {
        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid == null) return;
        UserRepository.getInstance().getProfile(uid, (profile, error) -> {
            if (error != null || profile == null || binding == null) return;
            college = profile.getCollege();
            if (text(binding.etDepartment).isEmpty() && profile.getDepartment() != null) {
                binding.etDepartment.setText(profile.getDepartment());
            }
            if (text(binding.etSemester).isEmpty() && profile.getSemester() > 0) {
                binding.etSemester.setText(String.valueOf(profile.getSemester()));
            }
        });
    }

    private void post() {
        viewModel.post(text(binding.etTitle), text(binding.etBody), text(binding.etSubject),
                text(binding.etDepartment), parseSemester(), college);
    }

    private long parseSemester() {
        String raw = text(binding.etSemester);
        if (raw.isEmpty()) return 0;
        try {
            return Math.max(0, Math.min(12, Long.parseLong(raw)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String text(android.widget.EditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
