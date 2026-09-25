package com.example.daily_learn.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.daily_learn.data.local.entity.StudyPlan;
import com.example.daily_learn.data.local.entity.StudySession;
import com.example.daily_learn.data.local.entity.StudySessionWithTopic;

import java.util.List;

@Dao
public interface StudyPlanDao {

    // ─── StudyPlan ─────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertStudyPlan(StudyPlan plan);

    @Query("SELECT * FROM study_plans WHERE student_id = :studentId OR :studentId >= 0 ORDER BY generated_date DESC LIMIT 1")
    LiveData<StudyPlan> getLatestPlan(long studentId);

    @Query("SELECT * FROM study_plans WHERE student_id = :studentId OR :studentId >= 0 ORDER BY generated_date DESC LIMIT 1")
    StudyPlan getLatestPlanSync(long studentId);

    @Query("DELETE FROM study_plans WHERE student_id = :studentId OR :studentId >= 0")
    void deleteAllPlansForStudent(long studentId);

    // ─── StudySession ──────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSession(StudySession session);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSessions(List<StudySession> sessions);

    @Update
    void updateSession(StudySession session);

    @Query("SELECT * FROM study_sessions WHERE study_plan_id = :planId ORDER BY scheduled_date")
    LiveData<List<StudySession>> getSessionsByPlan(long planId);

    @Query("SELECT * FROM study_sessions WHERE study_plan_id = :planId ORDER BY scheduled_date")
    List<StudySession> getSessionsByPlanSync(long planId);

    /** Sessions for a specific calendar day (between day start and day end epoch ms) */
    @Query("SELECT * FROM study_sessions WHERE (study_plan_id = :planId OR :planId <= 0) " +
           "AND scheduled_date >= :dayStart AND scheduled_date < :dayEnd " +
           "ORDER BY scheduled_date")
    List<StudySession> getSessionsForDay(long planId, long dayStart, long dayEnd);

    /** Overdue sessions still marked SCHEDULED */
    @Query("SELECT * FROM study_sessions WHERE (study_plan_id = :planId OR :planId <= 0) " +
           "AND scheduled_date < :todayStart AND status = 'SCHEDULED'")
    List<StudySession> getOverdueSessions(long planId, long todayStart);

    /** All future SCHEDULED sessions in date order */
    @Query("SELECT * FROM study_sessions WHERE (study_plan_id = :planId OR :planId <= 0) " +
           "AND scheduled_date >= :todayStart AND status = 'SCHEDULED' " +
           "ORDER BY scheduled_date")
    List<StudySession> getFutureScheduledSessions(long planId, long todayStart);

    @Query("UPDATE study_sessions SET status = :status WHERE id = :sessionId")
    void updateSessionStatus(long sessionId, String status);

    @Query("UPDATE study_sessions SET status = :status, actual_date = :actualDate WHERE id = :sessionId")
    void markSessionDone(long sessionId, String status, long actualDate);

    @Query("UPDATE study_sessions SET scheduled_date = :newDate, status = 'RESCHEDULED' WHERE id = :sessionId")
    void rescheduleSession(long sessionId, long newDate);

    @Query("DELETE FROM study_sessions WHERE (study_plan_id = :planId OR :planId <= 0) AND status = 'SCHEDULED'")
    void deleteScheduledSessions(long planId);

    // ─── With topic join ───────────────────────────────────────
    @Transaction
    @Query("SELECT * FROM study_sessions WHERE study_plan_id = :planId OR :planId <= 0 ORDER BY scheduled_date")
    LiveData<List<StudySessionWithTopic>> getSessionsWithTopics(long planId);

    @Transaction
    @Query("SELECT * FROM study_sessions WHERE scheduled_date >= :dayStart " +
           "AND scheduled_date < :dayEnd AND (study_plan_id = :planId OR :planId <= 0) ORDER BY scheduled_date")
    LiveData<List<StudySessionWithTopic>> getTodaysSessionsWithTopics(long planId, long dayStart, long dayEnd);
}
