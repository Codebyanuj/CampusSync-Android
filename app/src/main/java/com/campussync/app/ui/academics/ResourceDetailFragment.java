package com.campussync.app.ui.academics;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campussync.app.R;
import com.campussync.app.databinding.DialogRateResourceBinding;
import com.campussync.app.databinding.FragmentResourceDetailBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.model.AcademicResource;
import com.campussync.app.model.ResourceRating;
import com.campussync.app.ui.social.ReportDialogHelper;
import com.campussync.app.util.UploadValidator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Locale;

/**
 * One shared resource: the metadata, download, bookmark, star rating with written feedback,
 * reporting and (for the uploader) deletion.
 */
public class ResourceDetailFragment extends Fragment {

    private FragmentResourceDetailBinding binding;
    private ResourceDetailViewModel viewModel;
    private RatingAdapter ratingAdapter;
    private String resourceId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ResourceDetailViewModel.class);
        resourceId = getArguments() != null ? getArguments().getString("resourceId") : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentResourceDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ratingAdapter = new RatingAdapter();
        binding.rvRatings.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRatings.setAdapter(ratingAdapter);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_report_resource) {
                AcademicResource resource = viewModel.getResource().getValue();
                if (resource != null) {
                    ReportDialogHelper.show(this, "resource", resource.getId(),
                            resource.getTitle());
                }
                return true;
            }
            return false;
        });

        binding.btnDownload.setOnClickListener(v -> download());
        binding.btnRate.setOnClickListener(v -> showRatingDialog());
        binding.btnBookmark.setOnClickListener(v -> viewModel.toggleBookmark());
        binding.btnDelete.setOnClickListener(v -> confirmDelete());

        viewModel.getResource().observe(getViewLifecycleOwner(), this::bindResource);
        viewModel.getBookmarked().observe(getViewLifecycleOwner(), saved ->
                binding.btnBookmark.setText(Boolean.TRUE.equals(saved)
                        ? R.string.resource_saved : R.string.resource_save));
        viewModel.getRatings().observe(getViewLifecycleOwner(), this::bindRatings);
        viewModel.getMyRating().observe(getViewLifecycleOwner(), rating -> {
            if (rating != null && rating.getStars() > 0) {
                binding.btnRate.setText(R.string.resource_update_rating);
            }
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearMessage();
        });
        viewModel.getDeleted().observe(getViewLifecycleOwner(), deleted -> {
            if (!Boolean.TRUE.equals(deleted)) return;
            Snackbar.make(binding.getRoot(), R.string.resource_deleted, Snackbar.LENGTH_LONG).show();
            Navigation.findNavController(view).navigateUp();
        });

        viewModel.start(resourceId);
    }

    private void bindResource(AcademicResource resource) {
        if (resource == null) return;
        binding.tvTitle.setText(resource.getTitle() != null ? resource.getTitle() : "");
        binding.chipType.setText(AcademicUi.typeLabel(requireContext(), resource.getTypeOrDefault()));

        String subject = resource.getSubject();
        boolean hasSubject = subject != null && !subject.isEmpty();
        binding.chipSubject.setVisibility(hasSubject ? View.VISIBLE : View.GONE);
        if (hasSubject) binding.chipSubject.setText(subject);

        String organisation = AcademicUi.organisationLine(resource);
        binding.tvOrganisation.setText(organisation);
        binding.tvOrganisation.setVisibility(organisation.isEmpty() ? View.GONE : View.VISIBLE);

        binding.tvUploader.setText(getString(R.string.resource_uploaded_by,
                resource.getUploaderName() != null && !resource.getUploaderName().isEmpty()
                        ? resource.getUploaderName() : getString(R.string.anonymous_student)));

        String description = resource.getDescription();
        boolean hasDescription = description != null && !description.trim().isEmpty();
        binding.tvDescription.setText(hasDescription ? description.trim()
                : getString(R.string.resource_no_description));
        binding.tvDescription.setTextColor(requireContext().getColor(hasDescription
                ? R.color.on_surface : R.color.on_surface_variant));

        binding.tvFileName.setText(getString(R.string.resource_file_line,
                resource.getFileName() != null ? resource.getFileName() : "",
                UploadValidator.formatSize(resource.getFileSize())));

        String stats = getString(R.string.resource_downloads, resource.getDownloadCount());
        if (resource.getRatingCount() > 0) {
            stats = stats + " · " + String.format(Locale.getDefault(), "★ %.1f (%d)",
                    resource.getRatingAverage(), resource.getRatingCount());
        } else {
            stats = stats + " · " + getString(R.string.resource_not_rated);
        }
        binding.tvStats.setText(stats);

        String uid = AuthManager.getInstance().getCurrentUserId();
        binding.btnDelete.setVisibility(uid != null && uid.equals(resource.getUploaderId())
                ? View.VISIBLE : View.GONE);

        boolean hasFile = resource.getFileUrl() != null && !resource.getFileUrl().isEmpty();
        binding.btnDownload.setEnabled(hasFile);
    }

    private void bindRatings(List<ResourceRating> ratings) {
        ratingAdapter.submitList(ratings);
        boolean empty = ratings == null || ratings.isEmpty();
        binding.tvEmptyRatings.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvRatings.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void download() {
        AcademicResource resource = viewModel.getResource().getValue();
        if (resource == null || resource.getFileUrl() == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(resource.getFileUrl()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            viewModel.recordDownload();
        } catch (ActivityNotFoundException missingViewer) {
            Snackbar.make(binding.getRoot(), R.string.resource_no_viewer,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void showRatingDialog() {
        AcademicResource resource = viewModel.getResource().getValue();
        if (resource == null || !isAdded()) return;
        DialogRateResourceBinding dialog = DialogRateResourceBinding.inflate(getLayoutInflater());
        ResourceRating existing = viewModel.getMyRating().getValue();
        if (existing != null) {
            dialog.rbStars.setRating(existing.getStars());
            dialog.etFeedback.setText(existing.getFeedback());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.resource_rate_title)
                .setView(dialog.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.resource_rate_submit, (d, which) -> {
                    int stars = Math.round(dialog.rbStars.getRating());
                    if (stars < 1) {
                        Snackbar.make(binding.getRoot(), R.string.resource_rate_pick,
                                Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    viewModel.rate(stars, dialog.etFeedback.getText() != null
                            ? dialog.etFeedback.getText().toString().trim() : "");
                })
                .show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.resource_delete_title)
                .setMessage(R.string.resource_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.resource_delete_confirm, (dialog, which) ->
                        viewModel.deleteResource())
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
