package com.campussync.app.ui.events;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a flat list of display names (attendees or group members).
 */
public class AttendeeAdapter extends RecyclerView.Adapter<AttendeeAdapter.NameViewHolder> {

    private final List<String> names = new ArrayList<>();

    public void submit(List<String> newNames) {
        names.clear();
        if (newNames != null) names.addAll(newNames);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        android.view.View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendee, parent, false);
        return new NameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NameViewHolder holder, int position) {
        holder.name.setText(names.get(position));
    }

    @Override
    public int getItemCount() {
        return names.size();
    }

    static class NameViewHolder extends RecyclerView.ViewHolder {
        final TextView name;

        NameViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvName);
        }
    }
}
