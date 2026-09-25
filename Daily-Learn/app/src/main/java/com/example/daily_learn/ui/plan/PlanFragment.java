package com.example.daily_learn.ui.plan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.daily_learn.databinding.FragmentPlanBinding;
import com.example.daily_learn.domain.scheduler.StudyPlanGenerator;
import com.example.daily_learn.ui.adapters.PlanTimelineAdapter;
import com.example.daily_learn.viewmodel.PlanViewModel;

public class PlanFragment extends Fragment {

    private FragmentPlanBinding binding;
    private PlanViewModel viewModel;
    private PlanTimelineAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PlanViewModel.class);

        long todayStart = StudyPlanGenerator.todayMidnightMillis();
        adapter = new PlanTimelineAdapter(todayStart);

        binding.rvPlanTimeline.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlanTimeline.setAdapter(adapter);

        viewModel.allSessions.observe(getViewLifecycleOwner(), sessions -> {
            if (sessions != null) {
                adapter.setSessions(sessions);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
