package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "units",
    foreignKeys = @ForeignKey(
        entity = Subject.class,
        parentColumns = "id",
        childColumns = "subject_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("subject_id")}
)
public class Unit {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "subject_id")
    public long subjectId;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "order_index")
    public int orderIndex;

    public Unit() {}

    @Ignore
    public Unit(long subjectId, String name, int orderIndex) {
        this.subjectId = subjectId;
        this.name = name;
        this.orderIndex = orderIndex;
    }
}
