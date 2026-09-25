package com.example.daily_learn.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.Student;
import com.example.daily_learn.databinding.FragmentOnboardingBinding;
import com.example.daily_learn.viewmodel.OnboardingViewModel;

public class OnboardingFragment extends Fragment {

    private FragmentOnboardingBinding binding;
    private OnboardingViewModel viewModel;
    private Student primaryExistingStudent = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

        binding.btnCreateProfile.setOnClickListener(v -> {
            String name = binding.etName.getText() != null ? binding.etName.getText().toString() : "";
            String course = binding.etCourse.getText() != null ? binding.etCourse.getText().toString() : "";
            String semester = binding.etSemester.getText() != null ? binding.etSemester.getText().toString() : "";
            String dailyMinStr = binding.etDailyMinutes.getText() != null ? binding.etDailyMinutes.getText().toString() : "120";

            int dailyMinutes = 120;
            try {
                dailyMinutes = Integer.parseInt(dailyMinStr.trim());
            } catch (NumberFormatException ignored) {
            }

            viewModel.saveProfile(name, course, semester, dailyMinutes);
        });

        binding.btnContinueExisting.setOnClickListener(v -> {
            if (primaryExistingStudent != null) {
                viewModel.loginAs(primaryExistingStudent);
            }
        });

        viewModel.existingStudents.observe(getViewLifecycleOwner(), students -> {
            if (students != null && !students.isEmpty()) {
                primaryExistingStudent = students.get(0);
                binding.tvExistingStudentName.setText(primaryExistingStudent.name);
                binding.tvExistingStudentCourse.setText(primaryExistingStudent.course + " • " + primaryExistingStudent.semester);
                binding.cvExistingProfile.setVisibility(View.VISIBLE);
                binding.tvFormTitle.setText("Or Create New Profile");
            } else {
                binding.cvExistingProfile.setVisibility(View.GONE);
                binding.tvFormTitle.setText("Create Student Profile");
            }
        });

        viewModel.profileSaved.observe(getViewLifecycleOwner(), saved -> {
            if (Boolean.TRUE.equals(saved)) {
                Navigation.findNavController(view).navigate(R.id.action_onboarding_to_home);
            }
        });

        viewModel.error.observe(getViewLifecycleOwner(), errMsg -> {
            if (errMsg != null && !errMsg.isEmpty()) {
                binding.tvError.setText(errMsg);
                binding.tvError.setVisibility(View.VISIBLE);
            } else {
                binding.tvError.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
