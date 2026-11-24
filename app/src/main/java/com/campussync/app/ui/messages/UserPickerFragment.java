package com.campussync.app.ui.messages;

import android.os.Bundle;
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
import com.campussync.app.databinding.FragmentUserPickerBinding;
import com.campussync.app.model.Conversation;
import com.campussync.app.model.UserProfile;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * People picker. In direct mode a tap opens (or creates) the one-to-one chat; in add-members mode
 * the selection is added to an existing group.
 */
public class UserPickerFragment extends Fragment {

    private FragmentUserPickerBinding binding;
    private UserPickerViewModel viewModel;
    private UserPickerAdapter adapter;
    private boolean addMembersMode;
    private List<UserProfile> latestUsers = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String mode = getArguments() != null
                ? getArguments().getString("mode") : UserPickerViewModel.MODE_DIRECT;
        String conversationId = getArguments() != null
                ? getArguments().getString("conversationId") : null;
        addMembersMode = UserPickerViewModel.MODE_ADD_MEMBERS.equals(mode);
        viewModel = new ViewModelProvider(this).get(UserPickerViewModel.class);
        viewModel.configure(mode, conversationId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUserPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setTitle(addMembersMode
                ? R.string.btn_add_members : R.string.title_new_chat);
        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        adapter = new UserPickerAdapter(new UserPickerAdapter.Listener() {
            @Override
            public void onPick(UserProfile user) {
                viewModel.openChatWith(user);
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                binding.btnConfirm.setVisibility(addMembersMode ? View.VISIBLE : View.GONE);
                binding.btnConfirm.setText(selectedCount > 0
                        ? getString(R.string.selected_count, selectedCount)
                        : getString(R.string.btn_add_selected));
            }
        }, addMembersMode);
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUsers.setAdapter(adapter);

        binding.btnConfirm.setVisibility(View.GONE);
        binding.btnConfirm.setOnClickListener(v -> viewModel.addSelected(
                adapter.getSelectedIds(), latestUsers));

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.search(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        observe();
        viewModel.search("");
    }

    private void observe() {
        viewModel.getUsers().observe(getViewLifecycleOwner(), users -> {
            latestUsers = users != null ? users : new ArrayList<>();
            adapter.submitList(new ArrayList<>(latestUsers));
            binding.tvEmpty.setVisibility(latestUsers.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getOpenedConversation().observe(getViewLifecycleOwner(), conversation -> {
            if (conversation != null) openChat(conversation);
        });

        viewModel.getMembersAdded().observe(getViewLifecycleOwner(), added -> {
            if (Boolean.TRUE.equals(added) && isAdded()) {
                Navigation.findNavController(requireView()).navigateUp();
            }
        });

        viewModel.getMessageRes().observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            Snackbar.make(binding.getRoot(), res, Snackbar.LENGTH_SHORT).show();
            viewModel.clearMessage();
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });
    }

    private void openChat(Conversation conversation) {
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
