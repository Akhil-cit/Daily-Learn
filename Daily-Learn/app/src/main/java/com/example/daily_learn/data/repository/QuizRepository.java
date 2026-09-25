package com.example.daily_learn.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.dao.QuizDao;
import com.example.daily_learn.data.local.dao.SubjectDao;
import com.example.daily_learn.data.local.entity.Quiz;
import com.example.daily_learn.data.local.entity.QuizQuestion;
import com.example.daily_learn.data.local.entity.QuizResult;
import com.example.daily_learn.data.local.entity.QuizWithQuestions;
import com.example.daily_learn.data.local.entity.Topic;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class QuizRepository {

    private final QuizDao quizDao;
    private final SubjectDao subjectDao;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public QuizRepository(Application app) {
        AppDatabase db = AppDatabase.getInstance(app);
        quizDao    = db.quizDao();
        subjectDao = db.subjectDao();
    }

    public LiveData<QuizWithQuestions> getQuizWithQuestions(long topicId) {
        executor.execute(() -> ensureQuizExists(topicId));
        return quizDao.getQuizWithQuestionsForTopic(topicId);
    }

    private QuizQuestion createRandomizedQuestion(long quizId, String questionText,
                                                  List<String> rawOptions, int orderIndex) {
        String correctOption = rawOptions.get(0);
        List<String> shuffled = new ArrayList<>(rawOptions);
        Collections.shuffle(shuffled);
        int correctIndex = shuffled.indexOf(correctOption);
        return new QuizQuestion(quizId, questionText, new JSONArray(shuffled).toString(),
                correctIndex, orderIndex);
    }

    private void ensureQuizExists(long topicId) {
        Quiz existing = quizDao.getQuizForTopicSync(topicId);
        if (existing == null) {
            Topic topic = subjectDao.getTopicByIdSync(topicId);
            String topicName = topic != null ? topic.name : "Concept";

            Quiz quiz = new Quiz(topicId);
            long quizId = quizDao.insertQuiz(quiz);

            List<QuizQuestion> questions = new ArrayList<>();
            // Q1: Definition
            questions.add(createRandomizedQuestion(quizId,
                    "What is the primary definition or purpose of " + topicName + "?",
                    Arrays.asList("Core fundamental principle and structured practical implementation",
                            "A hardware bus clock multiplier",
                            "A legacy database indexing query syntax",
                            "A physical network cable standard"),
                    0));

            // Q2: Complexity
            questions.add(createRandomizedQuestion(quizId,
                    "What is the typical time complexity expectation for standard operations in " + topicName + "?",
                    Arrays.asList("Depends on implementation: usually O(1) or O(log n)",
                            "Always strictly O(n!)",
                            "Always O(n³)",
                            "Always negative constant"),
                    1));

            // Q3: Application
            questions.add(createRandomizedQuestion(quizId,
                    "Which problem is best addressed using " + topicName + "?",
                    Arrays.asList("Optimizing data processing, memory locality, or execution speed",
                            "Configuring wireless router antenna gain",
                            "Rendering audio waveform filters only",
                            "Compiling raw machine assembly code"),
                    2));

            // Q4: Key Tradeoff
            questions.add(createRandomizedQuestion(quizId,
                    "What is a primary engineering trade-off involved with " + topicName + "?",
                    Arrays.asList("Time efficiency vs memory/space overhead",
                            "Screen refresh rate vs resolution",
                            "Network packet MTU vs protocol header size",
                            "Color depth vs brightness"),
                    3));

            // Q5: Prerequisite
            questions.add(createRandomizedQuestion(quizId,
                    "Which foundational knowledge is most essential before implementing " + topicName + "?",
                    Arrays.asList("Fundamental data structures and asymptotic notation",
                            "Advanced quantum gate logic",
                            "Microcontroller interrupt vectors",
                            "3D graphics shader pipelines"),
                    4));

            quizDao.insertQuestions(questions);
        }
    }

    public void saveResult(QuizResult result, Runnable onDone) {
        executor.execute(() -> {
            quizDao.insertResult(result);
            if (onDone != null) onDone.run();
        });
    }

    public LiveData<QuizResult> getLatestResult(long quizId) {
        return quizDao.getLatestResult(quizId);
    }

    public float getOverallQuizAverage() {
        return quizDao.getOverallQuizAverage();
    }

    public void createQuiz(long topicId, List<QuizQuestion> questions, Runnable onDone) {
        executor.execute(() -> {
            Quiz quiz = new Quiz(topicId);
            long quizId = quizDao.insertQuiz(quiz);
            for (QuizQuestion q : questions) {
                q.quizId = quizId;
            }
            quizDao.insertQuestions(questions);
            if (onDone != null) onDone.run();
        });
    }
}
