package com.example.daily_learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.daily_learn.data.local.entity.StudyPlan;
import com.example.daily_learn.data.local.entity.StudySessionWithTopic;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.data.repository.StudyPlanRepository;

import java.util.List;

public class PlanViewModel extends AndroidViewModel {

    private final StudyPlanRepository planRepo;
    private final SettingsStore settings;

    public final LiveData<StudyPlan> latestPlan;
    public final LiveData<List<StudySessionWithTopic>> allSessions;

    public PlanViewModel(@NonNull Application app) {
        super(app);
        planRepo = new StudyPlanRepository(app);
        settings = new SettingsStore(app);

        long studentId = settings.getStudentId();
        latestPlan = planRepo.getLatestPlan(studentId);

        allSessions = Transformations.switchMap(latestPlan, plan -> {
            if (plan == null) return new MutableLiveData<>(null);
            return planRepo.getSessionsWithTopics(plan.id);
        });
    }
}
