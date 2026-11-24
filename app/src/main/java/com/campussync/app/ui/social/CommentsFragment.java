package com.campussync.app.ui.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentCommentsBinding;
import com.campussync.app.model.Comment;
import com.google.android.material.snackbar.Snackbar;

/** Comment thread for a post: reply, delete your own and post new comments. */
public class CommentsFragment extends Fragment {

    private FragmentCommentsBinding binding;
    private CommentsViewModel viewModel;
    private CommentAdapter adapter;
    private Comment replyTarget;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CommentsViewModel.class);
        String postId = getArguments() != null ? getArguments().getString("postId") : null;
        viewModel.init(postId);
        viewModel.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCommentsBinding.inflate(inflater, container, false);
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

        binding.btnCancelReply.setOnClickListener(v -> clearReplyTarget());
        binding.btnSend.setOnClickListener(v -> send());

        viewModel.getComments().observe(getViewLifecycleOwner(), comments -> {
            adapter.submitList(comments);
            boolean empty = comments == null || comments.isEmpty();
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
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

    private void send() {
        String text = binding.etComment.getText() != null
                ? binding.etComment.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        binding.etComment.setText("");
        viewModel.addComment(text, replyTarget != null ? replyTarget.getId() : null);
    }

    private void clearReplyTarget() {
        replyTarget = null;
        binding.replyBanner.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
