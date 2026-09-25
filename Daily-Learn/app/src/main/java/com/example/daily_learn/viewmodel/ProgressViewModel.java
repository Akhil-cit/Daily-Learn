package com.example.daily_learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.daily_learn.data.local.entity.Progress;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.data.repository.ExamRepository;
import com.example.daily_learn.data.local.entity.Exam;
import com.example.daily_learn.data.repository.ProgressRepository;

import java.util.List;

public class ProgressViewModel extends AndroidViewModel {

    private final ProgressRepository progressRepo;
    private final ExamRepository examRepo;
    private final SettingsStore settings;

    public final LiveData<Progress> overallProgress;
    public final LiveData<List<Progress>> subjectProgressList;
    public final LiveData<Exam> nextExam;

    public ProgressViewModel(@NonNull Application app) {
        super(app);
        progressRepo = new ProgressRepository(app);
        examRepo     = new ExamRepository(app);
        settings     = new SettingsStore(app);

        long studentId = settings.getStudentId();
        overallProgress    = progressRepo.getOverallProgress(studentId);
        subjectProgressList = progressRepo.getSubjectProgressList(studentId);
        nextExam           = examRepo.getNextExam(System.currentTimeMillis());
    }

    /**
     * Derive exam readiness label from progress vs days remaining.
     * Returns: "EXCELLENT" | "GOOD" | "FAIR" | "AT_RISK"
     */
    public String getReadinessLabel(int completedTopics, int totalTopics, int daysLeft) {
        if (totalTopics == 0 || daysLeft <= 0) return "FAIR";
        double progressPct = (double) completedTopics / totalTopics;
        double timePct     = 1.0 - ((double) daysLeft / 30.0); // normalized to 30-day window

        double gap = progressPct - timePct;
        if (gap >= 0.10)        return "EXCELLENT";
        if (gap >= -0.05)       return "GOOD";
        if (gap >= -0.20)       return "FAIR";
        return "AT_RISK";
    }
}
