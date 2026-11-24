package com.campussync.app.ui.messages;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
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

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.FragmentChatBinding;
import com.campussync.app.firebase.MessagingRepository;
import com.campussync.app.firebase.PresenceRepository;
import com.campussync.app.model.ContentReport;
import com.campussync.app.model.Conversation;
import com.campussync.app.model.Message;
import com.campussync.app.ui.social.ReportDialogHelper;
import com.campussync.app.util.TimeAgo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * One chat thread: message list with history pagination, composer, attachments, typing indicator,
 * read receipts, presence, mute, block, report and leaving a group.
 */
public class ChatFragment extends Fragment {

    private static final int MENU_COPY = 1;
    private static final int MENU_REPORT = 2;
    private static final int MENU_DELETE = 3;

    private FragmentChatBinding binding;
    private ChatViewModel viewModel;
    private MessageAdapter adapter;
    private String conversationId;
    private String currentUserId;
    private boolean muted;
    private boolean group;
    private int lastMessageCount;
    private ListenerRegistration presenceRegistration;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) viewModel.sendAttachment(uri, fileName(uri));
            });

    private final ActivityResultLauncher<String> documentPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) viewModel.sendAttachment(uri, fileName(uri));
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        conversationId = getArguments() != null
                ? getArguments().getString("conversationId") : null;
        currentUserId = MessagingRepository.currentUidOrNull();
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.start(conversationId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new MessageAdapter(currentUserId, false, new MessageAdapter.Listener() {
            @Override
            public void onAttachment(Message message) {
                openAttachment(message);
            }

            @Override
            public void onLongPress(Message message, View anchor) {
                showMessageMenu(message, anchor);
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);
        binding.rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(-1)) {
                    viewModel.loadOlder();
                }
            }
        });

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

        binding.btnSend.setOnClickListener(v -> {
            Editable editable = binding.etMessage.getText();
            String text = editable != null ? editable.toString() : "";
            viewModel.sendText(text);
            binding.etMessage.setText("");
        });

        binding.btnAttach.setOnClickListener(v -> showAttachPicker());

        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) viewModel.onDraftChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        observe();
    }

    private void observe() {
        viewModel.getConversation().observe(getViewLifecycleOwner(), this::bindConversation);

        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            List<Message> list = messages != null ? messages : new ArrayList<>();
            adapter.submitList(new ArrayList<>(list));
            binding.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            if (list.size() > lastMessageCount) {
                binding.rvMessages.scrollToPosition(list.size() - 1);
            }
            lastMessageCount = list.size();
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getTypingName().observe(getViewLifecycleOwner(), name -> {
            boolean typing = name != null && !name.isEmpty();
            binding.tvTyping.setVisibility(typing ? View.VISIBLE : View.GONE);
            if (typing) {
                binding.tvTyping.setText(getString(R.string.typing_indicator, name));
            } else {
                bindSubtitle(subtitleForConversation());
            }
        });

        viewModel.getUploading().observe(getViewLifecycleOwner(), uploading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(uploading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getBlocked().observe(getViewLifecycleOwner(), blocked -> {
            boolean isBlocked = Boolean.TRUE.equals(blocked);
            binding.tvBlockedNotice.setVisibility(isBlocked && !group
                    ? View.VISIBLE : View.GONE);
            binding.layoutComposer.setVisibility(isBlocked && !group
                    ? View.GONE : View.VISIBLE);
            MenuItem blockItem = binding.toolbar.getMenu().findItem(R.id.action_block);
            if (blockItem != null) {
                blockItem.setTitle(isBlocked ? R.string.action_unblock : R.string.action_block);
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

        viewModel.getLeftGroup().observe(getViewLifecycleOwner(), left -> {
            if (Boolean.TRUE.equals(left) && isAdded()) {
                Snackbar.make(binding.getRoot(), R.string.group_left, Snackbar.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).navigateUp();
            }
        });
    }

    private void bindConversation(Conversation conversation) {
        if (conversation == null) return;
        boolean wasGroup = group;
        group = conversation.isGroup();
        binding.toolbar.setTitle(conversation.getDisplayTitle());
        bindSubtitle(subtitleForConversation());

        if (wasGroup != group) {
            adapter = new MessageAdapter(currentUserId, group, new MessageAdapter.Listener() {
                @Override
                public void onAttachment(Message message) {
                    openAttachment(message);
                }

                @Override
                public void onLongPress(Message message, View anchor) {
                    showMessageMenu(message, anchor);
                }
            });
            binding.rvMessages.setAdapter(adapter);
        }

        android.view.Menu menu = binding.toolbar.getMenu();
        MenuItem block = menu.findItem(R.id.action_block);
        if (block != null) block.setVisible(!group);
        MenuItem report = menu.findItem(R.id.action_report_user);
        if (report != null) report.setVisible(!group);
        MenuItem leave = menu.findItem(R.id.action_leave_group);
        if (leave != null) leave.setVisible(group);

        if (!group && conversation.getOtherUserId() != null) {
            attachPresence(conversation.getOtherUserId());
        }
    }

    private String subtitleForConversation() {
        Conversation conversation = viewModel.getConversation().getValue();
        if (conversation == null) return "";
        if (conversation.isGroup()) {
            return getString(R.string.group_members_count, conversation.getMemberCount());
        }
        return getString(R.string.offline);
    }

    private void bindSubtitle(String subtitle) {
        binding.toolbar.setSubtitle(subtitle);
    }

    private void attachPresence(String otherUserId) {
        if (presenceRegistration != null) return;
        presenceRegistration = PresenceRepository.getInstance()
                .listenToPresence(otherUserId, (state, lastSeen, error) -> {
                    if (binding == null) return;
                    if (PresenceRepository.isOnline(state, lastSeen)) {
                        bindSubtitle(getString(R.string.online));
                    } else if (lastSeen != null) {
                        bindSubtitle(getString(R.string.status_last_seen,
                                TimeAgo.format(lastSeen)));
                    } else {
                        bindSubtitle(getString(R.string.offline));
                    }
                });
    }

    private boolean onMenuItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_chat_info) {
            openInfo();
            return true;
        }
        if (id == R.id.action_mute) {
            muted = !muted;
            viewModel.setMuted(muted);
            item.setTitle(muted ? R.string.action_unmute : R.string.action_mute);
            return true;
        }
        if (id == R.id.action_block) {
            toggleBlock();
            return true;
        }
        if (id == R.id.action_report_user) {
            Conversation conversation = viewModel.getConversation().getValue();
            if (conversation != null && conversation.getOtherUserId() != null) {
                ReportDialogHelper.show(this, ContentReport.TYPE_USER,
                        conversation.getOtherUserId(),
                        conversation.getDisplayTitle());
            }
            return true;
        }
        if (id == R.id.action_leave_group) {
            confirmLeaveGroup();
            return true;
        }
        return false;
    }

    private void openInfo() {
        Conversation conversation = viewModel.getConversation().getValue();
        if (conversation == null) return;
        if (conversation.isGroup()) {
            Bundle args = new Bundle();
            args.putString("conversationId", conversation.getId());
            Navigation.findNavController(requireView())
                    .navigate(R.id.groupInfoFragment, args);
        } else if (conversation.getOtherUserId() != null) {
            Bundle args = new Bundle();
            args.putString("uid", conversation.getOtherUserId());
            Navigation.findNavController(requireView())
                    .navigate(R.id.userProfileFragment, args);
        }
    }

    private void toggleBlock() {
        Conversation conversation = viewModel.getConversation().getValue();
        if (conversation == null || conversation.isGroup()) return;
        boolean isBlocked = Boolean.TRUE.equals(viewModel.getBlocked().getValue());
        if (isBlocked) {
            viewModel.unblockPartner();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.confirm_block_title, conversation.getDisplayTitle()))
                .setMessage(R.string.confirm_block_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_block, (dialog, which) ->
                        viewModel.blockPartner())
                .show();
    }

    private void confirmLeaveGroup() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_leave_group_title)
                .setMessage(R.string.confirm_leave_group_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_leave_group, (dialog, which) ->
                        viewModel.leaveGroup())
                .show();
    }

    private void showAttachPicker() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.attach_title)
                .setItems(new CharSequence[]{
                        getString(R.string.attach_image),
                        getString(R.string.attach_document)
                }, (dialog, which) -> {
                    if (which == 0) {
                        imagePicker.launch("image/*");
                    } else {
                        documentPicker.launch("*/*");
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMessageMenu(final Message message, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        boolean own = currentUserId != null && currentUserId.equals(message.getSenderId());
        if (message.hasText()) {
            popup.getMenu().add(0, MENU_COPY, 0, R.string.action_copy);
        }
        if (!own) {
            popup.getMenu().add(0, MENU_REPORT, 1, R.string.action_report_message);
        } else {
            popup.getMenu().add(0, MENU_DELETE, 1, R.string.action_delete_message);
        }
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_COPY) {
                copyToClipboard(message.getText());
                return true;
            }
            if (item.getItemId() == MENU_REPORT) {
                ReportDialogHelper.show(this, ContentReport.TYPE_MESSAGE, message.getId(),
                        ReportDialogHelper.label(message.getText()));
                return true;
            }
            if (item.getItemId() == MENU_DELETE) {
                confirmDelete(message);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(final Message message) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete_message_title)
                .setMessage(R.string.confirm_delete_message_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        viewModel.deleteMessage(message))
                .show();
    }

    private void copyToClipboard(String text) {
        if (text == null) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.title_chat), text));
        }
    }

    private void openAttachment(Message message) {
        if (message == null || message.getAttachmentUrl() == null) return;
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getAttachmentUrl()));
            startActivity(view);
        } catch (Exception ignored) {
            Snackbar.make(binding.getRoot(), R.string.error_generic, Snackbar.LENGTH_SHORT).show();
        }
    }

    private String fileName(Uri uri) {
        String segment = uri != null ? uri.getLastPathSegment() : null;
        if (segment == null) return "attachment";
        int slash = segment.lastIndexOf('/');
        return slash >= 0 ? segment.substring(slash + 1) : segment;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (presenceRegistration != null) {
            presenceRegistration.remove();
            presenceRegistration = null;
        }
        binding = null;
    }
}
