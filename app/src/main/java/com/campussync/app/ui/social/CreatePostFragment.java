package com.campussync.app.ui.social;

import android.net.Uri;
import android.os.Bundle;
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

import com.campussync.app.R;
import com.campussync.app.databinding.FragmentCreatePostBinding;
import com.google.android.material.snackbar.Snackbar;

/** Compose screen. Ghost mode publishes with no author anywhere in the public document. */
public class CreatePostFragment extends Fragment {

    private FragmentCreatePostBinding binding;
    private CreatePostViewModel viewModel;
    private String editingPostId;
    private Uri pickedImage;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || binding == null) return;
                pickedImage = uri;
                binding.cardPreview.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(this)
                        .load(uri)
                        .centerCrop()
                        .into(binding.ivPreview);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CreatePostViewModel.class);
        Bundle args = getArguments();
        if (args != null) editingPostId = args.getString("postId");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCreatePostBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean editing = editingPostId != null;
        binding.toolbar.setTitle(editing ? R.string.action_edit_post : R.string.title_create_post);
        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        if (editing) {
            Bundle args = getArguments();
            binding.etContent.setText(args != null ? args.getString("existingContent") : "");
            binding.btnPickImage.setVisibility(View.GONE);
            binding.cardGhost.setVisibility(View.GONE);
            binding.switchAnonymous.setEnabled(false);
        }

        binding.btnPickImage.setOnClickListener(v -> imagePicker.launch("image/*"));
        binding.btnRemoveImage.setOnClickListener(v -> {
            pickedImage = null;
            binding.cardPreview.setVisibility(View.GONE);
        });
        binding.btnPost.setText(editing ? R.string.btn_save : R.string.btn_post);
        binding.btnPost.setOnClickListener(v -> submit(editing));

        viewModel.getBusy().observe(getViewLifecycleOwner(), busy -> {
            boolean working = Boolean.TRUE.equals(busy);
            binding.progressPost.setVisibility(working ? View.VISIBLE : View.GONE);
            binding.btnPost.setEnabled(!working);
            binding.btnPost.setText(getString(working
                    ? R.string.please_wait
                    : (editing ? R.string.btn_save : R.string.btn_post)));
        });

        viewModel.getDone().observe(getViewLifecycleOwner(), done -> {
            if (!Boolean.TRUE.equals(done)) return;
            Snackbar.make(requireView(),
                    editing ? R.string.post_updated : R.string.post_published,
                    Snackbar.LENGTH_SHORT).show();
            Navigation.findNavController(requireView()).navigateUp();
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });
    }

    private void submit(boolean editing) {
        String content = binding.etContent.getText() != null
                ? binding.etContent.getText().toString().trim() : "";
        if (content.isEmpty() && pickedImage == null && !editing) {
            Snackbar.make(requireView(), R.string.error_empty_post, Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (editing && content.isEmpty()) {
            Snackbar.make(requireView(), R.string.error_empty_post, Snackbar.LENGTH_SHORT).show();
            return;
        }
        boolean anonymous = binding.switchAnonymous.isChecked() && !editing;
        viewModel.save(editing ? editingPostId : null, content, anonymous, pickedImage);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
