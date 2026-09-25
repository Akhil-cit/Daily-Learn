package com.example.daily_learn.ui.upload;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import androidx.navigation.Navigation;
import com.example.daily_learn.R;
import com.example.daily_learn.databinding.FragmentSyllabusAnalyzerBinding;
import com.example.daily_learn.domain.ai.SyllabusAnalysisResult;
import com.example.daily_learn.ui.adapters.DailyTopicAdapter;
import com.example.daily_learn.viewmodel.SyllabusAnalysisViewModel;

public class SyllabusAnalysisFragment extends Fragment {

    private FragmentSyllabusAnalyzerBinding binding;
    private SyllabusAnalysisViewModel viewModel;
    private DailyTopicAdapter topicAdapter;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && getContext() != null) {
                    try {
                        getContext().getContentResolver().takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (Exception ignored) {}
                    String fileName = getFileName(uri);
                    viewModel.setFile(uri, fileName);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSyllabusAnalyzerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SyllabusAnalysisViewModel.class);

        setupRecyclerView();
        setupListeners();
        observeViewModel();
    }

    private void setupRecyclerView() {
        topicAdapter = new DailyTopicAdapter();
        binding.rvDailyTopics.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDailyTopics.setAdapter(topicAdapter);
    }

    private void setupListeners() {
        // Upload Button -> Open Document/Image Picker
        binding.btnUploadSyllabus.setOnClickListener(v -> {
            filePickerLauncher.launch(new String[]{
                    "application/pdf",
                    "image/jpeg",
                    "image/png",
                    "image/*",
                    "text/plain"
            });
        });

        // Paste Text Dialog
        binding.btnPasteSyllabus.setOnClickListener(v -> showPasteDialog());

        // Days input watcher
        binding.etTargetDays.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && !s.toString().trim().isEmpty()) {
                    try {
                        int days = Integer.parseInt(s.toString().trim());
                        if (days > 0 && days <= 365) {
                            viewModel.setTargetDays(days);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        // Analyze Button
        binding.btnAnalyzeSyllabus.setOnClickListener(v -> {
            try {
                String daysStr = binding.etTargetDays.getText().toString().trim();
                int days = daysStr.isEmpty() ? 20 : Integer.parseInt(daysStr);
                viewModel.setTargetDays(days);
            } catch (Exception ignored) {}
            viewModel.analyze();
        });

        // Copy JSON Button
        binding.btnCopyJson.setOnClickListener(v -> {
            SyllabusAnalysisResult result = viewModel.getAnalysisResult().getValue();
            if (result != null && getContext() != null) {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Syllabus JSON", result.toJsonString());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "Copied structured JSON to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        // Save Plan & Start Learning Button
        binding.btnSaveAndStartLearning.setOnClickListener(v -> {
            SyllabusAnalysisResult result = viewModel.getAnalysisResult().getValue();
            if (result == null || result.days == null || result.days.isEmpty()) {
                Toast.makeText(requireContext(), "Please analyze a syllabus first", Toast.LENGTH_SHORT).show();
                return;
            }
            binding.btnSaveAndStartLearning.setEnabled(false);
            binding.btnSaveAndStartLearning.setText("Saving plan to database...");
            viewModel.savePlanAndStart(result, (subjectId, firstTopicId, firstSessionId) -> {
                if (getContext() == null) return;
                Toast.makeText(requireContext(), "Plan saved! Starting Day 1 topic...", Toast.LENGTH_LONG).show();
                if (firstTopicId != -1L) {
                    Bundle args = new Bundle();
                    args.putLong("topicId", firstTopicId);
                    args.putLong("sessionId", firstSessionId);
                    try {
                        Navigation.findNavController(v).navigate(R.id.action_analyzer_to_learning, args);
                    } catch (Exception e) {
                        Navigation.findNavController(v).navigate(R.id.action_analyzer_to_home);
                    }
                } else {
                    Navigation.findNavController(v).navigate(R.id.action_analyzer_to_home);
                }
            });
        });
    }

    private void observeViewModel() {
        // Selected File name / preview
        viewModel.getSelectedFileName().observe(getViewLifecycleOwner(), fileName -> {
            if (fileName != null && !fileName.isEmpty()) {
                binding.tvSelectedFilename.setText(fileName);
                binding.layoutFilePreview.setVisibility(View.VISIBLE);
            } else {
                binding.layoutFilePreview.setVisibility(View.GONE);
            }
        });

        // Loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.cardLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.btnAnalyzeSyllabus.setEnabled(!loading);
            binding.btnUploadSyllabus.setEnabled(!loading);
        });

        // Loading message
        viewModel.getProgressMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                binding.tvLoadingStatus.setText(msg);
            }
        });

        // Results
        viewModel.getAnalysisResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null) {
                binding.layoutResultsHeader.setVisibility(View.VISIBLE);
                binding.rvDailyTopics.setVisibility(View.VISIBLE);

                binding.tvResultCourseName.setText(result.courseName);
                if (result.courseCode != null && !result.courseCode.isEmpty()) {
                    binding.tvResultCourseCode.setText(result.courseCode);
                    binding.tvResultCourseCode.setVisibility(View.VISIBLE);
                } else {
                    binding.tvResultCourseCode.setVisibility(View.GONE);
                }

                binding.tvResultTotalDays.setText("TOTAL DAYS: " + result.totalDays + "  (" + result.totalTopicsCount + " Topics)");
                topicAdapter.setItems(result.days);

                // Scroll smoothly to results
                binding.rvDailyTopics.post(() -> {
                    if (binding != null) {
                        binding.getRoot().smoothScrollTo(0, binding.layoutResultsHeader.getTop());
                    }
                });
            } else {
                binding.layoutResultsHeader.setVisibility(View.GONE);
                binding.rvDailyTopics.setVisibility(View.GONE);
            }
        });

        // Error message
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), "⚠ " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showPasteDialog() {
        if (getContext() == null) return;
        EditText input = new EditText(requireContext());
        input.setHint("Paste syllabus text (Units, Chapters, Topics)...");
        input.setMinLines(6);
        input.setMaxLines(15);
        input.setPadding(32, 32, 32, 32);

        new AlertDialog.Builder(requireContext())
                .setTitle("Paste Syllabus Content")
                .setView(input)
                .setPositiveButton("Use This Syllabus", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        viewModel.setTextContent(text, "Pasted_Syllabus.txt");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getFileName(Uri uri) {
        String result = "Syllabus_Document";
        if (getContext() != null && "content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static final String SAMPLE_OOP_SYLLABUS =
            "Course Description\n" +
            "25SS212 / OBJECT ORIENTED PROGRAMMING\n" +
            "Semester II Year December 2025 – May 2026\n" +
            "Name of the Faculty : Dr. S. Manjula Gandhi\n" +
            "E-mail: manjulagandhi@cit.edu.in/9443128501\n" +
            "LinkedIn: https://www.linkedin.com/in/dr-manjula-gandhi-s-3a4b6a110/\n" +
            "Class Schedule\n" +
            "Lecture: Days: Monday, Tuesday, Thursday Timings: 9:25-10:20, 11:30-12:25, 10:35-11:30\n" +
            "Hours for meeting students: Thursday 3:50 – 4:45 p.m.\n" +
            "i)Course Objective\n" +
            "1. For a given scenario compare and criticize alternate implementations using functional programming and object oriented programming in view of privacy.\n" +
            "2. Create persistent objects using stream class hierarchy incorporating exceptional handling.\n" +
            "3. Compare and contrast function overloading and default argument function for appropriate usage in an object oriented system.\n" +
            "4. For a given scenario, construct an object oriented system using appropriate type of inheritance for realizing reusability.\n" +
            "5. Construct and compare object oriented systems using static binding, dynamic binding and template classes for realizing polymorphism.\n" +
            "ii) Once the student has successfully completed this course, he/she must be able to answer the following questions or perform/demonstrate the following:\n" +
            "1. Understand the features of C++\n" +
            "2. Will be able to write classes and work with objects\n" +
            "3. Will be able to define and bind property and methods together\n" +
            "4. Differentiate function overloading and passing default values to functions\n" +
            "5. Work with constructors and destructors\n" +
            "6. Dynamic allocation and deallocation of memory\n" +
            "7. Demonstrate the working of File I/O streams\n" +
            "8. Will know how to overload unary, binary operators\n" +
            "9. Understand the various ways to inherit a class using access specifier\n" +
            "10. Implement types of inheritance\n" +
            "11. Implement Dynamic polymorphism\n" +
            "12. Differentiate static and dynamic polymorphism\n" +
            "13. Working with Friend functions and Friend classes\n" +
            "14. Working with Function templates, Class templates\n" +
            "15. Exception Handling\n" +
            "iii) Topic/Unit/Chapter Layout\n" +
            "Topic Lecture Hours\n" +
            "PRINCIPLES OF OBJECT ORIENTED PROGRAMMING\n" +
            "Software crisis, Software Evolution - Procedure Oriented Programming - Object Oriented\n" +
            "Programming Paradigm - Basic Concepts and Benefits of OOP - Object Oriented\n" +
            "Programming Language - Application of OOP - Structure of C++ 3\n" +
            "Tokens, Expressions and Control Structures - Operators in C++ - Classes and Objects -\n" +
            "Member functions – Nesting of Member functions - Private member functions - Memory\n" +
            "allocation for Objects - Static data members - Static Member Functions - Arrays of\n" +
            "Objects - Objects as Function Arguments – Returning objects-Manipulators. 6\n" +
            "FUNCTIONS IN C++\n" +
            "Function Prototyping - Call by Reference - Return by reference - Inline functions -\n" +
            "Default, Const Arguments - Function Overloading - Friend Functions - Friend Class -\n" +
            "Const Member functions - Pointers to Members – Passing default values to functions 5\n" +
            "CONSTRUCTORS: Parameterized Constructors - Multiple Constructors in a Class -\n" +
            "Constructors with Default Arguments - Dynamic Initialization of Objects - Copy\n" +
            "Constructors – Destructors 4\n" +
            "STREAMS AND FILES\n" +
            "Streams classes - Stream Errors - Disk File I/O with streams - File pointers - Error\n" +
            "handling in file I/O with member function - Command line arguments. 6\n" +
            "INHERITANCE AND POLYMORPHISM\n" +
            "Inheritance: Introduction and benefits - Access Specifier - Base and Derived Class\n" +
            "Constructors - Types of Inheritance - Down casting and up casting- Virtual base class. 5\n" +
            "Polymorphism: Defining Operator Overloading - Overloading Unary Operators -\n" +
            "Overloading Binary Operators - Overloading Binary Operators using Friends -\n" +
            "Manipulation of Strings using Operators - Rules of Operator Overloading - Type\n" +
            "Conversion. 5\n" +
            "Function Overriding - Virtual functions – Pure virtual function. 3\n" +
            "TEMPLATES AND EXCEPTIONS\n" +
            "Function templates, Class templates. Exceptions-Grouping of exceptions - Catching\n" +
            "exceptions – Exceptions that are not errors – Uncaught Exceptions – Standard exceptions. 8\n" +
            "iv) Reference books\n" +
            "1. Bjarne Stroustrup, “The C++ Programming Language”, 3rd edition, Pearson Education, 2000.\n" +
            "2. Tony Gaddis, \"Starting Out with C++ from Control Structures to Objects \", 8th edition, Pearson Education, 2014.\n" +
            "3. Ira Pohl, “Object Oriented Programming Using C++”, 2nd Edition, Pearson Education, 2006.\n" +
            "4. Herbert Schildt, “The Complete Reference C++”, 4th edition, Tata McGraw Hill, 2003.\n" +
            "5. Stanley B Lippman, Jove Lajoie, and Barbara Moo “C++ Primer”, 5th Edition, Addison Wesley, 2012.\n" +
            "v) Evaluation Scheme\n" +
            "Evaluation Criteria Conducted Marks Converted Marks\n" +
            "Sessional Exam 1 60 100\n" +
            "Assignment –Set 1 40\n" +
            "Sessional Exam 2 60 100\n" +
            "Assignment – Set 2 40\n" +
            "Internal Marks 40\n" +
            "External Exam 100 60\n" +
            "Total 100\n";
}
