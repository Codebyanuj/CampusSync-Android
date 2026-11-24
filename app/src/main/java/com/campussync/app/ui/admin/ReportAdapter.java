package com.campussync.app.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.ItemReportBinding;
import com.campussync.app.model.ContentReport;
import com.campussync.app.util.TimeAgo;

import java.util.Objects;

/** Report cards for the administrator queue with resolve / dismiss / reveal actions. */
public class ReportAdapter extends ListAdapter<ContentReport, ReportAdapter.ReportViewHolder> {

    public interface Listener {
        void onResolve(ContentReport report);
        void onDismiss(ContentReport report);
        void onReveal(ContentReport report);
    }

    private static final DiffUtil.ItemCallback<ContentReport> DIFF =
            new DiffUtil.ItemCallback<ContentReport>() {
                @Override
                public boolean areItemsTheSame(@NonNull ContentReport oldItem,
                                               @NonNull ContentReport newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ContentReport oldItem,
                                                  @NonNull ContentReport newItem) {
                    return Objects.equals(oldItem.getStatus(), newItem.getStatus())
                            && Objects.equals(oldItem.getAttributedAuthorName(),
                            newItem.getAttributedAuthorName());
                }
            };

    private final Listener listener;

    public ReportAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReportBinding binding = ItemReportBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ReportViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {

        private final ItemReportBinding binding;

        ReportViewHolder(ItemReportBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final ContentReport report, final Listener listener) {
            String type = typeLabel(report.getTargetType());
            String label = report.getTargetLabel() != null ? report.getTargetLabel() : "";
            binding.tvTarget.setText(binding.getRoot().getContext()
                    .getString(R.string.report_target, type, label));
            binding.tvReason.setText(report.getReason());

            boolean hasDetails = report.getDetails() != null
                    && !report.getDetails().trim().isEmpty();
            binding.tvDetails.setVisibility(hasDetails ? View.VISIBLE : View.GONE);
            binding.tvDetails.setText(hasDetails ? report.getDetails() : "");

            binding.tvReporter.setText(TimeAgo.format(report.getCreatedAt()));

            boolean attributed = report.getAttributedAuthorName() != null;
            binding.tvAuthor.setVisibility(attributed ? View.VISIBLE : View.GONE);
            if (attributed) {
                binding.tvAuthor.setText(binding.getRoot().getContext()
                        .getString(R.string.author_revealed, report.getAttributedAuthorName()));
            }

            binding.btnReveal.setOnClickListener(v -> listener.onReveal(report));
            binding.btnResolve.setOnClickListener(v -> listener.onResolve(report));
            binding.btnDismiss.setOnClickListener(v -> listener.onDismiss(report));
        }

        private String typeLabel(String targetType) {
            android.content.Context context = binding.getRoot().getContext();
            if (ContentReport.TYPE_POST.equals(targetType)) {
                return context.getString(R.string.report_type_post);
            }
            if (ContentReport.TYPE_DISCUSSION.equals(targetType)) {
                return context.getString(R.string.report_type_discussion);
            }
            if (ContentReport.TYPE_STORY.equals(targetType)
                    || ContentReport.TYPE_STORY_REPLY.equals(targetType)) {
                return context.getString(R.string.report_type_story);
            }
            if (ContentReport.TYPE_USER.equals(targetType)) {
                return context.getString(R.string.report_type_user);
            }
            if (ContentReport.TYPE_MESSAGE.equals(targetType)) {
                return context.getString(R.string.report_type_message);
            }
            return context.getString(R.string.report_type_comment);
        }
    }
}
