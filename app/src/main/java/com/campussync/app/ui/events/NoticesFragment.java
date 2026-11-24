package com.campussync.app.ui.events;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campussync.app.R;
import com.campussync.app.databinding.DialogTextFormBinding;
import com.campussync.app.databinding.FragmentNoticesBinding;
import com.campussync.app.firebase.AuthManager;
import com.campussync.app.firebase.UserRepository;
import com.campussync.app.model.CollegeNotice;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * College notices shown to every signed-in user. Only administrators can post; the toolbar
 * action is hidden for everyone else and the security rules reject the write regardless.
 */
public class NoticesFragment extends Fragment {

    private FragmentNoticesBinding binding;
    private NoticesViewModel viewModel;
    private NoticeAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(NoticesViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNoticesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        adapter = new NoticeAdapter();
        binding.rvNotices.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvNotices.setAdapter(adapter);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_post_notice) {
                showPostNoticeDialog();
                return true;
            }
            return false;
        });
        if (binding.toolbar.getMenu().findItem(R.id.action_post_notice) != null) {
            binding.toolbar.getMenu().findItem(R.id.action_post_notice).setVisible(false);
        }
        UserRepository.getInstance().checkIsAdmin(new AuthManager.ResultListener<Boolean>() {
            @Override
            public void onComplete(Boolean isAdmin, Exception exception) {
                if (binding == null || exception != null) return;
                boolean admin = Boolean.TRUE.equals(isAdmin);
                binding.toolbar.getMenu().findItem(R.id.action_post_notice)
                        .setVisible(admin);
            }
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getNotices().observe(getViewLifecycleOwner(), this::render);

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearError();
        });

        viewModel.start();
    }

    private void render(List<CollegeNotice> notices) {
        adapter.submitList(notices);
        boolean empty = notices == null || notices.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvNotices.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showPostNoticeDialog() {
        DialogTextFormBinding form = DialogTextFormBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext())
                .setTitle(R.string.action_post_notice)
                .setView(form.getRoot())
                .setPositiveButton(R.string.btn_publish, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String title = form.etTitle.getText() != null
                            ? form.etTitle.getText().toString().trim() : "";
                    String body = form.etBody.getText() != null
                            ? form.etBody.getText().toString().trim() : "";
                    if (title.isEmpty()) {
                        form.tilTitle.setError(getString(R.string.error_title_required));
                        return;
                    }
                    if (body.isEmpty()) {
                        form.tilBody.setError(getString(R.string.error_description_required));
                        return;
                    }
                    viewModel.postNotice(title, body, viewModel.defaultCollege(), error -> {
                        if (error != null) {
                            Toast.makeText(requireContext(), error.getMessage() != null
                                    ? error.getMessage() : "Could not publish the notice",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(requireContext(), R.string.msg_notice_posted,
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
                }));
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
