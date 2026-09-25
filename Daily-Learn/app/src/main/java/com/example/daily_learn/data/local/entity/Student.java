package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "students")
public class Student {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "course")
    public String course;

    @ColumnInfo(name = "semester")
    public String semester;

    /** Daily available study time in minutes */
    @ColumnInfo(name = "daily_study_minutes")
    public int dailyStudyMinutes;

    @ColumnInfo(name = "created_at")
    public long createdAt; // epoch millis

    public Student() {}

    @Ignore
    public Student(String name, String course, String semester, int dailyStudyMinutes) {
        this.name = name;
        this.course = course;
        this.semester = semester;
        this.dailyStudyMinutes = dailyStudyMinutes;
        this.createdAt = System.currentTimeMillis();
    }
}
