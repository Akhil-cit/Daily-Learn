package com.example.daily_learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.prefs.SettingsStore;
import com.example.daily_learn.data.repository.ProgressRepository;
import com.example.daily_learn.data.repository.StudyPlanRepository;
import com.example.daily_learn.data.repository.SubjectRepository;
import com.example.daily_learn.domain.ExplanationSource;
import com.example.daily_learn.domain.LocalExplanationSource;

public class LearningViewModel extends AndroidViewModel {

    private final SubjectRepository subjectRepo;
    private final StudyPlanRepository planRepo;
    private final ProgressRepository progressRepo;
    private final SettingsStore settings;
    private final ExplanationSource explanationSource;

    public LiveData<Topic> topic;
    public final MutableLiveData<com.example.daily_learn.data.local.entity.Subject> subject = new MutableLiveData<>();
    public final MutableLiveData<Boolean> sessionCompleted = new MutableLiveData<>(false);

    // Content fields derived from ExplanationSource
    public final MutableLiveData<String> explanation     = new MutableLiveData<>();
    public final MutableLiveData<String> keyConcepts     = new MutableLiveData<>();
    public final MutableLiveData<String> example         = new MutableLiveData<>();
    public final MutableLiveData<String> importantPoints = new MutableLiveData<>();
    public final MutableLiveData<String> practiceQ       = new MutableLiveData<>();

    private long currentTopicId;
    private long currentSessionId = -1;
    private final java.util.concurrent.ExecutorService bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public LearningViewModel(@NonNull Application app) {
        super(app);
        subjectRepo       = new SubjectRepository(app);
        planRepo          = new StudyPlanRepository(app);
        progressRepo      = new ProgressRepository(app);
        settings          = new SettingsStore(app);
        explanationSource = new LocalExplanationSource();
    }

    public void loadTopic(long topicId, long sessionId) {
        this.currentTopicId  = topicId;
        this.currentSessionId = sessionId;
        topic = subjectRepo.getTopicById(topicId);
        topic.observeForever(t -> {
            if (t != null) {
                bgExecutor.execute(() -> {
                    com.example.daily_learn.data.local.entity.Subject s = subjectRepo.getSubjectByIdSync(t.subjectId);
                    String subName = (s != null) ? s.name : "";
                    if (s != null) {
                        subject.postValue(s);
                    }
                    loadContent(t.name, subName);
                });
            }
        });
    }

    private void loadContent(String topicName, String subjectName) {
        explanation.postValue(explanationSource.getExplanation(topicName, subjectName));
        keyConcepts.postValue(explanationSource.getKeyConcepts(topicName, subjectName));
        example.postValue(explanationSource.getExample(topicName, subjectName));
        importantPoints.postValue(explanationSource.getImportantPoints(topicName, subjectName));
        practiceQ.postValue(explanationSource.getPracticeQuestion(topicName, subjectName));
    }

    /**
     * Called when student taps "Yes, Understood / Need Revision / Difficult".
     * feedback: 0 = understood, 1 = needs revision, 2 = difficult
     */
    public void submitFeedback(int feedback) {
        boolean needsRevision = (feedback == 1 || feedback == 2);
        subjectRepo.markTopicCompleted(currentTopicId);
        subjectRepo.setNeedsRevision(currentTopicId, needsRevision);

        if (currentSessionId != -1) {
            planRepo.markSessionDone(currentSessionId);
        }

        long studentId = settings.getStudentId();
        progressRepo.recordDailyStudyActivity(studentId, null);

        sessionCompleted.postValue(true);
    }
}
