package com.example.daily_learn.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.databinding.FragmentLearningSessionBinding;
import com.example.daily_learn.domain.ai.AiTutorEngine;
import com.example.daily_learn.domain.ai.ChatMessage;
import com.example.daily_learn.domain.ai.ChatSessionManager;
import com.example.daily_learn.ui.adapters.ChatAdapter;
import com.example.daily_learn.viewmodel.LearningViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.dao.SubjectDao;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.domain.ai.LocalLLMEngine;
import com.example.daily_learn.domain.rag.RagService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LearningSessionFragment extends Fragment {

    private FragmentLearningSessionBinding binding;
    private LearningViewModel viewModel;
    private long topicId = -1L;
    private long sessionId = -1L;
    private String currentTopicName = "Topic";
    private String currentSubjectName = "Subject";

    private AiTutorEngine aiEngine;
    private LocalLLMEngine localLLMEngine;
    private RagService ragService;
    private ExecutorService aiExecutor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLearningSessionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(LearningViewModel.class);

        aiExecutor = Executors.newSingleThreadExecutor();
        localLLMEngine = new LocalLLMEngine();
        ragService = new RagService(requireContext(), AppDatabase.getInstance(requireContext()));
        aiEngine = new AiTutorEngine(localLLMEngine, ragService);

        aiExecutor.execute(() -> {
            localLLMEngine.loadModel("/data/local/tmp/benchmark_model.gguf", 99);
            ensureRagIndexed();
        });

        if (getArguments() != null) {
            topicId = getArguments().getLong("topicId", -1L);
            sessionId = getArguments().getLong("sessionId", -1L);
        }

        binding.toolbarLearning.setNavigationOnClickListener(v -> Navigation.findNavController(view).navigateUp());

        if (topicId != -1L) {
            viewModel.loadTopic(topicId, sessionId);
        }

        observeViewModel();

        binding.btnAskAiTutor.setOnClickListener(v -> showAiTutorBottomSheet());

        binding.btnUnderstood.setOnClickListener(v -> {
            viewModel.submitFeedback(0);
            Toast.makeText(requireContext(), "Great! Topic marked completed.", Toast.LENGTH_SHORT).show();
        });

        binding.btnNeedsRevision.setOnClickListener(v -> {
            viewModel.submitFeedback(1);
            Toast.makeText(requireContext(), "Topic added to your revision list.", Toast.LENGTH_SHORT).show();
        });

        binding.btnDifficult.setOnClickListener(v -> {
            viewModel.submitFeedback(2);
            Toast.makeText(requireContext(), "Flagged as difficult. We'll schedule extra practice.", Toast.LENGTH_SHORT).show();
        });

        binding.btnTakeQuiz.setOnClickListener(v -> {
            if (topicId != -1L) {
                Bundle args = new Bundle();
                args.putLong("topicId", topicId);
                Navigation.findNavController(view).navigate(R.id.action_learning_to_quiz, args);
            }
        });
    }

    private void observeViewModel() {
        viewModel.topic.observe(getViewLifecycleOwner(), topic -> {
            if (topic != null) {
                currentTopicName = topic.name;
                binding.tvLearningTopic.setText(topic.name);
                binding.tvLearningTime.setText("⏱ " + topic.estimatedMinutes + " min");
            }
        });

        viewModel.subject.observe(getViewLifecycleOwner(), subj -> {
            if (subj != null) {
                currentSubjectName = subj.name;
                binding.tvLearningSubject.setText(subj.name);
            }
        });

        viewModel.explanation.observe(getViewLifecycleOwner(), text -> {
            if (text != null) binding.tvExplanation.setText(text);
        });

        viewModel.keyConcepts.observe(getViewLifecycleOwner(), text -> {
            if (text != null) binding.tvKeyConcepts.setText(text);
        });

        viewModel.example.observe(getViewLifecycleOwner(), text -> {
            if (text != null) binding.tvExample.setText(text);
        });

        viewModel.importantPoints.observe(getViewLifecycleOwner(), text -> {
            if (text != null) binding.tvImportantPoints.setText(text);
        });

        viewModel.practiceQ.observe(getViewLifecycleOwner(), text -> {
            if (text != null) binding.tvPracticeQuestion.setText(text);
        });

        viewModel.sessionCompleted.observe(getViewLifecycleOwner(), completed -> {
            if (Boolean.TRUE.equals(completed)) {
                binding.btnUnderstood.setEnabled(false);
                binding.btnNeedsRevision.setEnabled(false);
                binding.btnDifficult.setEnabled(false);
            }
        });
    }

    private void showAiTutorBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.Theme_DailyLearn_BottomSheetDialog);
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_ai_tutor_sheet, null);
        dialog.setContentView(sheetView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.setOnShowListener(d -> {
            android.widget.FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<android.widget.FrameLayout> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setFitToContents(false);
                behavior.setDraggable(false); // Prevents scrolling up/down inside chat from closing the sheet
                bottomSheet.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();
            }
        });

        TextView tvSub = sheetView.findViewById(R.id.tv_ai_topic_sub);
        tvSub.setText("Ask anything about \"" + currentTopicName + "\" to clarify doubts");

        ImageButton btnClose = sheetView.findViewById(R.id.btn_close_ai_sheet);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        RecyclerView rvChat = sheetView.findViewById(R.id.rv_ai_chat);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        rvChat.setLayoutManager(layoutManager);
        rvChat.setItemAnimator(null);
        rvChat.setNestedScrollingEnabled(true);

        // Retrieve process-persistent chat history for this topic (lasts until user exits app)
        java.util.List<ChatMessage> chatHistory = ChatSessionManager.getHistory(currentTopicName);
        ChatAdapter chatAdapter = new ChatAdapter(chatHistory);
        rvChat.setAdapter(chatAdapter);

        // Scroll to latest message in history if exists
        if (chatAdapter.getItemCount() > 0) {
            rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
        }

        EditText etQuery = sheetView.findViewById(R.id.et_ai_query);
        View btnSend = sheetView.findViewById(R.id.btn_send_ai_query);

        etQuery.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                rvChat.postDelayed(() -> {
                    if (chatAdapter.getItemCount() > 0) {
                        rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                }, 200);
            }
        });

        etQuery.setOnClickListener(v -> {
            rvChat.postDelayed(() -> {
                if (chatAdapter.getItemCount() > 0) {
                    rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
                }
            }, 200);
        });

        // Helper to send query
        Runnable sendQuery = () -> {
            String text = etQuery.getText().toString().trim();
            if (text.isEmpty()) return;
            etQuery.setText("");

            chatAdapter.addMessage(new ChatMessage(text, true));
            
            // Add thinking placeholder
            int thinkingIndex = chatAdapter.getItemCount();
            chatAdapter.addMessage(new ChatMessage("Thinking...", false));
            rvChat.scrollToPosition(thinkingIndex);

            // Dynamically allocate generous token budget (512 default, 768 for detailed requests)
            int tokenBudget = 512;
            String qLower = text.toLowerCase();
            if (qLower.contains("detail") || qLower.contains("more") || qLower.contains("line") ||
                qLower.contains("elaborate") || qLower.contains("explain") || qLower.contains("long") ||
                qLower.contains("essay") || qLower.contains("notes") || qLower.contains("everything")) {
                tokenBudget = 768;
            }

            final int finalTokenBudget = tokenBudget;
            // Generate AI response in background using streaming
            aiExecutor.execute(() -> {
                aiEngine.generateResponseStreaming(currentTopicName, currentSubjectName, text, finalTokenBudget, new com.example.daily_learn.domain.ai.LlamaCppPoC.StreamCallback() {
                    private boolean isFirstToken = true;

                    @Override
                    public void onToken(String token) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                boolean wasAtBottom = !rvChat.canScrollVertically(1);
                                if (isFirstToken) {
                                    chatAdapter.updateMessage(thinkingIndex, new ChatMessage(token, false));
                                    isFirstToken = false;
                                } else {
                                    chatAdapter.appendStreamingToken(rvChat, thinkingIndex, token);
                                }
                                // Only auto-scroll to follow if this message is the latest message AND user was at bottom
                                if (wasAtBottom && thinkingIndex == chatAdapter.getItemCount() - 1) {
                                    rvChat.scrollToPosition(thinkingIndex);
                                }
                            });
                        }
                    }

                    @Override
                    public void onComplete(String fullResponse) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                boolean wasAtBottom = !rvChat.canScrollVertically(1);
                                String cleanResp = fullResponse;
                                if (cleanResp != null && cleanResp.trim().startsWith("NOT_FOUND")) {
                                    cleanResp = "The provided study material does not contain enough information to answer this.";
                                }
                                chatAdapter.updateMessage(thinkingIndex, new ChatMessage(cleanResp, false));
                                if (wasAtBottom && thinkingIndex == chatAdapter.getItemCount() - 1) {
                                    rvChat.scrollToPosition(thinkingIndex);
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                chatAdapter.updateMessage(thinkingIndex, new ChatMessage("Error: " + error, false));
                                if (thinkingIndex == chatAdapter.getItemCount() - 1) {
                                    rvChat.scrollToPosition(thinkingIndex);
                                }
                            });
                        }
                    }
                });
            });
        };

        btnSend.setOnClickListener(v -> sendQuery.run());

        etQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendQuery.run();
                return true;
            }
            return false;
        });

        // Quick Prompt Chips
        Chip chipAnalogy = sheetView.findViewById(R.id.chip_analogy);
        Chip chipCode = sheetView.findViewById(R.id.chip_code);
        Chip chipComplexity = sheetView.findViewById(R.id.chip_complexity);
        Chip chipExamQ = sheetView.findViewById(R.id.chip_exam_q);

        String combined = ((currentTopicName != null ? currentTopicName : "") + " " + (currentSubjectName != null ? currentSubjectName : "")).toLowerCase();
        boolean isDsaTopic = combined.matches(".*\\b(tree|bst|heap|graph|stack|queue|linked list|sorting|search|complexity|algorithm|dsa|big-o|traversal|recursion)\\b.*");

        if (!isDsaTopic) {
            chipComplexity.setText("🌐 Architecture");
        } else {
            chipComplexity.setText("⏱ Complexity");
        }

        chipAnalogy.setOnClickListener(v -> {
            etQuery.setText("Can you give me a simple real-world analogy for " + currentTopicName + "?");
            sendQuery.run();
        });

        chipCode.setOnClickListener(v -> {
            etQuery.setText("Show me a code example for " + currentTopicName);
            sendQuery.run();
        });

        chipComplexity.setOnClickListener(v -> {
            if (!isDsaTopic) {
                etQuery.setText("Explain the architecture, how it works, and key components of " + currentTopicName);
            } else {
                etQuery.setText("What is the time and space complexity of " + currentTopicName + "?");
            }
            sendQuery.run();
        });

        chipExamQ.setOnClickListener(v -> {
            etQuery.setText("What are the most common exam questions on " + currentTopicName + "?");
            sendQuery.run();
        });

        dialog.show();
    }

    /**
     * Ensures the local semantic RAG index is populated from the syllabus subjects in the
     * database before the AI tutor chat is used. Runs on the background aiExecutor; failures
     * are swallowed so a broken index never blocks the chat UI (the tutor degrades gracefully).
     */
    private void ensureRagIndexed() {
        try {
            if (ragService == null || ragService.isIndexed()) {
                return;
            }
            AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());
            SubjectDao subjectDao = db.subjectDao();
            long studentId = new SettingsStore(requireContext().getApplicationContext()).getStudentId();
            List<SubjectWithUnitsAndTopics> subjects = subjectDao.getAllSubjectsWithUnitsAndTopicsSync(studentId);
            if (subjects == null || subjects.isEmpty()) {
                return;
            }
            int totalChunks = 0;
            for (SubjectWithUnitsAndTopics subject : subjects) {
                if (subject == null || subject.subject == null || subject.units == null) continue;
                totalChunks += ragService.indexSyllabus(subject);
            }
            android.util.Log.i("LearningSessionFragment", "RAG index built: " + subjects.size()
                    + " subject(s), " + totalChunks + " chunk(s).");
        } catch (Throwable t) {
            android.util.Log.w("LearningSessionFragment", "RAG indexing skipped: " + t.getMessage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (localLLMEngine != null) {
            localLLMEngine.unloadModel();
        }
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        binding = null;
    }
}
