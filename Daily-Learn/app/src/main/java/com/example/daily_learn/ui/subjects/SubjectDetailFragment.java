package com.example.daily_learn.ui.subjects;

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
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.UnitWithTopics;
import com.example.daily_learn.databinding.FragmentSubjectDetailBinding;
import com.example.daily_learn.ui.adapters.UnitTopicAdapter;
import com.example.daily_learn.viewmodel.SubjectDetailViewModel;

public class SubjectDetailFragment extends Fragment {

    private FragmentSubjectDetailBinding binding;
    private SubjectDetailViewModel viewModel;
    private UnitTopicAdapter adapter;
    private long subjectId = -1L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSubjectDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SubjectDetailViewModel.class);

        if (getArguments() != null) {
            subjectId = getArguments().getLong("subjectId", -1L);
        }

        binding.toolbarDetail.setNavigationOnClickListener(v -> {
            Navigation.findNavController(view).navigateUp();
        });

        binding.toolbarDetail.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit_subject) {
                Bundle args = new Bundle();
                args.putLong("subjectId", subjectId);
                Navigation.findNavController(view).navigate(R.id.action_detail_to_editor, args);
                return true;
            }
            return false;
        });

        adapter = new UnitTopicAdapter(topic -> {
            if (topic != null) {
                Bundle args = new Bundle();
                args.putLong("topicId", topic.id);
                args.putLong("sessionId", -1L);
                Navigation.findNavController(view).navigate(R.id.action_detail_to_learning, args);
            }
        });

        binding.rvUnitsTopics.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUnitsTopics.setAdapter(adapter);

        if (subjectId != -1L) {
            viewModel.loadSubject(subjectId);
            viewModel.subjectDetail.observe(getViewLifecycleOwner(), subjectWithUnits -> {
                if (subjectWithUnits != null) {
                    binding.tvSubjectTitle.setText(subjectWithUnits.subject.name);

                    // Compute overall completion
                    int total = 0, done = 0;
                    if (subjectWithUnits.units != null) {
                        for (UnitWithTopics u : subjectWithUnits.units) {
                            if (u.topics != null) {
                                for (Topic t : u.topics) {
                                    total++;
                                    if (t.isCompleted) done++;
                                }
                            }
                        }
                    }

                    int pct = total > 0 ? (int) ((done * 100f) / total) : 0;
                    binding.pbSubjectDetail.setProgress(pct);
                    binding.tvSubjectDetailPercent.setText(pct + "% (" + done + "/" + total + " topics)");

                    adapter.setData(subjectWithUnits.units);
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
