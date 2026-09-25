package com.example.daily_learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.data.repository.SubjectRepository;
import com.example.daily_learn.domain.parser.SyllabusTextStructurer;

import java.util.List;

public class ManualEditorViewModel extends AndroidViewModel {

    private final SubjectRepository subjectRepo;
    private final SettingsStore settings;

    public final MutableLiveData<Long> editingSubjectId = new MutableLiveData<>(-1L);
    public final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>(false);
    public final MutableLiveData<String> error = new MutableLiveData<>();

    public ManualEditorViewModel(@NonNull Application app) {
        super(app);
        subjectRepo = new SubjectRepository(app);
        settings    = new SettingsStore(app);
    }

    public void addSubject(String name, int priority, int difficulty, long examDate) {
        long studentId = settings.getStudentId();
        Subject subject = new Subject(studentId, name, priority, difficulty, examDate);
        subjectRepo.insertSubject(subject, id -> editingSubjectId.postValue(id));
    }

    public void addUnit(long subjectId, String name, int orderIndex) {
        Unit unit = new Unit(subjectId, name, orderIndex);
        subjectRepo.insertUnit(unit, null);
    }

    public void addTopic(long unitId, long subjectId, String name,
                          int difficulty, int estimatedMinutes, int orderIndex) {
        Topic topic = new Topic(unitId, subjectId, name, difficulty, estimatedMinutes, orderIndex);
        subjectRepo.insertTopic(topic, null);
    }

    public void deleteTopic(Topic topic)   { subjectRepo.deleteTopic(topic); }
    public void deleteUnit(Unit unit)      { subjectRepo.deleteUnit(unit); }
    public void deleteSubject(Subject s)   { subjectRepo.deleteSubject(s); }
    public void updateTopic(Topic topic)   { subjectRepo.updateTopic(topic); }

    /** Bulk import parsed subjects from SyllabusTextStructurer */
    public void importParsed(List<SyllabusTextStructurer.ParsedSubject> parsed,
                              long examDate, Runnable onComplete) {
        long studentId = settings.getStudentId();
        new Thread(() -> {
            for (SyllabusTextStructurer.ParsedSubject ps : parsed) {
                Subject subject = new Subject(studentId, ps.name, 2, 2, examDate);
                subjectRepo.insertSubject(subject, subjectId -> {
                    int uOrder = 0;
                    for (SyllabusTextStructurer.ParsedUnit pu : ps.units) {
                        Unit unit = new Unit(subjectId, pu.name, uOrder++);
                        subjectRepo.insertUnit(unit, unitId -> {
                            int tOrder = 0;
                            for (String topicName : pu.topics) {
                                Topic t = new Topic(unitId, subjectId, topicName, 2, 45, tOrder++);
                                subjectRepo.insertTopic(t, null);
                            }
                        });
                    }
                });
            }
            if (onComplete != null) onComplete.run();
        }).start();
    }
}
