package com.campussync.app.ui.boards;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.campussync.app.R;
import com.campussync.app.databinding.ItemBoardBinding;
import com.campussync.app.model.Board;

import java.util.Objects;

/** Board cards with the discussion count. */
public class BoardAdapter extends ListAdapter<Board, BoardAdapter.BoardViewHolder> {

    public interface Listener {
        void onBoard(Board board);
    }

    private static final DiffUtil.ItemCallback<Board> DIFF = new DiffUtil.ItemCallback<Board>() {
        @Override
        public boolean areItemsTheSame(@NonNull Board oldItem, @NonNull Board newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Board oldItem, @NonNull Board newItem) {
            return Objects.equals(oldItem.getName(), newItem.getName())
                    && Objects.equals(oldItem.getDescription(), newItem.getDescription())
                    && oldItem.getDiscussionCount() == newItem.getDiscussionCount();
        }
    };

    private final Listener listener;

    public BoardAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public BoardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBoardBinding binding = ItemBoardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new BoardViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BoardViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class BoardViewHolder extends RecyclerView.ViewHolder {
        private final ItemBoardBinding binding;

        BoardViewHolder(ItemBoardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Board board, final Listener listener) {
            binding.tvInitial.setText(board.getInitial());
            binding.tvBoardName.setText(board.getName());
            String description = board.getDescription();
            binding.tvBoardDescription.setText(description != null ? description : "");
            binding.tvBoardDescription.setVisibility(description != null
                    && !description.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.tvDiscussionCount.setText(binding.getRoot().getContext()
                    .getString(R.string.board_discussions, board.getDiscussionCount()));
            binding.getRoot().setOnClickListener(v -> listener.onBoard(board));
        }
    }
}
