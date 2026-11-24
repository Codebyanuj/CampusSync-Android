package com.campussync.app.ui.events;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemEventCardBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.model.Event;
import com.campussync.app.util.Countdown;

import java.util.Objects;

/**
 * RecyclerView adapter for event cards.
 *
 * <p>Shows category, countdown and capacity, and highlights the joined state of the current
 * user without extra queries (the counters live on the event document).
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
            return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                    && Objects.equals(oldItem.getLocation(), newItem.getLocation())
                    && Objects.equals(oldItem.getStartDate(), newItem.getStartDate())
                    && Objects.equals(oldItem.getEndDate(), newItem.getEndDate())
                    && Objects.equals(oldItem.getCategory(), newItem.getCategory())
                    && Objects.equals(oldItem.getStatus(), newItem.getStatus())
                    && oldItem.getCapacity() == newItem.getCapacity()
                    && oldItem.getRegistrationCount() == newItem.getRegistrationCount()
                    && Objects.equals(oldItem.getAttendeeIds(), newItem.getAttendeeIds());
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
            binding.tvEventDate.setText(event.getShortDate() != null
                    ? event.getShortDate() : "Date to be announced");
            binding.tvEventLocation.setText(event.getLocation() != null ? event.getLocation() : "");

            binding.chipCategory.setText(EventUi.categoryLabel(
                    binding.getRoot().getContext(), event.getCategoryOrDefault()));
            binding.chipCategory.setVisibility(View.VISIBLE);

            if (event.isCancelled()) {
                binding.tvCountdown.setText(R.string.event_cancelled_status);
                binding.tvCountdown.setTextColor(ContextCompat.getColor(
                        binding.getRoot().getContext(), R.color.error));
                binding.chipJoined.setVisibility(View.GONE);
            } else if (event.hasEnded()) {
                binding.tvCountdown.setText(R.string.event_ended_status);
                binding.tvCountdown.setTextColor(ContextCompat.getColor(
                        binding.getRoot().getContext(), R.color.on_surface_variant));
            } else {
                binding.tvCountdown.setText(Countdown.label(event.getStartDate(), event.getEndDate()));
                binding.tvCountdown.setTextColor(ContextCompat.getColor(
                        binding.getRoot().getContext(), R.color.secondary));
            }

            if (event.getCapacity() > 0) {
                binding.tvSeats.setVisibility(View.VISIBLE);
                binding.tvSeats.setText(binding.getRoot().getContext().getString(
                        R.string.seats_left, event.getRegistrationCount(), event.getCapacity()));
            } else {
                binding.tvSeats.setVisibility(View.GONE);
            }

            String uid = AuthManager.getInstance().getCurrentUserId();
            boolean joined = uid != null && event.isRegistered(uid) && !event.isCancelled();
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
