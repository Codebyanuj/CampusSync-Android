package com.campussync.app.ui.academics;

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
import com.campussync.app.databinding.FragmentDoubtDetailBinding;
import com.campussync.app.model.Doubt;
import com.campussync.app.model.DoubtAnswer;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** One doubt and its answers, with accept / mark-solved actions for the student who asked. */
public class DoubtDetailFragment extends Fragment {

    private FragmentDoubtDetailBinding binding;
    private DoubtDetailViewModel viewModel;
    private AnswerAdapter answerAdapter;
    private String doubtId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DoubtDetailViewModel.class);
        doubtId = getArguments() != null ? getArguments().getString("doubtId") : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDoubtDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        answerAdapter = new AnswerAdapter(viewModel::accept);
        binding.rvAnswers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAnswers.setAdapter(answerAdapter);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        binding.btnPostAnswer.setOnClickListener(v -> {
            viewModel.answer(binding.etAnswer.getText() != null
                    ? binding.etAnswer.getText().toString() : "");
            binding.etAnswer.setText("");
        });
        binding.btnMarkSolved.setOnClickListener(v -> viewModel.markSolved());

        viewModel.getDoubt().observe(getViewLifecycleOwner(), this::bindDoubt);
        viewModel.getAnswers().observe(getViewLifecycleOwner(), this::bindAnswers);
        viewModel.getBusy().observe(getViewLifecycleOwner(), busy -> {
            boolean working = Boolean.TRUE.equals(busy);
            binding.progressBar.setVisibility(working ? View.VISIBLE : View.GONE);
            binding.btnPostAnswer.setEnabled(!working);
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearMessage();
        });

        viewModel.start(doubtId);
    }

    private void bindDoubt(Doubt doubt) {
        if (doubt == null) return;
        binding.tvDoubtTitle.setText(doubt.getTitle() != null ? doubt.getTitle() : "");
        binding.tvDoubtBody.setText(doubt.getBody() != null ? doubt.getBody() : "");

        String subject = doubt.getSubject();
        boolean hasSubject = subject != null && !subject.isEmpty();
        binding.chipSubject.setVisibility(hasSubject ? View.VISIBLE : View.GONE);
        if (hasSubject) binding.chipSubject.setText(subject);

        binding.chipSolved.setVisibility(doubt.isSolved() ? View.VISIBLE : View.GONE);

        String meta = getString(R.string.doubt_answers, doubt.getAnswerCount());
        if (doubt.getAuthorName() != null && !doubt.getAuthorName().isEmpty()) {
            meta = meta + " · " + doubt.getAuthorName();
        }
        binding.tvDoubtMeta.setText(meta);

        // Only the asker can accept an answer or close the doubt.
        boolean asker = viewModel.isAsker();
        answerAdapter.setCanAccept(asker && !doubt.isSolved());
        binding.btnMarkSolved.setVisibility(asker && !doubt.isSolved() ? View.VISIBLE : View.GONE);
    }

    private void bindAnswers(List<DoubtAnswer> answers) {
        answerAdapter.submitList(answers);
        boolean empty = answers == null || answers.isEmpty();
        binding.tvEmptyAnswers.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvAnswers.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
