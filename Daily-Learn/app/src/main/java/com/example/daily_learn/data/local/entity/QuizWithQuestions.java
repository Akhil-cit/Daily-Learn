package com.example.daily_learn.data.local.entity;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

/** Quiz with all its questions */
public class QuizWithQuestions {
    @Embedded
    public Quiz quiz;

    @Relation(
        parentColumn = "id",
        entityColumn = "quiz_id"
    )
    public List<QuizQuestion> questions;
}
