package com.example.daily_learn.data.local.entity;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

/** Relation: Unit + its Topics */
public class UnitWithTopics {
    @Embedded
    public Unit unit;

    @Relation(
        parentColumn = "id",
        entityColumn = "unit_id"
    )
    public List<Topic> topics;
}
