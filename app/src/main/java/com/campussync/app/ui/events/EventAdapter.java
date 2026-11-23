package com.campussync.app.ui.events;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemEventCardBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.model.Event;

/**
 * RecyclerView adapter for event cards.
 * Highlights the joined state of the current user without extra queries.
 */
public class EventAdapter extends ListAdapter<Event, EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    private static final DiffUtil.ItemCallback<Event> DIFF_CALLBACK = new DiffUtil.ItemCallback<Event>() {
        @Override
        public boolean areItemsTheSame(@NonNull Event oldItem, @NonNull Event newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Event oldItem, @NonNull Event newItem) {
            return java.util.Objects.equals(oldItem.getTitle(), newItem.getTitle())
                    && java.util.Objects.equals(oldItem.getLocation(), newItem.getLocation())
                    && java.util.Objects.equals(oldItem.getStartDate(), newItem.getStartDate())
                    && java.util.Objects.equals(oldItem.getAttendeeIds(), newItem.getAttendeeIds());
        }
    };

    private final OnEventClickListener listener;

    public EventAdapter(OnEventClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEventCardBinding binding = ItemEventCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EventViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {

        private final ItemEventCardBinding binding;

        EventViewHolder(ItemEventCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Event event, OnEventClickListener listener) {
            binding.tvEventTitle.setText(event.getTitle() != null ? event.getTitle() : "");
            binding.tvEventDate.setText(event.getShortDate() != null ? event.getShortDate() : "Date to be announced");
            binding.tvEventLocation.setText(event.getLocation() != null ? event.getLocation() : "");

            String uid = AuthManager.getInstance().getCurrentUserId();
            boolean joined = uid != null && event.getAttendeeIds() != null
                    && event.getAttendeeIds().contains(uid);
            binding.chipJoined.setVisibility(joined ? View.VISIBLE : View.GONE);

            if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
                Glide.with(binding.ivEventImage)
                        .load(event.getImageUrl())
                        .placeholder(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(binding.ivEventImage);
            } else {
                binding.ivEventImage.setImageResource(R.drawable.ic_events);
            }

            if (listener != null) {
                binding.getRoot().setOnClickListener(v -> listener.onEventClick(event));
            }
        }
    }
}
