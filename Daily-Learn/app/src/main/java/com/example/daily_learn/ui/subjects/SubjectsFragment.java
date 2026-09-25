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
import com.example.daily_learn.databinding.FragmentSubjectsBinding;
import com.example.daily_learn.ui.adapters.SubjectAdapter;
import com.example.daily_learn.viewmodel.SubjectsViewModel;

public class SubjectsFragment extends Fragment {

    private FragmentSubjectsBinding binding;
    private SubjectsViewModel viewModel;
    private SubjectAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSubjectsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SubjectsViewModel.class);

        adapter = new SubjectAdapter(subjectId -> {
            Bundle args = new Bundle();
            args.putLong("subjectId", subjectId);
            Navigation.findNavController(view).navigate(R.id.action_subjects_to_detail, args);
        });

        binding.rvSubjects.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSubjects.setAdapter(adapter);

        binding.toolbarSubjects.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit_syllabus) {
                Bundle args = new Bundle();
                args.putLong("subjectId", -1L);
                Navigation.findNavController(view).navigate(R.id.action_subjects_to_editor, args);
                return true;
            }
            return false;
        });

        binding.fabUploadSyllabus.setOnClickListener(v -> {
            Navigation.findNavController(view).navigate(R.id.action_subjects_to_upload);
        });

        viewModel.subjects.observe(getViewLifecycleOwner(), subjectsList -> {
            if (subjectsList != null) {
                adapter.setSubjects(subjectsList);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
