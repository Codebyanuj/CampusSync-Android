package com.campussync.app.ui.academics;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.databinding.ItemResourceBinding;
import com.campussync.app.model.AcademicResource;
import com.campussync.app.util.UploadValidator;

/** Resource cards: type, title, subject/department/semester, rating and downloads. */
public class ResourceAdapter
        extends ListAdapter<AcademicResource, ResourceAdapter.ResourceViewHolder> {

    public interface OnResourceClick {
        void onResource(AcademicResource resource);
    }

    private static final DiffUtil.ItemCallback<AcademicResource> DIFF =
            new DiffUtil.ItemCallback<AcademicResource>() {
                @Override
                public boolean areItemsTheSame(@NonNull AcademicResource left,
                                               @NonNull AcademicResource right) {
                    return left.getId() != null && left.getId().equals(right.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AcademicResource left,
                                                  @NonNull AcademicResource right) {
                    return left.getTitle() != null && left.getTitle().equals(right.getTitle())
                            && left.getTypeOrDefault().equals(right.getTypeOrDefault())
                            && left.getSubject() != null
                            && left.getSubject().equals(right.getSubject())
                            && left.getSemester() == right.getSemester()
                            && left.getDownloadCount() == right.getDownloadCount()
                            && left.getRatingSum() == right.getRatingSum()
                            && left.getRatingCount() == right.getRatingCount();
                }
            };

    private final OnResourceClick listener;

    public ResourceAdapter(OnResourceClick listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ResourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemResourceBinding binding = ItemResourceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ResourceViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ResourceViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ResourceViewHolder extends RecyclerView.ViewHolder {

        private final ItemResourceBinding binding;

        ResourceViewHolder(ItemResourceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AcademicResource resource, OnResourceClick listener) {
            binding.tvResourceTitle.setText(resource.getTitle() != null
                    ? resource.getTitle() : "Untitled resource");
            binding.chipResourceType.setText(AcademicUi.typeLabel(binding.getRoot().getContext(),
                    resource.getTypeOrDefault()));
            binding.tvResourceSubject.setText(AcademicUi.organisationLine(resource));

            String rating = resource.getRatingCount() > 0
                    ? String.format(java.util.Locale.getDefault(), "★ %.1f (%d)",
                            resource.getRatingAverage(), resource.getRatingCount())
                    : "Not rated yet";
            binding.tvResourceRating.setText(rating);
            binding.tvResourceMeta.setText(binding.getRoot().getContext().getString(
                    com.campussync.app.R.string.resource_downloads,
                    resource.getDownloadCount())
                    + " · " + UploadValidator.formatSize(resource.getFileSize()));

            binding.getRoot().setOnClickListener(view -> {
                if (listener != null) listener.onResource(resource);
            });
        }
    }
}
