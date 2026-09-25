package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "quiz_questions",
    foreignKeys = @ForeignKey(
        entity = Quiz.class,
        parentColumns = "id",
        childColumns = "quiz_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("quiz_id")}
)
public class QuizQuestion {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "quiz_id")
    public long quizId;

    @ColumnInfo(name = "question_text")
    public String questionText;

    /**
     * JSON array of 4 option strings, e.g.:
     * ["Option A","Option B","Option C","Option D"]
     */
    @ColumnInfo(name = "options_json")
    public String optionsJson;

    /** 0-based index of the correct option */
    @ColumnInfo(name = "correct_index")
    public int correctIndex;

    /** Order within the quiz */
    @ColumnInfo(name = "order_index")
    public int orderIndex;

    public QuizQuestion() {}

    @Ignore
    public QuizQuestion(long quizId, String questionText, String optionsJson,
                        int correctIndex, int orderIndex) {
        this.quizId = quizId;
        this.questionText = questionText;
        this.optionsJson = optionsJson;
        this.correctIndex = correctIndex;
        this.orderIndex = orderIndex;
    }
}
