package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "progress",
    foreignKeys = {
        @ForeignKey(
            entity = Student.class,
            parentColumns = "id",
            childColumns = "student_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = Subject.class,
            parentColumns = "id",
            childColumns = "subject_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index("student_id"),
        @Index("subject_id")
    }
)
public class Progress {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "student_id")
    public long studentId;

    /** Null = overall progress (not per-subject) */
    @ColumnInfo(name = "subject_id")
    public Long subjectId;

    @ColumnInfo(name = "completed_topics")
    public int completedTopics;

    @ColumnInfo(name = "total_topics")
    public int totalTopics;

    /** Consecutive days with ≥ 1 completed session */
    @ColumnInfo(name = "streak_days")
    public int streakDays;

    /** Average quiz score 0-100 */
    @ColumnInfo(name = "quiz_average")
    public float quizAverage;

    @ColumnInfo(name = "last_updated")
    public long lastUpdated;

    public Progress() {}

    @Ignore
    public Progress(long studentId, Long subjectId, int totalTopics) {
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.totalTopics = totalTopics;
        this.completedTopics = 0;
        this.streakDays = 0;
        this.quizAverage = 0f;
        this.lastUpdated = System.currentTimeMillis();
    }
}
