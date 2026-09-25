package com.example.daily_learn.viewmodel;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.entity.StudyPlan;
import com.example.daily_learn.data.local.entity.StudySession;
import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.repository.SyllabusAnalysisRepository;
import com.example.daily_learn.domain.ai.DailyTopicGroup;
import com.example.daily_learn.domain.ai.SyllabusAnalysisResult;
import com.example.daily_learn.domain.ai.SyllabusAnalyzer;
import com.example.daily_learn.domain.scheduler.StudyPlanGenerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SyllabusAnalysisViewModel extends AndroidViewModel {

    private final SyllabusAnalysisRepository repository;

    private final MutableLiveData<Uri> selectedUri = new MutableLiveData<>(null);
    private final MutableLiveData<String> selectedFileName = new MutableLiveData<>(null);
    private final MutableLiveData<String> rawTextContent = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> targetDays = new MutableLiveData<>(20);

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> progressMessage = new MutableLiveData<>("");
    private final MutableLiveData<Integer> progressPercent = new MutableLiveData<>(0);

    private final MutableLiveData<SyllabusAnalysisResult> analysisResult = new MutableLiveData<>(null);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    public SyllabusAnalysisViewModel(@NonNull Application application) {
        super(application);
        this.repository = new SyllabusAnalysisRepository(application);
    }

    public LiveData<Uri> getSelectedUri() { return selectedUri; }
    public LiveData<String> getSelectedFileName() { return selectedFileName; }
    public LiveData<Integer> getTargetDays() { return targetDays; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getProgressMessage() { return progressMessage; }
    public LiveData<Integer> getProgressPercent() { return progressPercent; }
    public LiveData<SyllabusAnalysisResult> getAnalysisResult() { return analysisResult; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void setFile(Uri uri, String fileName) {
        this.selectedUri.setValue(uri);
        this.selectedFileName.setValue(fileName);
        this.rawTextContent.setValue(null);
        this.errorMessage.setValue(null);
    }

    public void setTextContent(String text, String label) {
        this.rawTextContent.setValue(text);
        this.selectedFileName.setValue(label);
        this.selectedUri.setValue(null);
        this.errorMessage.setValue(null);
    }

    public void setTargetDays(int days) {
        if (days > 0) {
            this.targetDays.setValue(days);
        }
    }

    public void analyze() {
        int days = targetDays.getValue() != null && targetDays.getValue() > 0 ? targetDays.getValue() : 20;

        isLoading.postValue(true);
        progressMessage.postValue("Initializing AI analyzer...");
        progressPercent.postValue(5);
        errorMessage.postValue(null);

        SyllabusAnalyzer.AnalysisCallback callback = new SyllabusAnalyzer.AnalysisCallback() {
            @Override
            public void onProgress(String statusMessage, int percent) {
                progressMessage.postValue(statusMessage);
                progressPercent.postValue(percent);
            }

            @Override
            public void onSuccess(SyllabusAnalysisResult result) {
                isLoading.postValue(false);
                progressPercent.postValue(100);
                analysisResult.postValue(result);
            }

            @Override
            public void onFailure(Exception e) {
                isLoading.postValue(false);
                errorMessage.postValue(e != null && e.getMessage() != null ? e.getMessage() : "Analysis failed. Please check file format.");
            }
        };

        if (rawTextContent.getValue() != null && !rawTextContent.getValue().trim().isEmpty()) {
            repository.analyzeText(rawTextContent.getValue(), days, callback);
        } else if (selectedUri.getValue() != null) {
            repository.analyzeDocument(selectedUri.getValue(), days, callback);
        } else {
            isLoading.postValue(false);
            errorMessage.postValue("Please select a syllabus PDF, Image, or text first.");
        }
    }

    public void savePlanAndStart(SyllabusAnalysisResult result, OnPlanSavedListener listener) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplication());
            long studentId = 1L;
            try {
                com.example.daily_learn.data.local.entity.Student s = db.studentDao().getStudentSync();
                if (s != null) studentId = s.id;
            } catch (Exception ignored) {}

            long examDate = System.currentTimeMillis() + ((long) result.totalDays * 86400000L);
            String courseTitle = result.courseName != null && !result.courseName.isEmpty() 
                    ? result.courseName : "Syllabus Subject";
            Subject subject = new Subject(studentId, courseTitle, 2, 2, examDate);
            long subjectId = db.subjectDao().insertSubject(subject);

            // Create or get StudyPlan
            StudyPlan plan = db.studyPlanDao().getLatestPlanSync(studentId);
            long planId;
            if (plan != null) {
                planId = plan.id;
            } else {
                plan = new StudyPlan(studentId, examDate);
                planId = db.studyPlanDao().insertStudyPlan(plan);
            }

            long todayStart = StudyPlanGenerator.todayMidnightMillis();
            long firstTopicId = -1L;
            long firstSessionId = -1L;

            Map<String, Long> unitMap = new LinkedHashMap<>();
            int unitOrder = 0;
            int topicOrder = 0;

            for (int dayIdx = 0; dayIdx < result.days.size(); dayIdx++) {
                DailyTopicGroup day = result.days.get(dayIdx);
                String unitName = (day.sectionName != null && !day.sectionName.isEmpty())
                        ? day.sectionName : "Unit 1";

                Long unitId = unitMap.get(unitName);
                if (unitId == null) {
                    Unit unit = new Unit(subjectId, unitName, unitOrder++);
                    unitId = db.subjectDao().insertUnit(unit);
                    unitMap.put(unitName, unitId);
                }

                String topicName = day.title;
                if (topicName == null || topicName.trim().isEmpty()) {
                    topicName = "Topic " + (dayIdx + 1);
                }

                Topic topic = new Topic(unitId, subjectId, topicName, 2, 45, topicOrder++);
                long topicId = db.subjectDao().insertTopic(topic);

                if (firstTopicId == -1L) firstTopicId = topicId;

                long scheduledDate = todayStart + ((long) dayIdx * 86400000L);
                StudySession session = new StudySession(planId, topicId, scheduledDate, false);
                long sessId = db.studyPlanDao().insertSession(session);

                if (firstSessionId == -1L) firstSessionId = sessId;
            }

            final long finalFirstTopicId = firstTopicId;
            final long finalFirstSessionId = firstSessionId;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (listener != null) {
                    listener.onSaved(subjectId, finalFirstTopicId, finalFirstSessionId);
                }
            });
        }).start();
    }

    public interface OnPlanSavedListener {
        void onSaved(long subjectId, long firstTopicId, long firstSessionId);
    }
}
