package com.campussync.app.ui.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.ItemStoryAddBinding;
import com.campussync.app.databinding.ItemStoryBinding;
import com.campussync.app.model.Story;

import java.util.ArrayList;
import java.util.List;

/** Story circles with a leading "your story" tile. */
public class StoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ADD = 0;
    private static final int TYPE_STORY = 1;

    public interface Listener {
        void onAddStory();
        void onStory(Story story);
    }

    private final List<Story> stories = new ArrayList<>();
    private final Listener listener;

    public StoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setStories(List<Story> next) {
        stories.clear();
        if (next != null) stories.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_ADD : TYPE_STORY;
    }

    @Override
    public int getItemCount() {
        return stories.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ADD) {
            ItemStoryAddBinding binding = ItemStoryAddBinding.inflate(inflater, parent, false);
            return new AddHolder(binding);
        }
        ItemStoryBinding binding = ItemStoryBinding.inflate(inflater, parent, false);
        return new StoryHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddHolder) {
            ((AddHolder) holder).bind(listener);
            return;
        }
        Story story = stories.get(position - 1);
        ((StoryHolder) holder).bind(story, listener);
    }

    static class AddHolder extends RecyclerView.ViewHolder {
        private final ItemStoryAddBinding binding;

        AddHolder(ItemStoryAddBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Listener listener) {
            binding.getRoot().setOnClickListener(v -> listener.onAddStory());
        }
    }

    static class StoryHolder extends RecyclerView.ViewHolder {
        private final ItemStoryBinding binding;

        StoryHolder(ItemStoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Story story, final Listener listener) {
            Glide.with(binding.ivStory)
                    .load(story.getMediaUrl())
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(binding.ivStory);
            String name = story.getAuthorName() != null ? story.getAuthorName() : "";
            int space = name.indexOf(' ');
            binding.tvStoryName.setText(space > 0 ? name.substring(0, space) : name);
            binding.getRoot().setOnClickListener(v -> listener.onStory(story));
            binding.getRoot().setOnLongClickListener(null);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof StoryHolder) {
            ((StoryHolder) holder).binding.ivStory.setImageDrawable(null);
        }
    }
}
