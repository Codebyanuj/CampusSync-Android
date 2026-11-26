package com.campussync.app.ui.academics;

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
import com.campussync.app.databinding.DialogTutorOfferBinding;
import com.campussync.app.databinding.FragmentAcademicHubBinding;
import com.campussync.app.model.AcademicResource;
import com.campussync.app.model.Doubt;
import com.campussync.app.model.TutorProfile;
import com.campussync.app.model.UserProfile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

/**
 * Academic hub: one screen, three tabs.
 *
 * <ul>
 *   <li><b>Resources</b> — the shared library with type chips, "my department", "my semester" and
 *       a saved-only filter plus free text search.</li>
 *   <li><b>Doubts</b> — the peer doubt board.</li>
 *   <li><b>Tutors</b> — the tutor directory, searchable by subject, name or department.</li>
 * </ul>
 */
public class AcademicHubFragment extends Fragment {

    private static final int TAB_RESOURCES = 0;
    private static final int TAB_DOUBTS = 1;
    private static final int TAB_TUTORS = 2;

    private FragmentAcademicHubBinding binding;
    private AcademicHubViewModel viewModel;
    private ResourceAdapter resourceAdapter;
    private DoubtAdapter doubtAdapter;
    private TutorAdapter tutorAdapter;
    private int currentTab = TAB_RESOURCES;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AcademicHubViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAcademicHubBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        resourceAdapter = new ResourceAdapter(this::openResource);
        doubtAdapter = new DoubtAdapter(this::openDoubt);
        tutorAdapter = new TutorAdapter(this::openTutor);

        binding.rvResources.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvResources.setAdapter(resourceAdapter);
        binding.rvDoubts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDoubts.setAdapter(doubtAdapter);
        binding.rvTutors.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTutors.setAdapter(tutorAdapter);

        binding.tabLayout.addTab(binding.tabLayout.newTab()
                .setText(R.string.academic_tab_resources));
        binding.tabLayout.addTab(binding.tabLayout.newTab()
                .setText(R.string.academic_tab_doubts));
        binding.tabLayout.addTab(binding.tabLayout.newTab()
                .setText(R.string.academic_tab_tutors));
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateVisibility();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setQuery(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.chipTypes.setOnCheckedStateChangeListener((group, checkedIds) ->
                viewModel.setType(typeForChip(checkedIds.isEmpty()
                        ? R.id.chipAllTypes : checkedIds.get(0))));
        binding.chipQuickGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            viewModel.setMyDepartmentOnly(checkedIds.contains(R.id.chipMyDepartment));
            viewModel.setMySemesterOnly(checkedIds.contains(R.id.chipMySemester));
            viewModel.setSavedOnly(checkedIds.contains(R.id.chipSaved));
        });

        binding.btnRetry.setOnClickListener(v -> viewModel.retry());

        // The hub hides the tab bar, so the toolbar carries the only way back besides a system back.
        binding.toolbar.setNavigationOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_upload_resource) {
                Navigation.findNavController(view).navigate(R.id.uploadResourceFragment);
                return true;
            }
            if (id == R.id.action_ask_doubt) {
                Navigation.findNavController(view).navigate(R.id.createDoubtFragment);
                return true;
            }
            if (id == R.id.action_tutoring_requests) {
                Navigation.findNavController(view).navigate(R.id.tutoringRequestsFragment);
                return true;
            }
            if (id == R.id.action_karma) {
                Navigation.findNavController(view).navigate(R.id.karmaFragment);
                return true;
            }
            if (id == R.id.action_offer_tutoring) {
                showTutorDialog();
                return true;
            }
            return false;
        });

        observe();
        viewModel.startListening();
    }

    private static String typeForChip(int id) {
        if (id == R.id.chipNotes) return AcademicResource.TYPE_NOTES;
        if (id == R.id.chipPdf) return AcademicResource.TYPE_PDF;
        if (id == R.id.chipSlides) return AcademicResource.TYPE_SLIDES;
        if (id == R.id.chipPapers) return AcademicResource.TYPE_QUESTION_PAPER;
        return null;
    }

    private void observe() {
        viewModel.getLibrary().observe(getViewLifecycleOwner(), list -> {
            resourceAdapter.submitList(list);
            updateVisibility();
        });
        viewModel.getDoubts().observe(getViewLifecycleOwner(), list -> {
            doubtAdapter.submitList(list);
            updateVisibility();
        });
        viewModel.getTutors().observe(getViewLifecycleOwner(), list -> {
            tutorAdapter.submitList(list);
            updateVisibility();
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });

        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            viewModel.clearMessage();
        });

        viewModel.getMyProfile().observe(getViewLifecycleOwner(), this::bindProfile);
    }

    private void bindProfile(UserProfile profile) {
        if (binding == null || profile == null) return;
        boolean hasDepartment = profile.getDepartment() != null
                && !profile.getDepartment().trim().isEmpty();
        binding.chipMyDepartment.setEnabled(hasDepartment);
        binding.chipMySemester.setEnabled(profile.getSemester() > 0);
    }

    private void updateVisibility() {
        if (binding == null) return;
        boolean resources = currentTab == TAB_RESOURCES;
        boolean doubts = currentTab == TAB_DOUBTS;
        boolean tutors = currentTab == TAB_TUTORS;

        binding.chipTypes.setVisibility(resources ? View.VISIBLE : View.GONE);
        binding.chipQuickFilters.setVisibility(resources ? View.VISIBLE : View.GONE);
        binding.rvResources.setVisibility(resources ? View.VISIBLE : View.GONE);
        binding.rvDoubts.setVisibility(doubts ? View.VISIBLE : View.GONE);
        binding.rvTutors.setVisibility(tutors ? View.VISIBLE : View.GONE);

        int count = resources ? resourceAdapter.getItemCount()
                : doubts ? doubtAdapter.getItemCount()
                : tutorAdapter.getItemCount();
        boolean empty = count == 0 && !Boolean.TRUE.equals(viewModel.getLoading().getValue());
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            binding.tvEmpty.setText(resources ? R.string.empty_resources
                    : doubts ? R.string.empty_doubts : R.string.empty_tutors);
        }
    }

    private void showTutorDialog() {
        if (!isAdded()) return;
        DialogTutorOfferBinding dialog = DialogTutorOfferBinding.inflate(getLayoutInflater());
        TutorProfile existing = viewModel.getMyTutor().getValue();
        if (existing != null) {
            dialog.etSubjects.setText(AcademicUi.joinSubjects(existing.getSubjects()));
            dialog.etBio.setText(existing.getBio());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tutor_offer_title)
                .setView(dialog.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.tutor_offer_save, (d, which) -> {
                    List<String> subjects = AcademicUi.parseSubjects(
                            dialog.etSubjects.getText() != null
                                    ? dialog.etSubjects.getText().toString() : "");
                    if (subjects.isEmpty()) {
                        Snackbar.make(binding.getRoot(), R.string.tutor_no_subjects,
                                Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    viewModel.saveTutorListing(subjects,
                            dialog.etBio.getText() != null
                                    ? dialog.etBio.getText().toString().trim() : "");
                })
                .show();
    }

    private void openResource(AcademicResource resource) {
        if (resource.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("resourceId", resource.getId());
        Navigation.findNavController(requireView()).navigate(R.id.resourceDetailFragment, args);
    }

    private void openDoubt(Doubt doubt) {
        if (doubt.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("doubtId", doubt.getId());
        Navigation.findNavController(requireView()).navigate(R.id.doubtDetailFragment, args);
    }

    private void openTutor(TutorProfile tutor) {
        if (tutor.getId() == null) return;
        Bundle args = new Bundle();
        args.putString("tutorId", tutor.getId());
        Navigation.findNavController(requireView()).navigate(R.id.tutorProfileFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
