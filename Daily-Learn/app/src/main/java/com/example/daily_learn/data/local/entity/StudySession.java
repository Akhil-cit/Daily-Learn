package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "study_sessions",
    foreignKeys = {
        @ForeignKey(
            entity = StudyPlan.class,
            parentColumns = "id",
            childColumns = "study_plan_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = Topic.class,
            parentColumns = "id",
            childColumns = "topic_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index("study_plan_id"),
        @Index("topic_id"),
        @Index("scheduled_date")
    }
)
public class StudySession {
    public static final String STATUS_SCHEDULED   = "SCHEDULED";
    public static final String STATUS_DONE        = "DONE";
    public static final String STATUS_MISSED      = "MISSED";
    public static final String STATUS_RESCHEDULED = "RESCHEDULED";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "study_plan_id")
    public long studyPlanId;

    @ColumnInfo(name = "topic_id")
    public long topicId;

    /** epoch millis of the day this session is scheduled for (normalized to midnight) */
    @ColumnInfo(name = "scheduled_date")
    public long scheduledDate;

    /** epoch millis when actually completed, null = not done */
    @ColumnInfo(name = "actual_date")
    public Long actualDate;

    @ColumnInfo(name = "status")
    public String status;

    /** True if this session is a revision session (not new learning) */
    @ColumnInfo(name = "is_revision")
    public boolean isRevision;

    public StudySession() {}

    @Ignore
    public StudySession(long studyPlanId, long topicId, long scheduledDate, boolean isRevision) {
        this.studyPlanId = studyPlanId;
        this.topicId = topicId;
        this.scheduledDate = scheduledDate;
        this.status = STATUS_SCHEDULED;
        this.isRevision = isRevision;
    }
}
