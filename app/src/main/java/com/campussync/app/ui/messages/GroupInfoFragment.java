package com.campussync.app.ui.messages;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.DialogGroupNameBinding;
import com.campussync.app.databinding.FragmentGroupInfoBinding;
import com.campussync.app.firebase.MessagingRepository;
import com.campussync.app.model.Conversation;
import com.campussync.app.model.ConversationMember;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

/** Group details: photo, name, member list with admin controls, mute and leaving. */
public class GroupInfoFragment extends Fragment {

    private static final int MENU_PROMOTE = 1;
    private static final int MENU_DEMOTE = 2;
    private static final int MENU_REMOVE = 3;

    private FragmentGroupInfoBinding binding;
    private GroupInfoViewModel viewModel;
    private MemberAdapter adapter;
    private String conversationId;

    private final ActivityResultLauncher<String> photoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri ->
                    viewModel.changePhoto(uri));

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        conversationId = getArguments() != null
                ? getArguments().getString("conversationId") : null;
        viewModel = new ViewModelProvider(this).get(GroupInfoViewModel.class);
        viewModel.start(conversationId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGroupInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

        adapter = new MemberAdapter(MessagingRepository.currentUidOrNull(),
                (ConversationMember member, View anchor) -> showMemberMenu(member, anchor));
        binding.rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMembers.setAdapter(adapter);

        binding.btnChangePhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        binding.btnAddMembers.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("mode", UserPickerViewModel.MODE_ADD_MEMBERS);
            args.putString("conversationId", conversationId);
            Navigation.findNavController(view).navigate(R.id.userPickerFragment, args);
        });
        binding.btnLeaveGroup.setOnClickListener(v -> confirmLeave());

        observe();
    }

    private void observe() {
        viewModel.getConversation().observe(getViewLifecycleOwner(), conversation -> {
            if (conversation == null) return;
            binding.tvGroupName.setText(conversation.getDisplayTitle());
            binding.tvMemberCount.setText(getString(R.string.group_members_count,
                    conversation.getMemberCount()));
            Glide.with(this)
                    .load(conversation.getPhotoUrl())
                    .placeholder(R.drawable.ic_group)
                    .error(R.drawable.ic_group)
                    .circleCrop()
                    .into(binding.ivGroupPhoto);
        });

        viewModel.getMembers().observe(getViewLifecycleOwner(), members ->
                adapter.submitList(members != null ? members : new ArrayList<>()));

        viewModel.getAdmin().observe(getViewLifecycleOwner(), isAdmin -> {
            boolean admin = Boolean.TRUE.equals(isAdmin);
            adapter.setCanManage(admin);
            adapter.notifyDataSetChanged();
            binding.btnAddMembers.setVisibility(admin ? View.VISIBLE : View.GONE);
            binding.btnChangePhoto.setVisibility(admin ? View.VISIBLE : View.GONE);
            MenuItem rename = binding.toolbar.getMenu().findItem(R.id.action_rename_group);
            if (rename != null) rename.setVisible(admin);
        });

        viewModel.getMuted().observe(getViewLifecycleOwner(), muted -> {
            MenuItem item = binding.toolbar.getMenu().findItem(R.id.action_group_mute);
            if (item != null) {
                item.setTitle(Boolean.TRUE.equals(muted)
                        ? R.string.action_unmute : R.string.action_mute);
            }
        });

        viewModel.getBusy().observe(getViewLifecycleOwner(), busy ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(busy)
                        ? View.VISIBLE : View.GONE));

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

        viewModel.getLeftGroup().observe(getViewLifecycleOwner(), left -> {
            if (Boolean.TRUE.equals(left) && isAdded()) {
                Snackbar.make(binding.getRoot(), R.string.group_left, Snackbar.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).navigateUp();
            }
        });
    }

    private boolean onMenuItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_rename_group) {
            showRenameDialog();
            return true;
        }
        if (id == R.id.action_group_mute) {
            boolean muted = Boolean.TRUE.equals(viewModel.getMuted().getValue());
            viewModel.setMuted(!muted);
            return true;
        }
        if (id == R.id.action_group_leave) {
            confirmLeave();
            return true;
        }
        return false;
    }

    private void showRenameDialog() {
        final DialogGroupNameBinding dialogBinding = DialogGroupNameBinding.inflate(
                getLayoutInflater());
        Conversation conversation = viewModel.getConversation().getValue();
        if (conversation != null && conversation.getTitle() != null) {
            dialogBinding.etGroupName.setText(conversation.getTitle());
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_rename_group)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = dialogBinding.etGroupName.getText() != null
                            ? dialogBinding.etGroupName.getText().toString() : "";
                    if (name.trim().isEmpty()) {
                        dialogBinding.tilGroupName.setError(
                                getString(R.string.error_group_name_required));
                        return;
                    }
                    dialog.dismiss();
                    viewModel.rename(name);
                }));
        dialog.show();
    }

    private void showMemberMenu(final ConversationMember member, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        if (member.isAdmin()) {
            popup.getMenu().add(0, MENU_DEMOTE, 0, R.string.action_demote);
        } else {
            popup.getMenu().add(0, MENU_PROMOTE, 0, R.string.action_promote);
        }
        popup.getMenu().add(0, MENU_REMOVE, 1, R.string.action_remove_member);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_PROMOTE) {
                viewModel.setAdmin(member, true);
                return true;
            }
            if (item.getItemId() == MENU_DEMOTE) {
                viewModel.setAdmin(member, false);
                return true;
            }
            if (item.getItemId() == MENU_REMOVE) {
                confirmRemove(member);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmRemove(final ConversationMember member) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_remove_member_title)
                .setMessage(getString(R.string.confirm_remove_member_message,
                        member.getName() != null ? member.getName() : ""))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_remove_member, (dialog, which) ->
                        viewModel.removeMember(member))
                .show();
    }

    private void confirmLeave() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_leave_group_title)
                .setMessage(R.string.confirm_leave_group_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_leave_group, (dialog, which) ->
                        viewModel.leaveGroup())
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
