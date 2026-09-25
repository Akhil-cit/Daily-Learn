package com.example.daily_learn.ui.subjects;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.local.entity.UnitWithTopics;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.data.repository.StudyPlanRepository;
import com.example.daily_learn.data.repository.SubjectRepository;
import com.example.daily_learn.databinding.FragmentManualEditorBinding;
import com.example.daily_learn.ui.adapters.ManualEditorAdapter;
import com.example.daily_learn.viewmodel.ManualEditorViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ManualEditorFragment extends Fragment implements ManualEditorAdapter.EditorActionListener {

    private FragmentManualEditorBinding binding;
    private ManualEditorViewModel viewModel;
    private SubjectRepository subjectRepo;
    private StudyPlanRepository planRepo;
    private SettingsStore settings;
    private ManualEditorAdapter adapter;

    private List<SubjectWithUnitsAndTopics> currentData = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentManualEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ManualEditorViewModel.class);
        subjectRepo = new SubjectRepository(requireActivity().getApplication());
        planRepo = new StudyPlanRepository(requireActivity().getApplication());
        settings = new SettingsStore(requireContext());

        binding.toolbarEditor.setNavigationOnClickListener(v -> Navigation.findNavController(view).navigateUp());

        adapter = new ManualEditorAdapter(this);
        binding.rvEditorSubjects.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvEditorSubjects.setAdapter(adapter);

        long studentId = settings.getStudentId();
        subjectRepo.getAllSubjectsWithUnitsAndTopics(studentId).observe(getViewLifecycleOwner(), subjects -> {
            if (subjects != null) {
                currentData = subjects;
                adapter.setData(subjects);
            }
        });

        binding.btnAddSubject.setOnClickListener(v -> showAddSubjectDialog());

        binding.btnGeneratePlan.setOnClickListener(v -> generatePlan());
    }

    private void showAddSubjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Add New Subject");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        final EditText etName = new EditText(requireContext());
        etName.setHint("Subject Name (e.g. Operating Systems)");
        layout.addView(etName);

        final Spinner spPriority = new Spinner(requireContext());
        ArrayAdapter<String> priorityAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Low Priority", "Medium Priority", "High Priority"});
        spPriority.setAdapter(priorityAdapter);
        spPriority.setSelection(1);
        layout.addView(spPriority);

        final Spinner spDifficulty = new Spinner(requireContext());
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Easy", "Medium", "Hard"});
        spDifficulty.setAdapter(diffAdapter);
        spDifficulty.setSelection(1);
        layout.addView(spDifficulty);

        builder.setView(layout);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                int priority = spPriority.getSelectedItemPosition() + 1;
                int difficulty = spDifficulty.getSelectedItemPosition() + 1;
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, 30);
                viewModel.addSubject(name, priority, difficulty, cal.getTimeInMillis());
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onAddUnit(Subject subject) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Add Unit to " + subject.name);

        final EditText etName = new EditText(requireContext());
        etName.setHint("Unit Name (e.g. Unit I – Memory Management)");
        builder.setView(etName);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                viewModel.addUnit(subject.id, name, 1);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onDeleteSubject(Subject subject) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Subject")
                .setMessage("Are you sure you want to delete " + subject.name + " and all its units/topics?")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteSubject(subject))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onAddTopic(Unit unit, long subjectId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Add Topic to " + unit.name);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        final EditText etName = new EditText(requireContext());
        etName.setHint("Topic Name (e.g. Paging & Segmentation)");
        layout.addView(etName);

        final EditText etMinutes = new EditText(requireContext());
        etMinutes.setHint("Estimated Minutes (default: 45)");
        etMinutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etMinutes.setText("45");
        layout.addView(etMinutes);

        final Spinner spDifficulty = new Spinner(requireContext());
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Easy", "Medium", "Hard"});
        spDifficulty.setAdapter(diffAdapter);
        spDifficulty.setSelection(1);
        layout.addView(spDifficulty);

        builder.setView(layout);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                int minutes = 45;
                try {
                    minutes = Integer.parseInt(etMinutes.getText().toString().trim());
                } catch (NumberFormatException ignored) {}
                int difficulty = spDifficulty.getSelectedItemPosition() + 1;
                viewModel.addTopic(unit.id, subjectId, name, difficulty, minutes, 1);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onDeleteUnit(Unit unit) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Unit")
                .setMessage("Are you sure you want to delete " + unit.name + " and its topics?")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteUnit(unit))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onEditTopic(Topic topic) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Edit Topic");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        final EditText etName = new EditText(requireContext());
        etName.setText(topic.name);
        layout.addView(etName);

        final EditText etMinutes = new EditText(requireContext());
        etMinutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etMinutes.setText(String.valueOf(topic.estimatedMinutes));
        layout.addView(etMinutes);

        final Spinner spDifficulty = new Spinner(requireContext());
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Easy", "Medium", "Hard"});
        spDifficulty.setAdapter(diffAdapter);
        spDifficulty.setSelection(Math.max(0, topic.difficulty - 1));
        layout.addView(spDifficulty);

        final CheckBox cbCompleted = new CheckBox(requireContext());
        cbCompleted.setText("Mark as Completed");
        cbCompleted.setChecked(topic.isCompleted);
        layout.addView(cbCompleted);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                topic.name = name;
                try {
                    topic.estimatedMinutes = Integer.parseInt(etMinutes.getText().toString().trim());
                } catch (NumberFormatException ignored) {}
                topic.difficulty = spDifficulty.getSelectedItemPosition() + 1;
                topic.isCompleted = cbCompleted.isChecked();
                viewModel.updateTopic(topic);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onDeleteTopic(Topic topic) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Topic")
                .setMessage("Are you sure you want to delete " + topic.name + "?")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteTopic(topic))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void generatePlan() {
        long studentId = settings.getStudentId();
        int dailyMinutes = settings.getDailyStudyMinutes();

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            long examDate = System.currentTimeMillis() + (30L * 86_400_000L);

            com.example.daily_learn.data.local.entity.StudyPlan plan = db.studyPlanDao().getLatestPlanSync(studentId);
            if (plan == null) {
                plan = new com.example.daily_learn.data.local.entity.StudyPlan(studentId, examDate);
                long pId = db.studyPlanDao().insertStudyPlan(plan);
                plan.id = pId;
            }

            List<Topic> incomplete = new ArrayList<>();
            if (currentData != null) {
                for (SubjectWithUnitsAndTopics swu : currentData) {
                    if (swu.units != null) {
                        for (UnitWithTopics u : swu.units) {
                            if (u.topics != null) {
                                for (Topic t : u.topics) {
                                    if (!t.isCompleted) {
                                        incomplete.add(t);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            final long finalPlanId = plan.id;
            final long finalExamDate = examDate;
            planRepo.regeneratePlan(finalPlanId, incomplete, finalExamDate, dailyMinutes, () -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Study plan updated successfully!", Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(binding.getRoot()).navigateUp();
                    });
                }
            });
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
