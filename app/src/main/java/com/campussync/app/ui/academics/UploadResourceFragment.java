package com.campussync.app.ui.academics;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentUploadResourceBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.AcademicResource;
import com.campussync.app.model.UserProfile;
import com.campussync.app.util.UploadValidator;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

/**
 * Upload flow: pick a file, validate it locally, then let the repository upload it to Storage and
 * write the library record. The same limits are enforced again by the Storage and Firestore rules.
 */
public class UploadResourceFragment extends Fragment {

    private FragmentUploadResourceBinding binding;
    private UploadResourceViewModel viewModel;

    private Uri pickedUri;
    private String pickedName;
    private String pickedMime;
    private long pickedSize;
    private String myCollege;

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) onFilePicked(uri);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(UploadResourceViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUploadResourceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.btnPickFile.setOnClickListener(v -> filePicker.launch(new String[]{"*/*"}));
        binding.btnPublish.setOnClickListener(v -> publish());
        binding.chipResourceType.setOnCheckedStateChangeListener((group, checkedIds) ->
                binding.tvFileHint.setText(checkedIds.isEmpty()
                        ? R.string.upload_pick_first
                        : R.string.upload_pick_replacement));

        viewModel.getUploading().observe(getViewLifecycleOwner(), uploading -> {
            boolean busy = Boolean.TRUE.equals(uploading);
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnPublish.setEnabled(!busy);
            binding.btnPickFile.setEnabled(!busy);
        });
        viewModel.getProgress().observe(getViewLifecycleOwner(), percent -> {
            if (percent == null) return;
            binding.progressBar.setProgress(percent);
            binding.tvProgress.setText(getString(R.string.upload_progress, percent));
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearMessage();
        });
        viewModel.getUploaded().observe(getViewLifecycleOwner(), uploaded -> {
            if (!Boolean.TRUE.equals(uploaded)) return;
            Snackbar.make(binding.getRoot(), R.string.upload_done, Snackbar.LENGTH_LONG).show();
            Navigation.findNavController(view).navigateUp();
        });

        prefillFromProfile();
    }

    /** Starting point for the subject folder: the uploader's own department and semester. */
    private void prefillFromProfile() {
        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid == null) return;
        UserRepository.getInstance().getProfile(uid, (profile, error) -> {
            if (error != null || profile == null || binding == null) return;
            myCollege = profile.getCollege();
            if (text(binding.etDepartment).isEmpty() && profile.getDepartment() != null) {
                binding.etDepartment.setText(profile.getDepartment());
            }
            if (text(binding.etSemester).isEmpty() && profile.getSemester() > 0) {
                binding.etSemester.setText(String.valueOf(profile.getSemester()));
            }
        });
    }

    private void onFilePicked(Uri uri) {
        pickedUri = uri;
        pickedMime = requireContext().getContentResolver().getType(uri);
        pickedName = null;
        pickedSize = -1;

        Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (nameIndex >= 0) pickedName = cursor.getString(nameIndex);
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        pickedSize = cursor.getLong(sizeIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        if (pickedName == null) pickedName = uri.getLastPathSegment();
        if (pickedSize <= 0) pickedSize = lengthOf(uri);

        String error = UploadValidator.validate(pickedName, pickedMime, pickedSize);
        if (error != null) {
            pickedUri = null;
            binding.tvFileName.setText(R.string.upload_no_file);
            Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
            return;
        }
        binding.tvFileName.setText(getString(R.string.upload_selected, pickedName,
                UploadValidator.formatSize(pickedSize)));
        binding.tvFileHint.setText(R.string.upload_pick_replacement);
    }

    /** The picker sometimes reports no size; read the descriptor instead. */
    private long lengthOf(Uri uri) {
        try (android.content.res.AssetFileDescriptor descriptor =
                     requireContext().getContentResolver().openAssetFileDescriptor(uri, "r")) {
            return descriptor != null ? descriptor.getLength() : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void publish() {
        String title = text(binding.etTitle);
        String subject = text(binding.etSubject);
        if (pickedUri == null) {
            Snackbar.make(binding.getRoot(), R.string.upload_pick_first, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (title.isEmpty()) {
            binding.etTitle.setError(getString(R.string.error_title_required));
            return;
        }
        if (subject.isEmpty()) {
            binding.etSubject.setError(getString(R.string.error_subject_required));
            return;
        }

        AcademicResource resource = new AcademicResource();
        resource.setTitle(title);
        resource.setDescription(text(binding.etDescription));
        resource.setSubject(subject);
        resource.setSubjectLower(subject.toLowerCase(Locale.getDefault()));
        resource.setDepartment(text(binding.etDepartment));
        resource.setSemester(parseSemester());
        resource.setCollege(myCollege);
        resource.setType(typeForChip(binding.chipResourceType.getCheckedChipId()));
        resource.setFileName(pickedName);
        resource.setFileSize(pickedSize);
        resource.setMimeType(UploadValidator.mimeFor(pickedName, pickedMime));
        resource.setTags(AcademicUi.parseSubjects(text(binding.etTags)));
        viewModel.publish(resource, pickedUri);
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

    private static String typeForChip(int chipId) {
        if (chipId == R.id.chipUploadPdf) return AcademicResource.TYPE_PDF;
        if (chipId == R.id.chipUploadSlides) return AcademicResource.TYPE_SLIDES;
        if (chipId == R.id.chipUploadPaper) return AcademicResource.TYPE_QUESTION_PAPER;
        return AcademicResource.TYPE_NOTES;
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
