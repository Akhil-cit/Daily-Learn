package com.example.daily_learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.data.repository.SubjectRepository;

import java.util.List;

public class SubjectsViewModel extends AndroidViewModel {

    private final SubjectRepository subjectRepo;
    private final SettingsStore settings;

    public final LiveData<List<SubjectWithUnitsAndTopics>> subjects;

    public SubjectsViewModel(@NonNull Application app) {
        super(app);
        subjectRepo = new SubjectRepository(app);
        settings    = new SettingsStore(app);

        long studentId = settings.getStudentId();
        subjects = subjectRepo.getAllSubjectsWithUnitsAndTopics(studentId);
    }
}
