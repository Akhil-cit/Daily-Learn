package com.example.daily_learn.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "topic_embeddings",
    foreignKeys = @ForeignKey(
        entity = Topic.class,
        parentColumns = "id",
        childColumns = "topic_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("topic_id")}
)
public class TopicEmbedding {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "topic_id")
    public long topicId;

    @ColumnInfo(name = "chunk_id")
    public String chunkId;

    @ColumnInfo(name = "text")
    public String text;

    /** Stored as a byte array to avoid expensive String serialization/deserialization */
    @ColumnInfo(name = "vector")
    public byte[] vector;

    public TopicEmbedding() {}

    public TopicEmbedding(long topicId, String chunkId, String text, byte[] vector) {
        this.topicId = topicId;
        this.chunkId = chunkId;
        this.text = text;
        this.vector = vector;
    }
}
