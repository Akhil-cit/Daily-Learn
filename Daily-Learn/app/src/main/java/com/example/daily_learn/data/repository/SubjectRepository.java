package com.example.daily_learn.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.dao.SubjectDao;
import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.local.entity.UnitWithTopics;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SubjectRepository {

    private final SubjectDao subjectDao;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public SubjectRepository(Application app) {
        subjectDao = AppDatabase.getInstance(app).subjectDao();
    }

    // ── Subject ─────────────────────────────────────────────────────────────
    public void insertSubject(Subject subject, InsertCallback cb) {
        executor.execute(() -> {
            long id = subjectDao.insertSubject(subject);
            if (cb != null) cb.onInserted(id);
        });
    }

    public void updateSubject(Subject subject) {
        executor.execute(() -> subjectDao.updateSubject(subject));
    }

    public void deleteSubject(Subject subject) {
        executor.execute(() -> subjectDao.deleteSubject(subject));
    }

    public LiveData<List<Subject>> getSubjectsByStudent(long studentId) {
        return subjectDao.getSubjectsByStudent(studentId);
    }

    public LiveData<Subject> getSubjectById(long subjectId) {
        return subjectDao.getSubjectById(subjectId);
    }

    public Subject getSubjectByIdSync(long subjectId) {
        return subjectDao.getSubjectByIdSync(subjectId);
    }

    public void updateCompletionPercent(long subjectId, int percent) {
        executor.execute(() -> subjectDao.updateCompletionPercent(subjectId, percent));
    }

    // ── Unit ─────────────────────────────────────────────────────────────────
    public void insertUnit(Unit unit, InsertCallback cb) {
        executor.execute(() -> {
            long id = subjectDao.insertUnit(unit);
            if (cb != null) cb.onInserted(id);
        });
    }

    public void updateUnit(Unit unit) {
        executor.execute(() -> subjectDao.updateUnit(unit));
    }

    public void deleteUnit(Unit unit) {
        executor.execute(() -> subjectDao.deleteUnit(unit));
    }

    public LiveData<List<Unit>> getUnitsBySubject(long subjectId) {
        return subjectDao.getUnitsBySubject(subjectId);
    }

    // ── Topic ────────────────────────────────────────────────────────────────
    public void insertTopic(Topic topic, InsertCallback cb) {
        executor.execute(() -> {
            long id = subjectDao.insertTopic(topic);
            if (cb != null) cb.onInserted(id);
        });
    }

    public void updateTopic(Topic topic) {
        executor.execute(() -> subjectDao.updateTopic(topic));
    }

    public void deleteTopic(Topic topic) {
        executor.execute(() -> subjectDao.deleteTopic(topic));
    }

    public LiveData<Topic> getTopicById(long topicId) {
        return subjectDao.getTopicById(topicId);
    }

    public LiveData<List<Topic>> getTopicsByUnit(long unitId) {
        return subjectDao.getTopicsByUnit(unitId);
    }

    public void markTopicCompleted(long topicId) {
        executor.execute(() -> subjectDao.markTopicCompleted(topicId, System.currentTimeMillis()));
    }

    public void setNeedsRevision(long topicId, boolean needs) {
        executor.execute(() -> subjectDao.setNeedsRevision(topicId, needs));
    }

    public LiveData<List<Topic>> getAllWeakTopics() {
        return subjectDao.getAllWeakTopics();
    }

    // ── Relation ─────────────────────────────────────────────────────────────
    public LiveData<SubjectWithUnitsAndTopics> getSubjectWithUnitsAndTopics(long subjectId) {
        return subjectDao.getSubjectWithUnitsAndTopics(subjectId);
    }

    public LiveData<List<SubjectWithUnitsAndTopics>> getAllSubjectsWithUnitsAndTopics(long studentId) {
        return subjectDao.getAllSubjectsWithUnitsAndTopics(studentId);
    }

    /** Sync — call from background thread only */
    public List<SubjectWithUnitsAndTopics> getAllSubjectsWithUnitsAndTopicsSync(long studentId) {
        return subjectDao.getAllSubjectsWithUnitsAndTopicsSync(studentId);
    }

    public interface InsertCallback {
        void onInserted(long id);
    }
}
