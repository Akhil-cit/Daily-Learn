package com.example.daily_learn.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.daily_learn.data.local.entity.Quiz;
import com.example.daily_learn.data.local.entity.QuizQuestion;
import com.example.daily_learn.data.local.entity.QuizResult;
import com.example.daily_learn.data.local.entity.QuizWithQuestions;

import java.util.List;

@Dao
public interface QuizDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertQuiz(Quiz quiz);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertQuestions(List<QuizQuestion> questions);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertResult(QuizResult result);

    @Query("SELECT * FROM quizzes WHERE topic_id = :topicId LIMIT 1")
    Quiz getQuizForTopicSync(long topicId);

    @Query("SELECT * FROM quizzes WHERE topic_id = :topicId LIMIT 1")
    LiveData<Quiz> getQuizForTopic(long topicId);

    @Transaction
    @Query("SELECT * FROM quizzes WHERE topic_id = :topicId LIMIT 1")
    LiveData<QuizWithQuestions> getQuizWithQuestionsForTopic(long topicId);

    @Transaction
    @Query("SELECT * FROM quizzes WHERE topic_id = :topicId LIMIT 1")
    QuizWithQuestions getQuizWithQuestionsForTopicSync(long topicId);

    @Query("SELECT * FROM quiz_results WHERE quiz_id = :quizId ORDER BY date_taken DESC LIMIT 1")
    LiveData<QuizResult> getLatestResult(long quizId);

    @Query("SELECT * FROM quiz_results WHERE quiz_id = :quizId ORDER BY date_taken DESC")
    List<QuizResult> getResultsForQuizSync(long quizId);

    /** Average score across ALL quiz results for a student (via quiz_id JOIN) */
    @Query("SELECT AVG(CAST(score AS FLOAT) / total * 100) FROM quiz_results")
    float getOverallQuizAverage();
}
