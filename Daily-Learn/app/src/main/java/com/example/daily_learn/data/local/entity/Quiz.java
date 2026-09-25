package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "quizzes",
    foreignKeys = @ForeignKey(
        entity = Topic.class,
        parentColumns = "id",
        childColumns = "topic_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("topic_id")}
)
public class Quiz {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "topic_id")
    public long topicId;

    public Quiz() {}

    @Ignore
    public Quiz(long topicId) {
        this.topicId = topicId;
    }
}
