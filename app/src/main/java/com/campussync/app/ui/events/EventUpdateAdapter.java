package com.campussync.app.ui.events;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.databinding.ItemEventUpdateBinding;
import com.campussync.app.model.EventUpdate;
import com.campussync.app.util.TimeAgo;

import java.util.Objects;

/** Organizer change notes shown on the event detail screen. */
public class EventUpdateAdapter
        extends ListAdapter<EventUpdate, EventUpdateAdapter.UpdateViewHolder> {

    private static final DiffUtil.ItemCallback<EventUpdate> DIFF =
            new DiffUtil.ItemCallback<EventUpdate>() {
                @Override
                public boolean areItemsTheSame(@NonNull EventUpdate oldItem,
                                               @NonNull EventUpdate newItem) {
                    return Objects.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull EventUpdate oldItem,
                                                  @NonNull EventUpdate newItem) {
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                            && Objects.equals(oldItem.getBody(), newItem.getBody());
                }
            };

    public EventUpdateAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public UpdateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new UpdateViewHolder(ItemEventUpdateBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull UpdateViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class UpdateViewHolder extends RecyclerView.ViewHolder {

        private final ItemEventUpdateBinding binding;

        UpdateViewHolder(ItemEventUpdateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(EventUpdate update) {
            binding.tvUpdateTitle.setText(update.getTitle() != null ? update.getTitle() : "");
            binding.tvUpdateBody.setText(update.getBody() != null ? update.getBody() : "");
            binding.tvUpdateMeta.setText(update.getCreatedAt() != null
                    ? TimeAgo.format(update.getCreatedAt()) : "");
        }
    }
}
