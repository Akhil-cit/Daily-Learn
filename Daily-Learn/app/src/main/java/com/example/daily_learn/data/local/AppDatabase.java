package com.example.daily_learn.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.daily_learn.data.local.dao.ExamDao;
import com.example.daily_learn.data.local.dao.ProgressDao;
import com.example.daily_learn.data.local.dao.QuizDao;
import com.example.daily_learn.data.local.dao.StudentDao;
import com.example.daily_learn.data.local.dao.StudyPlanDao;
import com.example.daily_learn.data.local.dao.SubjectDao;
import com.example.daily_learn.data.local.entity.Exam;
import com.example.daily_learn.data.local.entity.Progress;
import com.example.daily_learn.data.local.entity.Quiz;
import com.example.daily_learn.data.local.entity.QuizQuestion;
import com.example.daily_learn.data.local.entity.QuizResult;
import com.example.daily_learn.data.local.entity.Student;
import com.example.daily_learn.data.local.entity.StudyPlan;
import com.example.daily_learn.data.local.entity.StudySession;
import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.TopicEmbedding;
import com.example.daily_learn.data.local.entity.Unit;

@Database(
    entities = {
        Student.class, Subject.class, Unit.class, Topic.class,
        Exam.class, StudyPlan.class, StudySession.class,
        Quiz.class, QuizQuestion.class, QuizResult.class, Progress.class,
        TopicEmbedding.class
    },
    version = 2,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract StudentDao studentDao();
    public abstract SubjectDao subjectDao();
    public abstract StudyPlanDao studyPlanDao();
    public abstract QuizDao quizDao();
    public abstract ProgressDao progressDao();
    public abstract ExamDao examDao();
    public abstract com.example.daily_learn.data.local.dao.TopicEmbeddingDao topicEmbeddingDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "daily_learn_db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
