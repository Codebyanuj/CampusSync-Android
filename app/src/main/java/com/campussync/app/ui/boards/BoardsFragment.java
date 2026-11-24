package com.campussync.app.ui.boards;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.campussync.app.databinding.DialogCreateBoardBinding;
import com.campussync.app.databinding.FragmentBoardsBinding;
import com.campussync.app.model.Board;
import com.campussync.app.model.Discussion;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/** Boards tab: browse or search boards, or switch to trending discussions. */
public class BoardsFragment extends Fragment {

    private FragmentBoardsBinding binding;
    private BoardsViewModel viewModel;
    private BoardAdapter boardAdapter;
    private DiscussionAdapter discussionAdapter;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BoardsViewModel.class);
        viewModel.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBoardsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boardAdapter = new BoardAdapter(this::openBoard);
        discussionAdapter = new DiscussionAdapter(new DiscussionAdapter.Listener() {
            @Override
            public void onDiscussion(Discussion discussion) {
                openDiscussion(discussion);
            }

            @Override
            public void onVote(Discussion discussion, long value) {
                viewModel.vote(discussion, value);
            }
        });

        binding.rvBoards.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBoards.setAdapter(boardAdapter);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        binding.chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            viewModel.switchMode(checkedIds.get(0) == R.id.chipTrending
                    ? BoardsViewModel.MODE_TRENDING : BoardsViewModel.MODE_BOARDS);
            applyMode();
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
                pendingSearch = () -> viewModel.setQuery(s != null ? s.toString() : "");
                searchHandler.postDelayed(pendingSearch, 300);
            }
        });

        binding.fabCreateBoard.setOnClickListener(v -> showCreateBoardDialog());

        viewModel.getBoards().observe(getViewLifecycleOwner(), boards -> {
            if (viewModel.getMode() != BoardsViewModel.MODE_BOARDS) return;
            boardAdapter.submitList(boards);
            updateEmpty(boards == null || boards.isEmpty());
        });

        viewModel.getDiscussions().observe(getViewLifecycleOwner(), discussions -> {
            if (viewModel.getMode() != BoardsViewModel.MODE_TRENDING) return;
            discussionAdapter.submitList(discussions);
            updateEmpty(discussions == null || discussions.isEmpty());
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progress.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });

        applyMode();
    }

    private void applyMode() {
        boolean boardsMode = viewModel.getMode() == BoardsViewModel.MODE_BOARDS;
        binding.rvBoards.setAdapter(boardsMode ? boardAdapter : discussionAdapter);
        binding.fabCreateBoard.setVisibility(boardsMode ? View.VISIBLE : View.GONE);
        binding.tilSearch.setHint(boardsMode
                ? R.string.search_boards : R.string.search_discussions);
        binding.tvEmpty.setText(boardsMode ? R.string.empty_boards : R.string.empty_trending);
        updateEmpty(boardsMode
                ? boardAdapter.getItemCount() == 0
                : discussionAdapter.getItemCount() == 0);
    }

    private void updateEmpty(boolean empty) {
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvBoards.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openBoard(Board board) {
        if (board.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("boardId", board.getId());
        args.putString("boardName", board.getName());
        Navigation.findNavController(requireView()).navigate(R.id.boardDetailFragment, args);
    }

    private void openDiscussion(Discussion discussion) {
        if (discussion.getId() == null || discussion.getBoardId() == null) return;
        Bundle args = new Bundle();
        args.putString("boardId", discussion.getBoardId());
        args.putString("discussionId", discussion.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.discussionDetailFragment, args);
    }

    private void showCreateBoardDialog() {
        final DialogCreateBoardBinding dialogBinding =
                DialogCreateBoardBinding.inflate(getLayoutInflater());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_create_board)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_create_board, (dialog, which) -> {
                    String name = text(dialogBinding.etName);
                    if (name.isEmpty()) {
                        if (binding != null) {
                            Snackbar.make(binding.getRoot(), R.string.error_board_name_required,
                                    Snackbar.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    viewModel.createBoard(name, text(dialogBinding.etDescription), error -> {
                        if (binding == null) return;
                        Snackbar.make(binding.getRoot(),
                                error == null ? R.string.board_created : R.string.error_generic,
                                Snackbar.LENGTH_SHORT).show();
                    });
                })
                .show();
    }

    private String text(android.widget.EditText field) {
        return field != null && field.getText() != null
                ? field.getText().toString().trim() : "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        searchHandler.removeCallbacksAndMessages(null);
        binding = null;
    }
}
