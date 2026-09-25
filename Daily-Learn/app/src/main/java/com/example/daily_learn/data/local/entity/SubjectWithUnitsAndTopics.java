package com.example.daily_learn.data.local.entity;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

/** Relation: Subject → Units → Topics (two-level nesting) */
public class SubjectWithUnitsAndTopics {
    @Embedded
    public Subject subject;

    @Relation(
        entity = Unit.class,
        parentColumn = "id",
        entityColumn = "subject_id"
    )
    public List<UnitWithTopics> units;
}
