package com.campussync.app.ui.social;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentFeedBinding;
import com.campussync.app.firebase.SocialRepository;
import com.campussync.app.model.Post;
import com.campussync.app.model.Story;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/**
 * Social tab: active stories, campus / following / trending feeds, reactions and a compose FAB.
 */
public class FeedFragment extends Fragment {

    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;
    private static final int MENU_REPORT = 3;

    private FragmentFeedBinding binding;
    private FeedViewModel viewModel;
    private StoriesViewModel storiesViewModel;
    private PostAdapter adapter;
    private StoryAdapter storyAdapter;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) storiesViewModel.upload(uri, Story.TYPE_IMAGE);
            });

    private final ActivityResultLauncher<String> videoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) storiesViewModel.upload(uri, Story.TYPE_VIDEO);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(FeedViewModel.class);
        storiesViewModel = new ViewModelProvider(this).get(StoriesViewModel.class);
        storiesViewModel.start();
        viewModel.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFeedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new PostAdapter(new PostAdapter.Listener() {
            @Override
            public void onLike(Post post) {
                viewModel.toggleLike(post);
            }

            @Override
            public void onComment(Post post) {
                openComments(post);
            }

            @Override
            public void onShare(Post post) {
                sharePost(post);
            }

            @Override
            public void onBookmark(Post post) {
                viewModel.toggleBookmark(post);
            }

            @Override
            public void onMore(Post post, View anchor) {
                showPostMenu(post, anchor);
            }

            @Override
            public void onAuthor(Post post) {
                openAuthor(post);
            }
        });
        binding.rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPosts.setAdapter(adapter);
        binding.rvPosts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && !recyclerView.canScrollVertically(1)) viewModel.loadMore();
            }
        });

        storyAdapter = new StoryAdapter(new StoryAdapter.Listener() {
            @Override
            public void onAddStory() {
                showStoryPicker();
            }

            @Override
            public void onStory(Story story) {
                openStory(story);
            }
        });
        binding.rvStories.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvStories.setAdapter(storyAdapter);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_boards) {
                Navigation.findNavController(view).navigate(R.id.boardsFragment);
                return true;
            }
            return false;
        });

        binding.chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipFollowing) {
                viewModel.switchMode(SocialRepository.MODE_FOLLOWING);
            } else if (id == R.id.chipTrending) {
                viewModel.switchMode(SocialRepository.MODE_TRENDING);
            } else {
                viewModel.switchMode(SocialRepository.MODE_COLLEGE);
            }
        });

        binding.swipeRefresh.setColorSchemeResources(R.color.primary);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
            binding.swipeRefresh.setRefreshing(false);
        });

        binding.fabCreatePost.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.createPostFragment));
        binding.btnRetry.setOnClickListener(v -> viewModel.refresh());
        binding.btnRetryError.setOnClickListener(v -> viewModel.refresh());

        observe();
    }

    private void observe() {
        viewModel.getPosts().observe(getViewLifecycleOwner(), posts ->
                adapter.submitList(posts != null ? new java.util.ArrayList<>(posts)
                        : new java.util.ArrayList<>()));

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.loadingOverlay.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getEmpty().observe(getViewLifecycleOwner(), empty -> {
            boolean isEmpty = Boolean.TRUE.equals(empty);
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.tvEmpty.setText(SocialRepository.MODE_FOLLOWING.equals(viewModel.getMode())
                    ? R.string.following_feed_empty : R.string.empty_feed);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            boolean hasError = message != null && !message.isEmpty();
            binding.errorBanner.setVisibility(hasError ? View.VISIBLE : View.GONE);
            if (hasError) binding.tvError.setText(message);
        });

        viewModel.getMessageRes().observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            Snackbar.make(binding.getRoot(), res, Snackbar.LENGTH_SHORT).show();
            viewModel.clearMessage();
        });

        storiesViewModel.getStories().observe(getViewLifecycleOwner(), stories ->
                storyAdapter.setStories(stories));

        storiesViewModel.getUploading().observe(getViewLifecycleOwner(), uploading ->
                binding.loadingStory.setVisibility(Boolean.TRUE.equals(uploading)
                        ? View.VISIBLE : View.GONE));

        storiesViewModel.getMessageRes().observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            Snackbar.make(binding.getRoot(), res, Snackbar.LENGTH_SHORT).show();
            storiesViewModel.clearMessage();
        });

        storiesViewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            storiesViewModel.clearError();
        });
    }

    private void showStoryPicker() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.story_pick_title)
                .setItems(new CharSequence[]{
                        getString(R.string.story_pick_image),
                        getString(R.string.story_pick_video)
                }, (dialog, which) -> {
                    if (which == 0) {
                        imagePicker.launch("image/*");
                    } else {
                        videoPicker.launch("video/*");
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openStory(Story story) {
        if (story.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("storyId", story.getId());
        args.putString("authorId", story.getAuthorId());
        Navigation.findNavController(requireView()).navigate(R.id.storyViewerFragment, args);
    }

    private void openComments(Post post) {
        if (post.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("postId", post.getId());
        Navigation.findNavController(requireView()).navigate(R.id.commentsFragment, args);
    }

    private void openAuthor(Post post) {
        if (post.isAnonymous() || post.getAuthorId() == null) return;
        Bundle args = new Bundle();
        args.putString("uid", post.getAuthorId());
        Navigation.findNavController(requireView()).navigate(R.id.userProfileFragment, args);
    }

    private void sharePost(Post post) {
        if (post.getId() == null) return;
        String body = post.hasText() ? post.getContent() : getString(R.string.title_social);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.post_share_text, body));
        startActivity(Intent.createChooser(send, getString(R.string.action_share)));
        viewModel.share(post);
    }

    private void showPostMenu(final Post post, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        if (post.isOwnedByMe()) {
            popup.getMenu().add(0, MENU_EDIT, 0, R.string.action_edit_post);
            popup.getMenu().add(0, MENU_DELETE, 1, R.string.action_delete);
        }
        popup.getMenu().add(0, MENU_REPORT, 2, R.string.action_report);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_EDIT) {
                Bundle args = new Bundle();
                args.putString("postId", post.getId());
                args.putString("existingContent", post.getContent());
                Navigation.findNavController(requireView())
                        .navigate(R.id.createPostFragment, args);
                return true;
            }
            if (item.getItemId() == MENU_DELETE) {
                confirmDelete(post);
                return true;
            }
            if (item.getItemId() == MENU_REPORT) {
                ReportDialogHelper.show(this, com.campussync.app.model.ContentReport.TYPE_POST,
                        post.getId(), ReportDialogHelper.label(post.getContent()));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(final Post post) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete_post_title)
                .setMessage(R.string.confirm_delete_post_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        viewModel.deletePost(post, error -> {
                            if (binding == null) return;
                            Snackbar.make(binding.getRoot(),
                                    error == null ? R.string.post_deleted : R.string.error_generic,
                                    Snackbar.LENGTH_SHORT).show();
                        }))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
