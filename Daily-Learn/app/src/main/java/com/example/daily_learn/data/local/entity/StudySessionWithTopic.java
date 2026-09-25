package com.example.daily_learn.data.local.entity;

import androidx.room.Embedded;
import androidx.room.Relation;

/** Relation: StudySession joined with its Topic */
public class StudySessionWithTopic {
    @Embedded
    public StudySession session;

    @Relation(
        parentColumn = "topic_id",
        entityColumn = "id"
    )
    public Topic topic;
}
