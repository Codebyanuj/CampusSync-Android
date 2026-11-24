package com.campussync.app.ui.messages;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.campussync.app.R;
import com.campussync.app.databinding.FragmentCreateGroupBinding;
import com.campussync.app.model.Conversation;
import com.campussync.app.model.UserProfile;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/** Creates a group chat with a name, an optional photo and a selected member list. */
public class CreateGroupFragment extends Fragment {

    private FragmentCreateGroupBinding binding;
    private CreateGroupViewModel viewModel;
    private UserPickerAdapter adapter;
    private List<UserProfile> latestUsers = new ArrayList<>();

    private final ActivityResultLauncher<String> photoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                viewModel.setPhotoUri(uri);
                Glide.with(this).load(uri).circleCrop().into(binding.ivGroupPhoto);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CreateGroupViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCreateGroupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        adapter = new UserPickerAdapter(new UserPickerAdapter.Listener() {
            @Override
            public void onPick(UserProfile user) {
                // Multi-select only, handled by onSelectionChanged.
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                binding.tvSelectedCount.setText(getString(R.string.selected_count, selectedCount));
            }
        }, true);
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUsers.setAdapter(adapter);
        binding.tvSelectedCount.setText(getString(R.string.selected_count, 0));

        binding.btnPickPhoto.setOnClickListener(v -> photoPicker.launch("image/*"));

        binding.btnCreateGroup.setOnClickListener(v -> viewModel.create(
                binding.etGroupName.getText() != null ? binding.etGroupName.getText().toString() : "",
                adapter.getSelectedIds()));

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
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getCreating().observe(getViewLifecycleOwner(), creating ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(creating)
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

        viewModel.getCreated().observe(getViewLifecycleOwner(), (Conversation conversation) -> {
            if (conversation == null || conversation.getId() == null || !isAdded()) return;
            Snackbar.make(binding.getRoot(), R.string.group_created,
                    Snackbar.LENGTH_SHORT).show();
            Bundle args = new Bundle();
            args.putString("conversationId", conversation.getId());
            Navigation.findNavController(requireView()).navigate(R.id.chatFragment, args);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
