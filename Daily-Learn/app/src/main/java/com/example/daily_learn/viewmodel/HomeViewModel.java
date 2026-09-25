package com.example.daily_learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.entity.Exam;
import com.example.daily_learn.data.local.entity.Progress;
import com.example.daily_learn.data.local.entity.Student;
import com.example.daily_learn.data.local.entity.StudyPlan;
import com.example.daily_learn.data.local.entity.StudySessionWithTopic;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.data.repository.ExamRepository;
import com.example.daily_learn.data.repository.ProgressRepository;
import com.example.daily_learn.data.repository.StudyPlanRepository;
import com.example.daily_learn.data.repository.SubjectRepository;

import java.util.Calendar;
import java.util.List;

public class HomeViewModel extends AndroidViewModel {

    private final StudyPlanRepository planRepo;
    private final SubjectRepository subjectRepo;
    private final ProgressRepository progressRepo;
    private final ExamRepository examRepo;
    private final SettingsStore settings;

    public final LiveData<Student> student;
    public LiveData<StudyPlan> latestPlan;
    public LiveData<List<StudySessionWithTopic>> todaysSessions;
    public LiveData<Progress> overallProgress;
    public LiveData<Exam> nextExam;
    public LiveData<List<Topic>> weakTopics;

    /** "ON_TRACK" | "BEHIND" | "AHEAD" */
    public final MutableLiveData<String> scheduleStatus = new MutableLiveData<>("ON_TRACK");

    public HomeViewModel(@NonNull Application app) {
        super(app);
        planRepo     = new StudyPlanRepository(app);
        subjectRepo  = new SubjectRepository(app);
        progressRepo = new ProgressRepository(app);
        examRepo     = new ExamRepository(app);
        settings     = new SettingsStore(app);

        long studentId = settings.getStudentId();

        // Load current active student by ID
        student = studentId > 0
            ? AppDatabase.getInstance(app).studentDao().getStudentById(studentId)
            : AppDatabase.getInstance(app).studentDao().getStudent();

        // Observe overall progress for active student
        overallProgress = progressRepo.getOverallProgress(studentId);

        // Observe next exam
        long todayMillis = System.currentTimeMillis();
        nextExam = examRepo.getNextExam(todayMillis);

        // Observe weak topics
        weakTopics = subjectRepo.getAllWeakTopics();

        // Observe plan and today's sessions for active student
        latestPlan = planRepo.getLatestPlan(studentId);
        todaysSessions = Transformations.switchMap(latestPlan, plan -> {
            if (plan == null) return new MutableLiveData<>(null);
            long[] dayBounds = todayBounds();
            return planRepo.getTodaysSessionsWithTopics(plan.id, dayBounds[0], dayBounds[1]);
        });
    }

    /** Run missed-day detection and reschedule on the latest plan */
    public void runAdaptiveReschedule(List<Topic> allTopics) {
        StudyPlan plan = latestPlan.getValue();
        if (plan == null) return;
        int dailyMinutes = settings.getDailyStudyMinutes();
        planRepo.runAdaptiveReschedule(plan.id, dailyMinutes, allTopics, () -> {
            // Recalculate schedule status after reschedule
            computeScheduleStatus();
        });
    }

    public void computeScheduleStatus() {
        Progress p = overallProgress.getValue();
        Exam exam  = nextExam.getValue();
        if (p == null || exam == null || p.totalTopics == 0) return;

        long todayMs = System.currentTimeMillis();
        long examMs  = exam.date;
        long totalDaysMs = examMs - todayMs;
        if (totalDaysMs <= 0) return;

        int totalDays = (int) (totalDaysMs / 86_400_000L);
        double expectedPercent = 1.0 - ((double) totalDays / 30.0);
        double actualPercent   = (double) p.completedTopics / p.totalTopics;

        if (actualPercent >= expectedPercent + 0.05) {
            scheduleStatus.postValue("AHEAD");
        } else if (actualPercent < expectedPercent - 0.05) {
            scheduleStatus.postValue("BEHIND");
        } else {
            scheduleStatus.postValue("ON_TRACK");
        }
    }

    private long[] todayBounds() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        long end   = start + 86_400_000L;
        return new long[]{start, end};
    }
}
