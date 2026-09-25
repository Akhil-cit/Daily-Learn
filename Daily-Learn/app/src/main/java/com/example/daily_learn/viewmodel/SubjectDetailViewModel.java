package com.example.daily_learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.repository.SubjectRepository;

public class SubjectDetailViewModel extends AndroidViewModel {

    private final SubjectRepository subjectRepo;
    public LiveData<SubjectWithUnitsAndTopics> subjectDetail;
    public final MutableLiveData<Long> subjectId = new MutableLiveData<>();

    public SubjectDetailViewModel(@NonNull Application app) {
        super(app);
        subjectRepo = new SubjectRepository(app);
    }

    public void loadSubject(long id) {
        subjectId.setValue(id);
        subjectDetail = subjectRepo.getSubjectWithUnitsAndTopics(id);
    }

    public void markTopicCompleted(long topicId) {
        subjectRepo.markTopicCompleted(topicId);
        // Recompute subject completion percent
        refreshCompletionPercent();
    }

    public void setNeedsRevision(long topicId, boolean needs) {
        subjectRepo.setNeedsRevision(topicId, needs);
    }

    private void refreshCompletionPercent() {
        Long id = subjectId.getValue();
        if (id == null) return;
        // Recalculated reactively via LiveData observation in the Fragment
    }
}
