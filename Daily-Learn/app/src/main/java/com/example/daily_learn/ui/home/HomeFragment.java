package com.example.daily_learn.ui.home;

import android.graphics.Color;
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

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.StudySessionWithTopic;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.databinding.FragmentHomeBinding;
import com.example.daily_learn.ui.adapters.UpcomingTopicAdapter;
import com.example.daily_learn.ui.adapters.WeakTopicAdapter;
import com.example.daily_learn.viewmodel.HomeViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private WeakTopicAdapter weakTopicAdapter;
    private UpcomingTopicAdapter upcomingTopicAdapter;

    private Long currentTopicId = null;
    private Long currentSessionId = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupRecyclerViews();
        observeViewModel();

        binding.btnStartLearning.setOnClickListener(v -> {
            if (currentTopicId != null) {
                Bundle args = new Bundle();
                args.putLong("topicId", currentTopicId);
                args.putLong("sessionId", currentSessionId != null ? currentSessionId : -1L);
                Navigation.findNavController(view).navigate(R.id.action_home_to_learning, args);
            }
        });

        binding.cardAiSyllabusAnalyzer.setOnClickListener(v -> {
            Navigation.findNavController(view).navigate(R.id.action_home_to_analyzer);
        });
    }

    private void setupRecyclerViews() {
        weakTopicAdapter = new WeakTopicAdapter();
        binding.rvWeakTopics.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWeakTopics.setAdapter(weakTopicAdapter);

        upcomingTopicAdapter = new UpcomingTopicAdapter();
        binding.rvUpcomingTopics.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUpcomingTopics.setAdapter(upcomingTopicAdapter);
    }

    private void observeViewModel() {
        // Student name greeting
        viewModel.student.observe(getViewLifecycleOwner(), student -> {
            if (student != null) {
                Calendar c = Calendar.getInstance();
                int timeOfDay = c.get(Calendar.HOUR_OF_DAY);
                String greeting = "Hello";
                if (timeOfDay >= 0 && timeOfDay < 12) {
                    greeting = getString(R.string.good_morning);
                } else if (timeOfDay >= 12 && timeOfDay < 16) {
                    greeting = getString(R.string.good_afternoon);
                } else {
                    greeting = getString(R.string.good_evening);
                }
                binding.tvGreeting.setText(greeting + ", " + student.name + " 👋");
            }
        });

        // Today's session and topic
        viewModel.todaysSessions.observe(getViewLifecycleOwner(), sessions -> {
            if (sessions != null && !sessions.isEmpty()) {
                StudySessionWithTopic first = sessions.get(0);
                currentSessionId = first.session.id;
                if (first.topic != null) {
                    currentTopicId = first.topic.id;
                    binding.tvTopicName.setText(first.topic.name);
                    binding.tvEstimatedTime.setText("⏱ Estimated time: " + first.topic.estimatedMinutes + " min");
                    binding.tvTodayLabel.setText(first.session.isRevision ? "TODAY'S REVISION" : "TODAY'S TOPIC");
                    binding.btnStartLearning.setEnabled(true);
                } else {
                    binding.tvTopicName.setText(R.string.no_topic_today);
                    binding.tvEstimatedTime.setText("No topic assigned to this session");
                    binding.btnStartLearning.setEnabled(false);
                }

                // Collect remaining upcoming sessions for today & beyond
                if (sessions.size() > 1) {
                    List<StudySessionWithTopic> upcoming = new ArrayList<>(sessions.subList(1, sessions.size()));
                    upcomingTopicAdapter.setSessions(upcoming);
                    binding.cardUpcoming.setVisibility(View.VISIBLE);
                } else {
                    binding.cardUpcoming.setVisibility(View.GONE);
                }
            } else {
                binding.tvTopicName.setText(R.string.no_topic_today);
                binding.tvEstimatedTime.setText("Relax or review past topics");
                binding.btnStartLearning.setEnabled(false);
                binding.cardUpcoming.setVisibility(View.GONE);
            }
        });

        // Overall progress
        viewModel.overallProgress.observe(getViewLifecycleOwner(), progress -> {
            if (progress != null) {
                int total = progress.totalTopics;
                int done = progress.completedTopics;
                int pct = total > 0 ? (int) ((done * 100f) / total) : 0;

                binding.tvSyllabusPercent.setText(pct + "%");
                binding.tvProgressPercent.setText(pct + "% completed (" + done + "/" + total + ")");
                binding.pbTopicProgress.setProgress(pct);

                viewModel.computeScheduleStatus();
            }
        });

        // Next exam & countdown
        viewModel.nextExam.observe(getViewLifecycleOwner(), exam -> {
            if (exam != null) {
                long now = System.currentTimeMillis();
                long diff = exam.date - now;
                int daysLeft = Math.max(0, (int) (diff / (1000 * 60 * 60 * 24)));
                binding.tvDaysLeft.setText(String.valueOf(daysLeft));
                viewModel.computeScheduleStatus();
            }
        });

        // Schedule status (On Track / Behind / Ahead)
        viewModel.scheduleStatus.observe(getViewLifecycleOwner(), status -> {
            if ("AHEAD".equals(status)) {
                binding.tvStatusIcon.setText("🚀");
                binding.tvStatusLabel.setText(R.string.ahead_of_schedule);
                binding.tvStatusLabel.setTextColor(Color.parseColor("#43A047"));
            } else if ("BEHIND".equals(status)) {
                binding.tvStatusIcon.setText("⚠");
                binding.tvStatusLabel.setText(R.string.behind_schedule);
                binding.tvStatusLabel.setTextColor(Color.parseColor("#EF5350"));
            } else {
                binding.tvStatusIcon.setText("✓");
                binding.tvStatusLabel.setText(R.string.on_track);
                binding.tvStatusLabel.setTextColor(Color.parseColor("#43A047"));
            }
        });

        // Weak topics
        viewModel.weakTopics.observe(getViewLifecycleOwner(), topics -> {
            if (topics != null && !topics.isEmpty()) {
                weakTopicAdapter.setTopics(topics);
                binding.cardWeakTopics.setVisibility(View.VISIBLE);
            } else {
                binding.cardWeakTopics.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
