package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "quiz_results",
    foreignKeys = @ForeignKey(
        entity = Quiz.class,
        parentColumns = "id",
        childColumns = "quiz_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("quiz_id")}
)
public class QuizResult {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "quiz_id")
    public long quizId;

    @ColumnInfo(name = "score")
    public int score;

    @ColumnInfo(name = "total")
    public int total;

    /** epoch millis */
    @ColumnInfo(name = "date_taken")
    public long dateTaken;

    public QuizResult() {}

    @Ignore
    public QuizResult(long quizId, int score, int total) {
        this.quizId = quizId;
        this.score = score;
        this.total = total;
        this.dateTaken = System.currentTimeMillis();
    }
}
