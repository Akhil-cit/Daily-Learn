package com.example.daily_learn.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.daily_learn.R;
import com.example.daily_learn.databinding.FragmentQuizResultBinding;

public class QuizResultFragment extends Fragment {

    private FragmentQuizResultBinding binding;
    private int score = 0;
    private int total = 0;
    private long topicId = -1L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQuizResultBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            score = getArguments().getInt("score", 0);
            total = getArguments().getInt("total", 0);
            topicId = getArguments().getLong("topicId", -1L);
        }

        binding.tvResultScore.setText(score + "/" + total);

        float pct = total > 0 ? ((float) score / total) : 0f;
        if (pct >= 0.8f) {
            binding.tvResultMessage.setText(R.string.quiz_result_great);
            binding.tvResultDetail.setText("Outstanding! You have mastered this concept.");
        } else if (pct >= 0.5f) {
            binding.tvResultMessage.setText(R.string.quiz_result_good);
            binding.tvResultDetail.setText("Good effort! Consider reviewing missed questions.");
        } else {
            binding.tvResultMessage.setText(R.string.quiz_result_needswork);
            binding.tvResultDetail.setText("We recommend revising this topic before the exam.");
        }

        binding.btnRetryQuiz.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong("topicId", topicId);
            Navigation.findNavController(view).navigate(R.id.quizFragment, args);
        });

        binding.btnBackToHome.setOnClickListener(v -> {
            Navigation.findNavController(view).navigate(R.id.action_result_to_home);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
