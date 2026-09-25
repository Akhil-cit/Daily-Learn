package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "subjects",
    foreignKeys = @ForeignKey(
        entity = Student.class,
        parentColumns = "id",
        childColumns = "student_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("student_id")}
)
public class Subject {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "student_id")
    public long studentId;

    @ColumnInfo(name = "name")
    public String name;

    /** 1 = low, 2 = medium, 3 = high */
    @ColumnInfo(name = "priority")
    public int priority;

    /** 1=Easy, 2=Medium, 3=Hard */
    @ColumnInfo(name = "difficulty")
    public int difficulty;

    /** epoch millis of exam date for this subject */
    @ColumnInfo(name = "exam_date")
    public long examDate;

    /** 0-100 percentage */
    @ColumnInfo(name = "completion_percent")
    public int completionPercent;

    public Subject() {}

    @Ignore
    public Subject(long studentId, String name, int priority, int difficulty, long examDate) {
        this.studentId = studentId;
        this.name = name;
        this.priority = priority;
        this.difficulty = difficulty;
        this.examDate = examDate;
        this.completionPercent = 0;
    }
}
