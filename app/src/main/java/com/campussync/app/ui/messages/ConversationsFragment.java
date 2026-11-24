package com.campussync.app.ui.messages;

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
import com.campussync.app.databinding.FragmentConversationsBinding;
import com.campussync.app.model.Conversation;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

/**
 * Messages tab: every thread the signed-in user belongs to, with last message, time and unread
 * badge. Blocked partners' direct threads are hidden.
 */
public class ConversationsFragment extends Fragment {

    private FragmentConversationsBinding binding;
    private ConversationsViewModel viewModel;
    private ConversationAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ConversationsViewModel.class);
        viewModel.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentConversationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ConversationAdapter(this::openConversation);
        binding.rvConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvConversations.setAdapter(adapter);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_new_chat) {
                Bundle args = new Bundle();
                args.putString("mode", UserPickerViewModel.MODE_DIRECT);
                Navigation.findNavController(view)
                        .navigate(R.id.userPickerFragment, args);
                return true;
            }
            if (id == R.id.action_new_group) {
                Navigation.findNavController(view).navigate(R.id.createGroupFragment);
                return true;
            }
            if (id == R.id.action_blocked_users) {
                Navigation.findNavController(view).navigate(R.id.blockedUsersFragment);
                return true;
            }
            return false;
        });

        binding.swipeRefresh.setColorSchemeResources(R.color.primary);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
            binding.swipeRefresh.setRefreshing(false);
        });

        viewModel.getConversations().observe(getViewLifecycleOwner(), conversations -> {
            adapter.submitList(conversations != null ? conversations : new ArrayList<>());
            boolean empty = conversations == null || conversations.isEmpty();
            binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });
    }

    private void openConversation(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("conversationId", conversation.getId());
        Navigation.findNavController(requireView()).navigate(R.id.chatFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
