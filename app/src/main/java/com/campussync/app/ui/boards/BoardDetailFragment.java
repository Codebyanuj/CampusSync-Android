package com.campussync.app.ui.boards;

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
import com.campussync.app.databinding.DialogCreateDiscussionBinding;
import com.campussync.app.databinding.FragmentBoardDetailBinding;
import com.campussync.app.model.Board;
import com.campussync.app.model.Discussion;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/** One board: description, discussion list, voting and the compose dialog. */
public class BoardDetailFragment extends Fragment {

    private FragmentBoardDetailBinding binding;
    private BoardDetailViewModel viewModel;
    private DiscussionAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BoardDetailViewModel.class);
        Bundle args = getArguments();
        if (args != null) viewModel.init(args.getString("boardId"));
        viewModel.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBoardDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String boardName = args != null ? args.getString("boardName") : null;
        binding.tvBoardName.setText(boardName != null ? boardName : getString(R.string.title_boards));

        adapter = new DiscussionAdapter(new DiscussionAdapter.Listener() {
            @Override
            public void onDiscussion(Discussion discussion) {
                openDiscussion(discussion);
            }

            @Override
            public void onVote(Discussion discussion, long value) {
                viewModel.vote(discussion, value);
            }
        });
        binding.rvDiscussions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDiscussions.setAdapter(adapter);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.fabCreateDiscussion.setOnClickListener(v -> showCreateDiscussionDialog());

        viewModel.getBoard().observe(getViewLifecycleOwner(), this::bindBoard);
        viewModel.getDiscussions().observe(getViewLifecycleOwner(), discussions -> {
            adapter.submitList(discussions);
            binding.tvEmpty.setVisibility(discussions == null || discussions.isEmpty()
                    ? View.VISIBLE : View.GONE);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progress.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });
    }

    private void bindBoard(Board board) {
        if (board == null || binding == null) return;
        binding.tvBoardName.setText(board.getName());
        String description = board.getDescription();
        binding.tvBoardDescription.setText(description != null ? description : "");
        binding.tvBoardDescription.setVisibility(description != null && !description.isEmpty()
                ? View.VISIBLE : View.GONE);
        binding.toolbar.setTitle(board.getName());
    }

    private void openDiscussion(Discussion discussion) {
        if (discussion.getId() == null || discussion.getBoardId() == null) return;
        Bundle args = new Bundle();
        args.putString("boardId", discussion.getBoardId());
        args.putString("discussionId", discussion.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.discussionDetailFragment, args);
    }

    private void showCreateDiscussionDialog() {
        final DialogCreateDiscussionBinding dialogBinding =
                DialogCreateDiscussionBinding.inflate(getLayoutInflater());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_create_discussion)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_create_discussion, (dialog, which) -> {
                    final String title = dialogBinding.etTitle.getText() != null
                            ? dialogBinding.etTitle.getText().toString().trim() : "";
                    if (title.isEmpty()) {
                        if (binding != null) {
                            Snackbar.make(binding.getRoot(),
                                    R.string.error_discussion_title_required,
                                    Snackbar.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    final String body = dialogBinding.etBody.getText() != null
                            ? dialogBinding.etBody.getText().toString().trim() : "";
                    viewModel.createDiscussion(title, body,
                            dialogBinding.switchAnonymous.isChecked(), error -> {
                                if (binding == null) return;
                                Snackbar.make(binding.getRoot(),
                                        error == null ? R.string.discussion_published
                                                : R.string.error_generic,
                                        Snackbar.LENGTH_SHORT).show();
                            });
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
