package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "study_plans",
    foreignKeys = @ForeignKey(
        entity = Student.class,
        parentColumns = "id",
        childColumns = "student_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("student_id")}
)
public class StudyPlan {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "student_id")
    public long studentId;

    /** epoch millis when this plan was generated */
    @ColumnInfo(name = "generated_date")
    public long generatedDate;

    /** epoch millis of the target exam date */
    @ColumnInfo(name = "exam_date")
    public long examDate;

    public StudyPlan() {}

    @Ignore
    public StudyPlan(long studentId, long examDate) {
        this.studentId = studentId;
        this.generatedDate = System.currentTimeMillis();
        this.examDate = examDate;
    }
}
