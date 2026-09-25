package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "exams",
    foreignKeys = @ForeignKey(
        entity = Subject.class,
        parentColumns = "id",
        childColumns = "subject_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("subject_id")}
)
public class Exam {
    public static final String TYPE_MIDSEM = "MIDSEM";
    public static final String TYPE_FINAL  = "FINAL";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "subject_id")
    public long subjectId;

    /** epoch millis */
    @ColumnInfo(name = "date")
    public long date;

    @ColumnInfo(name = "type")
    public String type;

    public Exam() {}

    @Ignore
    public Exam(long subjectId, long date, String type) {
        this.subjectId = subjectId;
        this.date = date;
        this.type = type;
    }
}
