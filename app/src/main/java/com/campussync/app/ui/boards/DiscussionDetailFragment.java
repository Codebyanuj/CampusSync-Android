package com.campussync.app.ui.boards;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentDiscussionDetailBinding;
import com.campussync.app.model.Comment;
import com.campussync.app.model.ContentReport;
import com.campussync.app.model.Discussion;
import com.campussync.app.ui.social.CommentAdapter;
import com.campussync.app.ui.social.ReportDialogHelper;
import com.campussync.app.util.TimeAgo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/** One discussion thread: vote, comment, reply, report and delete your own discussion. */
public class DiscussionDetailFragment extends Fragment {

    private FragmentDiscussionDetailBinding binding;
    private DiscussionDetailViewModel viewModel;
    private CommentAdapter adapter;
    private Comment replyTarget;
    private Discussion currentDiscussion;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DiscussionDetailViewModel.class);
        Bundle args = getArguments();
        if (args != null) {
            viewModel.init(args.getString("boardId"), args.getString("discussionId"));
        }
        viewModel.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiscussionDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new CommentAdapter(new CommentAdapter.Listener() {
            @Override
            public void onReply(Comment comment) {
                replyTarget = comment;
                String name = comment.isAnonymous()
                        ? getString(R.string.anonymous_user) : comment.getAuthorName();
                binding.tvReplyingTo.setText(getString(R.string.replying_to, name));
                binding.replyBanner.setVisibility(View.VISIBLE);
                binding.etComment.requestFocus();
            }

            @Override
            public void onDelete(Comment comment) {
                viewModel.deleteComment(comment);
            }
        });
        binding.rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvComments.setAdapter(adapter);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

        binding.btnUpvote.setOnClickListener(v -> viewModel.vote(1L));
        binding.btnDownvote.setOnClickListener(v -> viewModel.vote(-1L));
        binding.btnCancelReply.setOnClickListener(v -> clearReplyTarget());
        binding.btnSend.setOnClickListener(v -> send());

        viewModel.getDiscussion().observe(getViewLifecycleOwner(), this::bindDiscussion);
        viewModel.getComments().observe(getViewLifecycleOwner(), comments -> {
            adapter.submitList(comments);
            binding.tvEmpty.setVisibility(comments == null || comments.isEmpty()
                    ? View.VISIBLE : View.GONE);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progress.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));
        viewModel.getMessageRes().observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            Snackbar.make(requireView(), res, Snackbar.LENGTH_SHORT).show();
            viewModel.clearMessage();
            clearReplyTarget();
        });
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });
    }

    private void bindDiscussion(Discussion discussion) {
        if (discussion == null || binding == null) return;
        currentDiscussion = discussion;
        binding.tvTitle.setText(discussion.getTitle());

        String author = discussion.isAnonymous()
                ? getString(R.string.anonymous_user) : discussion.getAuthorName();
        binding.tvMeta.setText((author != null ? author : "")
                + " · " + TimeAgo.format(discussion.getCreatedAt()));

        boolean hasBody = discussion.getBody() != null && !discussion.getBody().trim().isEmpty();
        binding.tvBody.setVisibility(hasBody ? View.VISIBLE : View.GONE);
        binding.tvBody.setText(hasBody ? discussion.getBody() : "");

        binding.tvScore.setText(String.valueOf(discussion.getScore()));
        binding.tvCommentCount.setText(getString(R.string.comments_short,
                discussion.getCommentCount()));

        MenuItem delete = binding.toolbar.getMenu().findItem(R.id.action_delete);
        if (delete != null) delete.setVisible(discussion.isOwnedByMe());
    }

    private boolean onMenuItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_report) {
            if (currentDiscussion != null) {
                ReportDialogHelper.show(this, ContentReport.TYPE_DISCUSSION,
                        currentDiscussion.getId(),
                        ReportDialogHelper.label(currentDiscussion.getTitle()));
            }
            return true;
        }
        if (item.getItemId() == R.id.action_delete) {
            confirmDelete();
            return true;
        }
        return false;
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete_discussion_title)
                .setMessage(R.string.confirm_delete_discussion_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        viewModel.deleteDiscussion(error -> {
                            if (binding == null) return;
                            Snackbar.make(binding.getRoot(),
                                    error == null ? R.string.discussion_deleted
                                            : R.string.error_generic,
                                    Snackbar.LENGTH_SHORT).show();
                            if (error == null) {
                                Navigation.findNavController(requireView()).navigateUp();
                            }
                        }))
                .show();
    }

    private void send() {
        String text = binding.etComment.getText() != null
                ? binding.etComment.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        binding.etComment.setText("");
        viewModel.addComment(text, replyTarget != null ? replyTarget.getId() : null);
    }

    private void clearReplyTarget() {
        replyTarget = null;
        if (binding != null) binding.replyBanner.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
