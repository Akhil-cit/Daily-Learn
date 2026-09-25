package com.example.daily_learn.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.dao.ProgressDao;
import com.example.daily_learn.data.local.entity.Progress;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ProgressRepository {

    private final ProgressDao progressDao;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public ProgressRepository(Application app) {
        progressDao = AppDatabase.getInstance(app).progressDao();
    }

    public LiveData<Progress> getOverallProgress(long studentId) {
        return progressDao.getOverallProgress(studentId);
    }

    public LiveData<List<Progress>> getSubjectProgressList(long studentId) {
        return progressDao.getSubjectProgressList(studentId);
    }

    public void updateOverallTopics(long studentId, int done, int total) {
        executor.execute(() -> progressDao.updateOverallTopics(studentId, done, total));
    }

    public void updateStreak(long studentId, int streak) {
        executor.execute(() -> progressDao.updateStreak(studentId, streak));
    }

    public void updateQuizAverage(long studentId, float avg) {
        executor.execute(() -> progressDao.updateQuizAverage(studentId, avg));
    }

    public void upsertProgress(Progress progress) {
        executor.execute(() -> progressDao.insertProgress(progress));
    }

    /**
     * Record daily study completion and update streak accordingly
     */
    public void recordDailyStudyActivity(long studentId, Runnable onStreakIncremented) {
        executor.execute(() -> {
            Progress overall = progressDao.getOverallProgressSync(studentId);
            long now = System.currentTimeMillis();

            if (overall == null) {
                overall = new Progress(studentId, null, 0);
                overall.streakDays = 1;
                overall.lastUpdated = now;
                progressDao.insertProgress(overall);
                if (onStreakIncremented != null) onStreakIncremented.run();
                return;
            }

            long lastStudyTime = overall.lastUpdated;
            Calendar today = Calendar.getInstance();
            today.setTimeInMillis(now);

            Calendar last = Calendar.getInstance();
            last.setTimeInMillis(lastStudyTime);

            boolean isSameDay = today.get(Calendar.YEAR) == last.get(Calendar.YEAR)
                    && today.get(Calendar.DAY_OF_YEAR) == last.get(Calendar.DAY_OF_YEAR);

            if (!isSameDay) {
                Calendar yesterday = Calendar.getInstance();
                yesterday.add(Calendar.DAY_OF_YEAR, -1);

                boolean isYesterday = yesterday.get(Calendar.YEAR) == last.get(Calendar.YEAR)
                        && yesterday.get(Calendar.DAY_OF_YEAR) == last.get(Calendar.DAY_OF_YEAR);

                if (isYesterday) {
                    overall.streakDays += 1;
                } else {
                    overall.streakDays = 1;
                }
                overall.lastUpdated = now;
                progressDao.updateProgress(overall);

                if (onStreakIncremented != null) onStreakIncremented.run();
            }
        });
    }
}
