package com.example.daily_learn.ui.progress;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.Progress;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.data.repository.SubjectRepository;
import com.example.daily_learn.databinding.FragmentProgressBinding;
import com.example.daily_learn.ui.adapters.SubjectProgressAdapter;
import com.example.daily_learn.viewmodel.ProgressViewModel;

import java.util.ArrayList;
import java.util.List;

public class ProgressFragment extends Fragment {

    private FragmentProgressBinding binding;
    private ProgressViewModel viewModel;
    private SubjectProgressAdapter adapter;
    private SubjectRepository subjectRepo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProgressBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProgressViewModel.class);
        subjectRepo = new SubjectRepository(requireActivity().getApplication());

        adapter = new SubjectProgressAdapter();
        binding.rvSubjectProgress.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSubjectProgress.setAdapter(adapter);

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.overallProgress.observe(getViewLifecycleOwner(), progress -> {
            if (progress != null) {
                int total = progress.totalTopics;
                int done = progress.completedTopics;
                int pct = total > 0 ? (int) ((done * 100f) / total) : 0;

                binding.tvOverallPercentLarge.setText(pct + "%");
                binding.tvTopicsDone.setText(done + " / " + total + " topics completed");
                binding.pbOverallProgress.setProgress(pct);

                binding.tvStreak.setText("🔥 " + progress.streakDays);
                binding.tvStreakBadge.setText(progress.streakDays + " Day Streak");
                binding.tvQuizAvg.setText((int) progress.quizAverage + "%");

                updateExamReadiness(done, total);
            }
        });

        viewModel.nextExam.observe(getViewLifecycleOwner(), exam -> {
            if (exam != null) {
                long now = System.currentTimeMillis();
                long diff = exam.date - now;
                int daysLeft = Math.max(0, (int) (diff / (1000 * 60 * 60 * 24)));
                binding.tvDaysUntilExam.setText(String.valueOf(daysLeft));

                Progress p = viewModel.overallProgress.getValue();
                if (p != null) {
                    updateExamReadiness(p.completedTopics, p.totalTopics);
                }
            }
        });

        SettingsStore settings = new SettingsStore(requireContext());
        subjectRepo.getAllSubjectsWithUnitsAndTopics(settings.getStudentId()).observe(getViewLifecycleOwner(), subjects -> {
            if (subjects != null) {
                List<SubjectProgressAdapter.ProgressItem> items = new ArrayList<>();
                for (var s : subjects) {
                    Progress p = new Progress(settings.getStudentId(), s.subject.id, 0);
                    int total = 0, done = 0;
                    if (s.units != null) {
                        for (var u : s.units) {
                            if (u.topics != null) {
                                for (var t : u.topics) {
                                    total++;
                                    if (t.isCompleted) done++;
                                }
                            }
                        }
                    }
                    p.totalTopics = total;
                    p.completedTopics = done;
                    items.add(new SubjectProgressAdapter.ProgressItem(p, s.subject.name));
                }
                adapter.setItems(items);
            }
        });
    }

    private void updateExamReadiness(int done, int total) {
        var exam = viewModel.nextExam.getValue();
        int daysLeft = 28;
        if (exam != null) {
            daysLeft = Math.max(0, (int) ((exam.date - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)));
        }

        String label = viewModel.getReadinessLabel(done, total, daysLeft);
        if ("EXCELLENT".equals(label)) {
            binding.tvExamReadinessLabel.setText(R.string.readiness_excellent);
        } else if ("GOOD".equals(label)) {
            binding.tvExamReadinessLabel.setText(R.string.readiness_good);
        } else if ("FAIR".equals(label)) {
            binding.tvExamReadinessLabel.setText(R.string.readiness_fair);
        } else {
            binding.tvExamReadinessLabel.setText(R.string.readiness_at_risk);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
