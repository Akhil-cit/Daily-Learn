package com.example.daily_learn.ui.upload;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.navigation.Navigation;

import com.example.daily_learn.R;
import com.example.daily_learn.databinding.FragmentSyllabusUploadBinding;
import com.example.daily_learn.domain.parser.SyllabusTextStructurer;
import com.example.daily_learn.viewmodel.ManualEditorViewModel;
import com.example.daily_learn.viewmodel.SyllabusUploadViewModel;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SyllabusUploadFragment extends Fragment {

    private FragmentSyllabusUploadBinding binding;
    private SyllabusUploadViewModel uploadViewModel;
    private ManualEditorViewModel editorViewModel;

    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> pdfPickerLauncher;
    private ActivityResultLauncher<String> textPickerLauncher;

    private List<SyllabusTextStructurer.ParsedSubject> extractedSubjects = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) uploadViewModel.parseImage(uri);
                }
        );

        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handlePdfUri
        );

        textPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleTextUri
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSyllabusUploadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        uploadViewModel = new ViewModelProvider(this).get(SyllabusUploadViewModel.class);
        editorViewModel = new ViewModelProvider(this).get(ManualEditorViewModel.class);

        binding.toolbarUpload.setNavigationOnClickListener(v -> Navigation.findNavController(view).navigateUp());

        binding.btnPickImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        binding.btnPickPdf.setOnClickListener(v -> pdfPickerLauncher.launch("application/pdf"));
        binding.btnPickText.setOnClickListener(v -> textPickerLauncher.launch("text/*"));
        binding.btnPasteText.setOnClickListener(v -> showPasteDialog());

        binding.btnEditManually.setOnClickListener(v -> {
            Navigation.findNavController(view).navigate(R.id.action_upload_to_editor);
        });

        binding.btnConfirmImport.setOnClickListener(v -> {
            if (extractedSubjects != null && !extractedSubjects.isEmpty()) {
                long examDate = System.currentTimeMillis() + (30L * 86_400_000L);
                editorViewModel.importParsed(extractedSubjects, examDate, () -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "🎉 Syllabus imported! Study plan generated.", Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(view).navigate(R.id.action_upload_to_editor);
                        });
                    }
                });
            }
        });

        observeViewModel();
    }

    private void handlePdfUri(Uri uri) {
        if (uri == null) return;
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is != null) {
                uploadViewModel.parsePdf(is);
            }
        } catch (Exception e) {
            uploadViewModel.parseErrorMessage.setValue("Could not open PDF file.");
        }
    }

    private void handleTextUri(Uri uri) {
        if (uri == null) return;
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                uploadViewModel.parseText(sb.toString());
            }
        } catch (Exception e) {
            uploadViewModel.parseErrorMessage.setValue("Could not read text file.");
        }
    }

    private void showPasteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Paste Syllabus Text");

        final EditText input = new EditText(requireContext());
        input.setHint("Paste course topics, units, or table of contents here...");
        input.setMinLines(6);
        input.setMaxLines(15);
        input.setPadding(32, 24, 32, 24);
        builder.setView(input);

        builder.setPositiveButton("Parse Topics", (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                uploadViewModel.parseText(text);
            } else {
                Toast.makeText(requireContext(), "Please paste syllabus text first", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void observeViewModel() {
        uploadViewModel.loading.observe(getViewLifecycleOwner(), loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            binding.pbUploading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnPickImage.setEnabled(!isLoading);
            binding.btnPickPdf.setEnabled(!isLoading);
            binding.btnPickText.setEnabled(!isLoading);
            binding.btnPasteText.setEnabled(!isLoading);
        });

        uploadViewModel.parseSuccess.observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                extractedSubjects = uploadViewModel.parsedSubjects.getValue();
                int totalSubjects = extractedSubjects != null ? extractedSubjects.size() : 0;
                int totalTopics = 0;
                if (extractedSubjects != null) {
                    for (SyllabusTextStructurer.ParsedSubject s : extractedSubjects) {
                        for (SyllabusTextStructurer.ParsedUnit u : s.units) {
                            totalTopics += u.topics.size();
                        }
                    }
                }

                String summary = "Found " + totalSubjects + " subject(s) with " + totalTopics + " total topics.";
                binding.tvUploadStatus.setText(summary);
                binding.cvParsedSummary.setVisibility(View.VISIBLE);
            }
        });

        uploadViewModel.parseErrorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
