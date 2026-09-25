package com.example.daily_learn.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.daily_learn.data.local.entity.Exam;

import java.util.List;

@Dao
public interface ExamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertExam(Exam exam);

    @Delete
    void deleteExam(Exam exam);

    @Query("SELECT * FROM exams WHERE subject_id = :subjectId ORDER BY date")
    LiveData<List<Exam>> getExamsForSubject(long subjectId);

    @Query("SELECT * FROM exams ORDER BY date")
    LiveData<List<Exam>> getAllExams();

    /** Nearest upcoming exam from today */
    @Query("SELECT * FROM exams WHERE date >= :todayMillis ORDER BY date LIMIT 1")
    LiveData<Exam> getNextExam(long todayMillis);

    @Query("SELECT * FROM exams WHERE date >= :todayMillis ORDER BY date LIMIT 1")
    Exam getNextExamSync(long todayMillis);

    @Query("DELETE FROM exams WHERE subject_id = :subjectId")
    void deleteExamsForSubject(long subjectId);
}
