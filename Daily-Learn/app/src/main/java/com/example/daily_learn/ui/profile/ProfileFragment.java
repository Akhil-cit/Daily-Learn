package com.example.daily_learn.ui.profile;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.Student;
import com.example.daily_learn.databinding.FragmentProfileBinding;
import com.example.daily_learn.viewmodel.ProfileViewModel;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private Student currentStudent = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        // Switches setup
        binding.swDailyReminder.setChecked(viewModel.isDailyReminderEnabled());
        binding.swExamWarning.setChecked(viewModel.isExamWarningEnabled());
        binding.swBehindNotif.setChecked(viewModel.isBehindNotifEnabled());

        binding.swDailyReminder.setOnCheckedChangeListener((btn, checked) -> viewModel.setDailyReminder(checked));
        binding.swExamWarning.setOnCheckedChangeListener((btn, checked) -> viewModel.setExamWarning(checked));
        binding.swBehindNotif.setOnCheckedChangeListener((btn, checked) -> viewModel.setBehindNotif(checked));

        viewModel.student.observe(getViewLifecycleOwner(), student -> {
            if (student != null) {
                currentStudent = student;
                binding.tvProfileName.setText(student.name);
                binding.tvProfileCourse.setText(student.course + " • " + student.semester);
                binding.tvAvatar.setText(student.name.isEmpty() ? "S" : String.valueOf(student.name.charAt(0)).toUpperCase());

                binding.etProfileName.setText(student.name);
                binding.etProfileCourse.setText(student.course);
                binding.etProfileDailyMin.setText(String.valueOf(student.dailyStudyMinutes));
            }
        });

        binding.btnSaveProfile.setOnClickListener(v -> {
            if (currentStudent != null) {
                String name = binding.etProfileName.getText() != null ? binding.etProfileName.getText().toString().trim() : "";
                String course = binding.etProfileCourse.getText() != null ? binding.etProfileCourse.getText().toString().trim() : "";
                String minStr = binding.etProfileDailyMin.getText() != null ? binding.etProfileDailyMin.getText().toString().trim() : "120";

                if (!name.isEmpty()) {
                    currentStudent.name = name;
                    currentStudent.course = course;
                    try {
                        int minutes = Integer.parseInt(minStr);
                        currentStudent.dailyStudyMinutes = minutes;
                        viewModel.setDailyStudyMinutes(minutes);
                    } catch (NumberFormatException ignored) {}

                    viewModel.saveStudentProfile(currentStudent);
                }
            }
        });

        viewModel.saveDone.observe(getViewLifecycleOwner(), done -> {
            if (Boolean.TRUE.equals(done)) {
                Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to log out? All your syllabus progress, streaks, and study schedule are safely saved and will be restored when you log back in.")
                    .setPositiveButton("Log Out", (dialog, which) -> {
                        viewModel.logout();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.btnDeleteProfile.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Profile")
                    .setMessage("Are you sure you want to permanently delete this student profile and all associated study data?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (currentStudent != null) {
                            viewModel.deleteProfile(currentStudent.id);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        viewModel.logoutDone.observe(getViewLifecycleOwner(), loggedOut -> {
            if (Boolean.TRUE.equals(loggedOut)) {
                Toast.makeText(requireContext(), "Logged out. See you soon!", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(view).navigate(R.id.splashFragment);
            }
        });

        viewModel.deleteDone.observe(getViewLifecycleOwner(), deleted -> {
            if (Boolean.TRUE.equals(deleted)) {
                Toast.makeText(requireContext(), "Profile deleted.", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(view).navigate(R.id.splashFragment);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
