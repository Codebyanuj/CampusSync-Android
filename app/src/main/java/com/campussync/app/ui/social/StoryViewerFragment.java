package com.campussync.app.ui.social;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.FragmentStoryViewerBinding;
import com.campussync.app.model.Story;
import com.campussync.app.util.TimeAgo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen story viewer: progress segments, tap left/right navigation, view tracking,
 * replies and deletion of your own stories.
 */
public class StoryViewerFragment extends Fragment {

    private static final long IMAGE_DURATION_MS = 5000L;

    private FragmentStoryViewerBinding binding;
    private StoriesViewModel viewModel;
    private final List<Story> queue = new ArrayList<>();
    private final List<LinearProgressIndicator> segments = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ValueAnimator animator;
    private boolean suppressAdvance;
    private boolean built;
    private int index;
    private String startStoryId;
    private String authorIdFilter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(StoriesViewModel.class);
        viewModel.start();
        Bundle args = getArguments();
        if (args != null) {
            startStoryId = args.getString("storyId");
            authorIdFilter = args.getString("authorId");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStoryViewerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnClose.setOnClickListener(v -> close());
        binding.btnDelete.setOnClickListener(v -> confirmDelete());
        binding.btnSendReply.setOnClickListener(v -> sendReply());
        binding.ivStory.setOnTouchListener(this::onMediaTouch);
        binding.videoStory.setOnTouchListener(this::onMediaTouch);

        viewModel.getStories().observe(getViewLifecycleOwner(), this::onStoriesChanged);

        viewModel.getMessageRes().observe(getViewLifecycleOwner(), res -> {
            if (res == null || binding == null) return;
            Snackbar.make(binding.getRoot(), res, Snackbar.LENGTH_SHORT).show();
            viewModel.clearMessage();
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty() || binding == null) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });
    }

    private void onStoriesChanged(List<Story> all) {
        if (binding == null) return;
        String currentId = index >= 0 && index < queue.size() ? queue.get(index).getId() : startStoryId;

        List<Story> next = buildQueue(all);
        queue.clear();
        queue.addAll(next);
        if (queue.isEmpty()) {
            close();
            return;
        }
        int found = indexOfId(currentId);
        if (found >= 0) {
            index = found;
        } else if (!built) {
            index = 0;
        } else if (index >= queue.size()) {
            index = queue.size() - 1;
        }
        built = true;
        showCurrent();
    }

    private List<Story> buildQueue(List<Story> all) {
        String author = authorIdFilter;
        if (all == null) return new ArrayList<>();
        if (author == null && startStoryId != null) {
            for (Story story : all) {
                if (startStoryId.equals(story.getId())) {
                    author = story.getAuthorId();
                    break;
                }
            }
        }
        List<Story> filtered = new ArrayList<>();
        for (Story story : all) {
            if (author == null || author.equals(story.getAuthorId())) filtered.add(story);
        }
        filtered.sort((a, b) -> Long.compare(created(a), created(b)));
        return filtered;
    }

    private static long created(Story story) {
        return story.getCreatedAt() != null ? story.getCreatedAt().getTime() : 0L;
    }

    private int indexOfId(String id) {
        if (id == null) return -1;
        for (int i = 0; i < queue.size(); i++) {
            if (id.equals(queue.get(i).getId())) return i;
        }
        return -1;
    }

    private void showCurrent() {
        cancelTimers();
        if (binding == null || queue.isEmpty()) return;
        final Story story = queue.get(index);

        binding.tvAuthor.setText(story.getAuthorName());
        String time = TimeAgo.format(story.getCreatedAt());
        if (story.isOwnedByMe()) {
            time = time + " · " + getString(R.string.story_views, story.getViewCount());
        }
        binding.tvTime.setText(time);
        binding.btnDelete.setVisibility(story.isOwnedByMe() ? View.VISIBLE : View.GONE);

        Glide.with(binding.ivAvatar)
                .load(story.getAuthorPhotoUrl())
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.ivAvatar);

        buildSegments();

        if (story.isVideo()) {
            binding.ivStory.setVisibility(View.GONE);
            binding.videoStory.setVisibility(View.VISIBLE);
            binding.progressStory.setVisibility(View.GONE);
            binding.videoStory.stopPlayback();
            binding.videoStory.setVideoURI(Uri.parse(story.getMediaUrl()));
            binding.videoStory.setOnPreparedListener(player -> {
                player.setLooping(false);
                binding.videoStory.start();
                if (index < segments.size()) segments.get(index).setIndeterminate(true);
            });
            binding.videoStory.setOnCompletionListener(player -> next());
        } else {
            binding.videoStory.stopPlayback();
            binding.videoStory.setVisibility(View.GONE);
            binding.ivStory.setVisibility(View.VISIBLE);
            Glide.with(binding.ivStory)
                    .load(story.getMediaUrl())
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(binding.ivStory);
            startImageProgress();
        }

        viewModel.markViewed(story.getId());
    }

    private void buildSegments() {
        binding.progressContainer.removeAllViews();
        segments.clear();
        for (int i = 0; i < queue.size(); i++) {
            LinearProgressIndicator indicator = new LinearProgressIndicator(requireContext());
            indicator.setMax(1000);
            indicator.setTrackColor(Color.argb(80, 255, 255, 255));
            indicator.setIndicatorColor(Color.WHITE);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(3), 1f);
            params.setMarginStart(dp(2));
            params.setMarginEnd(dp(2));
            binding.progressContainer.addView(indicator, params);
            segments.add(indicator);
            indicator.setProgress(i < index ? 1000 : 0);
        }
    }

    private void startImageProgress() {
        suppressAdvance = false;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(IMAGE_DURATION_MS);
        animator.addUpdateListener(animation -> {
            if (index < segments.size()) {
                segments.get(index).setProgress(
                        (int) (animation.getAnimatedFraction() * 1000));
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!suppressAdvance) next();
            }
        });
        animator.start();
    }

    private boolean onMediaTouch(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (event.getX() < view.getWidth() / 2f) {
                previous();
            } else {
                next();
            }
        }
        return true;
    }

    private void next() {
        cancelTimers();
        if (queue.isEmpty()) return;
        if (index < queue.size() - 1) {
            index++;
            showCurrent();
        } else {
            close();
        }
    }

    private void previous() {
        cancelTimers();
        if (index > 0) index--;
        showCurrent();
    }

    private void sendReply() {
        if (binding == null || queue.isEmpty()) return;
        String text = binding.etReply.getText() != null
                ? binding.etReply.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        final Story story = queue.get(index);
        viewModel.reply(story.getId(), text, () -> {
            if (binding != null) binding.etReply.setText("");
        });
    }

    private void confirmDelete() {
        if (queue.isEmpty()) return;
        final Story story = queue.get(index);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete_story_title)
                .setMessage(R.string.confirm_delete_story_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        viewModel.deleteStory(story.getId(), () -> {
                            if (binding == null) return;
                            if (index < queue.size()) queue.remove(index);
                            if (queue.isEmpty()) {
                                close();
                            } else {
                                if (index >= queue.size()) index = queue.size() - 1;
                                showCurrent();
                            }
                        }))
                .show();
    }

    private void cancelTimers() {
        suppressAdvance = true;
        handler.removeCallbacksAndMessages(null);
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void close() {
        cancelTimers();
        if (!isAdded() || getView() == null) return;
        Navigation.findNavController(requireView()).popBackStack();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onStop() {
        super.onStop();
        cancelTimers();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimers();
        if (binding != null) binding.videoStory.stopPlayback();
        binding = null;
    }
}
