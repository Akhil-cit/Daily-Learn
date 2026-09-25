package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "topics",
    foreignKeys = @ForeignKey(
        entity = Unit.class,
        parentColumns = "id",
        childColumns = "unit_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("unit_id")}
)
public class Topic {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "unit_id")
    public long unitId;

    @ColumnInfo(name = "name")
    public String name;

    /** 1=Easy, 2=Medium, 3=Hard */
    @ColumnInfo(name = "difficulty")
    public int difficulty;

    /** Estimated minutes to study this topic */
    @ColumnInfo(name = "estimated_minutes")
    public int estimatedMinutes;

    @ColumnInfo(name = "is_completed")
    public boolean isCompleted;

    /** True if student marked "Need Revision" or "Difficult" */
    @ColumnInfo(name = "needs_revision")
    public boolean needsRevision;

    /** epoch millis, 0 = never reviewed */
    @ColumnInfo(name = "last_reviewed_date")
    public long lastReviewedDate;

    /** Cached: which subject this topic belongs to (denormalized for scheduler) */
    @ColumnInfo(name = "subject_id")
    public long subjectId;

    /** Order within its unit */
    @ColumnInfo(name = "order_index")
    public int orderIndex;

    public Topic() {}

    @Ignore
    public Topic(long unitId, long subjectId, String name, int difficulty,
                 int estimatedMinutes, int orderIndex) {
        this.unitId = unitId;
        this.subjectId = subjectId;
        this.name = name;
        this.difficulty = difficulty;
        this.estimatedMinutes = estimatedMinutes;
        this.orderIndex = orderIndex;
        this.isCompleted = false;
        this.needsRevision = false;
        this.lastReviewedDate = 0;
    }
}
