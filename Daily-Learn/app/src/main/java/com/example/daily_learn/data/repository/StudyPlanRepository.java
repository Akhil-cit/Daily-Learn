package com.example.daily_learn.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.dao.StudyPlanDao;
import com.example.daily_learn.data.local.entity.StudyPlan;
import com.example.daily_learn.data.local.entity.StudySession;
import com.example.daily_learn.data.local.entity.StudySessionWithTopic;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.domain.scheduler.AdaptiveRescheduler;
import com.example.daily_learn.domain.scheduler.StudyPlanGenerator;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class StudyPlanRepository {

    private final StudyPlanDao dao;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final AdaptiveRescheduler rescheduler = new AdaptiveRescheduler();

    public StudyPlanRepository(Application app) {
        dao = AppDatabase.getInstance(app).studyPlanDao();
    }

    // ── Study Plan ─────────────────────────────────────────────────────────
    public void insertPlan(StudyPlan plan, InsertCallback cb) {
        executor.execute(() -> {
            long id = dao.insertStudyPlan(plan);
            if (cb != null) cb.onInserted(id);
        });
    }

    public LiveData<StudyPlan> getLatestPlan(long studentId) {
        return dao.getLatestPlan(studentId);
    }

    /** Sync — background thread only */
    public StudyPlan getLatestPlanSync(long studentId) {
        return dao.getLatestPlanSync(studentId);
    }

    // ── Sessions ────────────────────────────────────────────────────────────
    public void insertSessions(List<StudySession> sessions) {
        executor.execute(() -> dao.insertSessions(sessions));
    }

    public LiveData<List<StudySession>> getSessionsByPlan(long planId) {
        return dao.getSessionsByPlan(planId);
    }

    public LiveData<List<StudySessionWithTopic>> getSessionsWithTopics(long planId) {
        return dao.getSessionsWithTopics(planId);
    }

    public LiveData<List<StudySessionWithTopic>> getTodaysSessionsWithTopics(
            long planId, long dayStart, long dayEnd) {
        return dao.getTodaysSessionsWithTopics(planId, dayStart, dayEnd);
    }

    public void markSessionDone(long sessionId) {
        executor.execute(() ->
            dao.markSessionDone(sessionId, StudySession.STATUS_DONE, System.currentTimeMillis()));
    }

    public void updateSessionStatus(long sessionId, String status) {
        executor.execute(() -> dao.updateSessionStatus(sessionId, status));
    }

    /**
     * Run the adaptive rescheduler on background thread:
     *   1. Detect overdue SCHEDULED sessions → mark MISSED
     *   2. Push remaining sessions forward respecting daily budget
     *   3. Persist changes
     */
    public void runAdaptiveReschedule(long planId, int dailyMinutes,
                                       List<Topic> topics, Runnable onComplete) {
        executor.execute(() -> {
            long todayStart = StudyPlanGenerator.todayMidnightMillis();
            List<StudySession> allSessions = dao.getSessionsByPlanSync(planId);

            AdaptiveRescheduler.RescheduleResult result =
                rescheduler.reschedule(allSessions, todayStart, dailyMinutes, topics);

            for (StudySession s : result.updatedSessions) {
                dao.updateSession(s);
            }

            if (onComplete != null) onComplete.run();
        });
    }

    /**
     * Regenerate the learning sessions from scratch (e.g., after adding new subjects).
     * Deletes existing SCHEDULED sessions and re-generates via StudyPlanGenerator.
     */
    public void regeneratePlan(long planId, List<Topic> incompleteTopics,
                                long examDateMillis, int dailyMinutes, Runnable onComplete) {
        executor.execute(() -> {
            dao.deleteScheduledSessions(planId);

            StudyPlanGenerator generator = new StudyPlanGenerator();
            List<StudySession> sessions =
                generator.generate(planId, incompleteTopics, examDateMillis, dailyMinutes);

            dao.insertSessions(sessions);
            if (onComplete != null) onComplete.run();
        });
    }

    public interface InsertCallback {
        void onInserted(long id);
    }
}
