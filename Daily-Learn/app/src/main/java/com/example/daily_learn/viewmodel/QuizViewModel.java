package com.example.daily_learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.daily_learn.data.local.entity.QuizQuestion;
import com.example.daily_learn.data.local.entity.QuizResult;
import com.example.daily_learn.data.local.entity.QuizWithQuestions;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.data.repository.ProgressRepository;
import com.example.daily_learn.data.repository.QuizRepository;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizViewModel extends AndroidViewModel {

    private final QuizRepository quizRepo;
    private final ProgressRepository progressRepo;
    private final SettingsStore settings;

    private final MediatorLiveData<QuizWithQuestions> _quizWithQuestions = new MediatorLiveData<>();
    public final LiveData<QuizWithQuestions> quizWithQuestions = _quizWithQuestions;
    public final MutableLiveData<Integer> currentQuestionIndex = new MutableLiveData<>(0);
    public final MutableLiveData<Integer> selectedAnswer       = new MutableLiveData<>(-1);
    public final MutableLiveData<Boolean> answerSubmitted      = new MutableLiveData<>(false);
    public final MutableLiveData<Integer> score                = new MutableLiveData<>(0);
    public final MutableLiveData<Boolean> quizFinished         = new MutableLiveData<>(false);

    private long topicId;
    private long quizId;
    private int correctCount = 0;
    private int totalQuestions = 0;
    private boolean quizRandomized = false;
    private LiveData<QuizWithQuestions> currentRepoSource = null;

    public QuizViewModel(@NonNull Application app) {
        super(app);
        quizRepo     = new QuizRepository(app);
        progressRepo = new ProgressRepository(app);
        settings     = new SettingsStore(app);
    }

    public void loadQuiz(long topicId) {
        this.topicId = topicId;
        this.quizRandomized = false;
        this.correctCount = 0;
        this.currentQuestionIndex.setValue(0);
        this.selectedAnswer.setValue(-1);
        this.answerSubmitted.setValue(false);
        this.score.setValue(0);
        this.quizFinished.setValue(false);

        if (currentRepoSource != null) {
            _quizWithQuestions.removeSource(currentRepoSource);
        }

        currentRepoSource = quizRepo.getQuizWithQuestions(topicId);
        _quizWithQuestions.addSource(currentRepoSource, qwq -> {
            if (qwq != null && qwq.questions != null && !qwq.questions.isEmpty()) {
                quizId = qwq.quiz.id;
                totalQuestions = qwq.questions.size();
                if (!quizRandomized) {
                    quizRandomized = true;
                    QuizWithQuestions randomizedQwq = randomizeQuizQuestions(qwq);
                    _quizWithQuestions.setValue(randomizedQwq);
                }
            }
        });
    }

    private QuizWithQuestions randomizeQuizQuestions(QuizWithQuestions original) {
        QuizWithQuestions result = new QuizWithQuestions();
        result.quiz = original.quiz;
        List<QuizQuestion> randomizedList = new ArrayList<>();

        for (QuizQuestion q : original.questions) {
            QuizQuestion copy = new QuizQuestion(q.quizId, q.questionText, q.optionsJson, q.correctIndex, q.orderIndex);
            copy.id = q.id;

            List<String> options = parseOptions(q.optionsJson);
            if (options.size() > 1) {
                String correctOptionText = (q.correctIndex >= 0 && q.correctIndex < options.size())
                        ? options.get(q.correctIndex)
                        : options.get(0);

                List<String> shuffled = new ArrayList<>(options);
                Collections.shuffle(shuffled);
                copy.correctIndex = shuffled.indexOf(correctOptionText);
                copy.optionsJson = new JSONArray(shuffled).toString();
            }
            randomizedList.add(copy);
        }
        result.questions = randomizedList;
        return result;
    }

    public void submitAnswer(int selectedIndex) {
        QuizWithQuestions qwq = quizWithQuestions.getValue();
        if (qwq == null) return;

        int idx = currentQuestionIndex.getValue() != null ? currentQuestionIndex.getValue() : 0;
        if (idx >= qwq.questions.size()) return;

        QuizQuestion q = qwq.questions.get(idx);
        if (selectedIndex == q.correctIndex) {
            correctCount++;
            score.setValue(correctCount);
        }
        answerSubmitted.setValue(true);
    }

    public void nextQuestion() {
        int idx = currentQuestionIndex.getValue() != null ? currentQuestionIndex.getValue() : 0;
        QuizWithQuestions qwq = quizWithQuestions.getValue();
        if (qwq == null) return;

        if (idx + 1 >= qwq.questions.size()) {
            // Quiz done - save result & update streak
            QuizResult result = new QuizResult(quizId, correctCount, totalQuestions);
            quizRepo.saveResult(result, () -> {
                long studentId = settings.getStudentId();
                progressRepo.recordDailyStudyActivity(studentId, null);
                float overallAvg = quizRepo.getOverallQuizAverage();
                if (overallAvg > 0) {
                    progressRepo.updateQuizAverage(studentId, overallAvg);
                }
                quizFinished.postValue(true);
            });
        } else {
            currentQuestionIndex.setValue(idx + 1);
            selectedAnswer.setValue(-1);
            answerSubmitted.setValue(false);
        }
    }

    /** Parse optionsJson string into a List<String> */
    public static List<String> parseOptions(String optionsJson) {
        List<String> options = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(optionsJson);
            for (int i = 0; i < arr.length(); i++) {
                options.add(arr.getString(i));
            }
        } catch (JSONException e) {
            // Return empty list on parse failure
        }
        return options;
    }

    public int getScore()          { return correctCount; }
    public int getTotalQuestions() { return totalQuestions; }
    public long getTopicId()       { return topicId; }
}
