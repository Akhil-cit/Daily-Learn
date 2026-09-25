package com.example.daily_learn.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.dao.ExamDao;
import com.example.daily_learn.data.local.entity.Exam;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ExamRepository {

    private final ExamDao examDao;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public ExamRepository(Application app) {
        examDao = AppDatabase.getInstance(app).examDao();
    }

    public void insertExam(Exam exam) {
        executor.execute(() -> examDao.insertExam(exam));
    }

    public void deleteExam(Exam exam) {
        executor.execute(() -> examDao.deleteExam(exam));
    }

    public LiveData<Exam> getNextExam(long todayMillis) {
        return examDao.getNextExam(todayMillis);
    }

    public LiveData<List<Exam>> getAllExams() {
        return examDao.getAllExams();
    }

    public LiveData<List<Exam>> getExamsForSubject(long subjectId) {
        return examDao.getExamsForSubject(subjectId);
    }
}
