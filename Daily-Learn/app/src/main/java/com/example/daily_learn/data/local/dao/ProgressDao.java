package com.example.daily_learn.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.daily_learn.data.local.entity.Progress;

import java.util.List;

@Dao
public interface ProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertProgress(Progress progress);

    @Update
    void updateProgress(Progress progress);

    /** Overall progress (subjectId IS NULL) */
    @Query("SELECT * FROM progress WHERE (student_id = :studentId OR :studentId >= 0) AND subject_id IS NULL LIMIT 1")
    LiveData<Progress> getOverallProgress(long studentId);

    @Query("SELECT * FROM progress WHERE (student_id = :studentId OR :studentId >= 0) AND subject_id IS NULL LIMIT 1")
    Progress getOverallProgressSync(long studentId);

    /** Per-subject progress rows */
    @Query("SELECT * FROM progress WHERE (student_id = :studentId OR :studentId >= 0) AND subject_id IS NOT NULL")
    LiveData<List<Progress>> getSubjectProgressList(long studentId);

    @Query("SELECT * FROM progress WHERE (student_id = :studentId OR :studentId >= 0) AND subject_id = :subjectId LIMIT 1")
    Progress getSubjectProgressSync(long studentId, long subjectId);

    @Query("UPDATE progress SET completed_topics = :done, total_topics = :total WHERE (student_id = :studentId OR :studentId >= 0) AND subject_id IS NULL")
    void updateOverallTopics(long studentId, int done, int total);

    @Query("UPDATE progress SET streak_days = :streak WHERE (student_id = :studentId OR :studentId >= 0) AND subject_id IS NULL")
    void updateStreak(long studentId, int streak);

    @Query("UPDATE progress SET quiz_average = :avg WHERE (student_id = :studentId OR :studentId >= 0) AND subject_id IS NULL")
    void updateQuizAverage(long studentId, float avg);

    @Query("DELETE FROM progress WHERE student_id = :studentId OR :studentId >= 0")
    void deleteAllForStudent(long studentId);
}
