package com.example.daily_learn.ui.learning;

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

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.QuizQuestion;
import com.example.daily_learn.data.local.entity.QuizWithQuestions;
import com.example.daily_learn.databinding.FragmentQuizBinding;
import com.example.daily_learn.viewmodel.QuizViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class QuizFragment extends Fragment {

    private FragmentQuizBinding binding;
    private QuizViewModel viewModel;
    private long topicId = -1L;

    private List<MaterialButton> optionButtons = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQuizBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(QuizViewModel.class);

        if (getArguments() != null) {
            topicId = getArguments().getLong("topicId", -1L);
        }

        binding.toolbarQuiz.setNavigationOnClickListener(v -> Navigation.findNavController(view).navigateUp());

        optionButtons.clear();
        optionButtons.add(binding.btnOptionA);
        optionButtons.add(binding.btnOptionB);
        optionButtons.add(binding.btnOptionC);
        optionButtons.add(binding.btnOptionD);

        for (int i = 0; i < optionButtons.size(); i++) {
            final int index = i;
            optionButtons.get(i).setOnClickListener(v -> {
                if (!Boolean.TRUE.equals(viewModel.answerSubmitted.getValue())) {
                    viewModel.selectedAnswer.setValue(index);
                    viewModel.submitAnswer(index);
                }
            });
        }

        binding.btnNextQuestion.setOnClickListener(v -> viewModel.nextQuestion());

        if (topicId != -1L) {
            viewModel.loadQuiz(topicId);
        }

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.quizWithQuestions.observe(getViewLifecycleOwner(), qwq -> {
            if (qwq != null && !qwq.questions.isEmpty()) {
                displayQuestion(qwq, 0);
            }
        });

        viewModel.currentQuestionIndex.observe(getViewLifecycleOwner(), index -> {
            QuizWithQuestions qwq = viewModel.quizWithQuestions.getValue();
            if (qwq != null && index != null && index < qwq.questions.size()) {
                displayQuestion(qwq, index);
            }
        });

        viewModel.answerSubmitted.observe(getViewLifecycleOwner(), submitted -> {
            if (Boolean.TRUE.equals(submitted)) {
                QuizWithQuestions qwq = viewModel.quizWithQuestions.getValue();
                Integer currentIdx = viewModel.currentQuestionIndex.getValue();
                Integer selected = viewModel.selectedAnswer.getValue();
                if (qwq != null && currentIdx != null && selected != null) {
                    QuizQuestion q = qwq.questions.get(currentIdx);
                    boolean isCorrect = (selected == q.correctIndex);

                    for (int i = 0; i < optionButtons.size(); i++) {
                        MaterialButton btn = optionButtons.get(i);
                        btn.setEnabled(false);
                        if (i == q.correctIndex) {
                            btn.setBackgroundColor(Color.parseColor("#43A047"));
                            btn.setTextColor(Color.WHITE);
                        } else if (i == selected) {
                            btn.setBackgroundColor(Color.parseColor("#EF5350"));
                            btn.setTextColor(Color.WHITE);
                        }
                    }

                    binding.tvAnswerFeedback.setText(isCorrect ? "✓ Correct!" : "✗ Incorrect");
                    binding.tvAnswerFeedback.setTextColor(isCorrect ? Color.parseColor("#43A047") : Color.parseColor("#EF5350"));
                    binding.tvAnswerFeedback.setVisibility(View.VISIBLE);

                    boolean isLast = (currentIdx + 1 >= qwq.questions.size());
                    binding.btnNextQuestion.setText(isLast ? "Finish Quiz" : "Next Question →");
                    binding.btnNextQuestion.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.quizFinished.observe(getViewLifecycleOwner(), finished -> {
            if (Boolean.TRUE.equals(finished)) {
                Bundle args = new Bundle();
                args.putInt("score", viewModel.getScore());
                args.putInt("total", viewModel.getTotalQuestions());
                args.putLong("topicId", viewModel.getTopicId());
                Navigation.findNavController(requireView()).navigate(R.id.action_quiz_to_result, args);
            }
        });
    }

    private void displayQuestion(QuizWithQuestions qwq, int index) {
        QuizQuestion q = qwq.questions.get(index);
        int total = qwq.questions.size();

        binding.tvQuestionCounter.setText(getString(R.string.quiz_question_number, (index + 1), total));
        binding.pbQuizProgress.setProgress((int) (((index + 1) * 100f) / total));

        binding.tvQuestionText.setText(q.questionText);

        List<String> options = QuizViewModel.parseOptions(q.optionsJson);
        for (int i = 0; i < optionButtons.size(); i++) {
            MaterialButton btn = optionButtons.get(i);
            btn.setEnabled(true);
            btn.setBackgroundColor(Color.TRANSPARENT);
            btn.setTextColor(Color.parseColor("#5C6BC0"));
            if (i < options.size()) {
                btn.setText(options.get(i));
                btn.setVisibility(View.VISIBLE);
            } else {
                btn.setVisibility(View.GONE);
            }
        }

        binding.tvAnswerFeedback.setVisibility(View.GONE);
        binding.btnNextQuestion.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
