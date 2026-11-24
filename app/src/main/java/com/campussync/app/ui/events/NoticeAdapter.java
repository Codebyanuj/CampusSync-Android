package com.campussync.app.ui.events;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.databinding.ItemNoticeBinding;
import com.campussync.app.model.CollegeNotice;
import com.campussync.app.util.TimeAgo;

import java.util.Objects;

/** College notices at the top of the radar. */
public class NoticeAdapter extends ListAdapter<CollegeNotice, NoticeAdapter.NoticeViewHolder> {

    private static final DiffUtil.ItemCallback<CollegeNotice> DIFF =
            new DiffUtil.ItemCallback<CollegeNotice>() {
                @Override
                public boolean areItemsTheSame(@NonNull CollegeNotice oldItem,
                                               @NonNull CollegeNotice newItem) {
                    return Objects.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull CollegeNotice oldItem,
                                                  @NonNull CollegeNotice newItem) {
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                            && Objects.equals(oldItem.getBody(), newItem.getBody())
                            && Objects.equals(oldItem.getCollege(), newItem.getCollege())
                            && Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt())
                            && oldItem.isPinned() == newItem.isPinned();
                }
            };

    public NoticeAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public NoticeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new NoticeViewHolder(ItemNoticeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull NoticeViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class NoticeViewHolder extends RecyclerView.ViewHolder {

        private final ItemNoticeBinding binding;

        NoticeViewHolder(ItemNoticeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CollegeNotice notice) {
            binding.tvTitle.setText(notice.getTitle() != null ? notice.getTitle() : "");
            binding.tvBody.setText(notice.getBody() != null ? notice.getBody() : "");
            binding.chipPinned.setVisibility(notice.isPinned() ? View.VISIBLE : View.GONE);

            StringBuilder meta = new StringBuilder();
            if (notice.getCollege() != null) meta.append(notice.getCollege());
            if (notice.getAuthorName() != null) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(notice.getAuthorName());
            }
            if (notice.getCreatedAt() != null) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(TimeAgo.format(notice.getCreatedAt()));
            }
            binding.tvMeta.setText(meta);
        }
    }
}
