package com.example.daily_learn.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.local.entity.Topic;

import java.util.List;

@Dao
public interface SubjectDao {

    // ─── Subject ───────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSubject(Subject subject);

    @Update
    void updateSubject(Subject subject);

    @Delete
    void deleteSubject(Subject subject);

    @Query("SELECT * FROM subjects WHERE student_id = :studentId OR :studentId >= 0 ORDER BY priority DESC")
    LiveData<List<Subject>> getSubjectsByStudent(long studentId);

    @Query("SELECT * FROM subjects WHERE student_id = :studentId OR :studentId >= 0 ORDER BY priority DESC")
    List<Subject> getSubjectsByStudentSync(long studentId);

    @Query("SELECT * FROM subjects WHERE id = :subjectId")
    LiveData<Subject> getSubjectById(long subjectId);

    @Query("SELECT * FROM subjects WHERE id = :subjectId")
    Subject getSubjectByIdSync(long subjectId);

    @Query("UPDATE subjects SET completion_percent = :percent WHERE id = :subjectId")
    void updateCompletionPercent(long subjectId, int percent);

    @Query("DELETE FROM subjects WHERE student_id = :studentId OR :studentId >= 0")
    void deleteAllByStudent(long studentId);

    // ─── Unit ──────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertUnit(Unit unit);

    @Update
    void updateUnit(Unit unit);

    @Delete
    void deleteUnit(Unit unit);

    @Query("SELECT * FROM units WHERE subject_id = :subjectId ORDER BY order_index")
    LiveData<List<Unit>> getUnitsBySubject(long subjectId);

    @Query("SELECT * FROM units WHERE subject_id = :subjectId ORDER BY order_index")
    List<Unit> getUnitsBySubjectSync(long subjectId);

    // ─── Topic ─────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTopic(Topic topic);

    @Update
    void updateTopic(Topic topic);

    @Delete
    void deleteTopic(Topic topic);

    @Query("SELECT * FROM topics WHERE unit_id = :unitId ORDER BY order_index")
    LiveData<List<Topic>> getTopicsByUnit(long unitId);

    @Query("SELECT * FROM topics WHERE unit_id = :unitId ORDER BY order_index")
    List<Topic> getTopicsByUnitSync(long unitId);

    @Query("SELECT * FROM topics WHERE id = :topicId")
    LiveData<Topic> getTopicById(long topicId);

    @Query("SELECT * FROM topics WHERE id = :topicId")
    Topic getTopicByIdSync(long topicId);

    @Query("SELECT * FROM topics WHERE subject_id = :subjectId AND is_completed = 0 ORDER BY order_index")
    List<Topic> getIncompleteTopicsBySubject(long subjectId);

    @Query("SELECT * FROM topics WHERE is_completed = 0 ORDER BY order_index")
    List<Topic> getAllIncompleteTopicsSync();

    @Query("SELECT * FROM topics WHERE subject_id = :subjectId ORDER BY order_index")
    List<Topic> getAllTopicsBySubjectSync(long subjectId);

    @Query("SELECT * FROM topics ORDER BY order_index")
    List<Topic> getAllTopicsSync();

    @Query("SELECT * FROM topics WHERE needs_revision = 1 AND subject_id = :subjectId")
    LiveData<List<Topic>> getWeakTopics(long subjectId);

    @Query("SELECT * FROM topics WHERE needs_revision = 1")
    LiveData<List<Topic>> getAllWeakTopics();

    @Query("UPDATE topics SET is_completed = 1, last_reviewed_date = :timestamp WHERE id = :topicId")
    void markTopicCompleted(long topicId, long timestamp);

    @Query("UPDATE topics SET needs_revision = :needsRevision WHERE id = :topicId")
    void setNeedsRevision(long topicId, boolean needsRevision);

    // ─── Subject with full tree ────────────────────────────────
    @Transaction
    @Query("SELECT * FROM subjects WHERE id = :subjectId")
    LiveData<SubjectWithUnitsAndTopics> getSubjectWithUnitsAndTopics(long subjectId);

    @Transaction
    @Query("SELECT * FROM subjects WHERE student_id = :studentId OR :studentId >= 0 ORDER BY priority DESC")
    LiveData<List<SubjectWithUnitsAndTopics>> getAllSubjectsWithUnitsAndTopics(long studentId);

    @Transaction
    @Query("SELECT * FROM subjects WHERE student_id = :studentId OR :studentId >= 0 ORDER BY priority DESC")
    List<SubjectWithUnitsAndTopics> getAllSubjectsWithUnitsAndTopicsSync(long studentId);
}
