package com.example.daily_learn.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.daily_learn.data.local.entity.TopicEmbedding;

import java.util.List;

@Dao
public interface TopicEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TopicEmbedding> embeddings);

    @Query("SELECT * FROM topic_embeddings")
    List<TopicEmbedding> getAllEmbeddings();

    @Query("SELECT COUNT(*) FROM topic_embeddings")
    int getEmbeddingCount();

    @Query("DELETE FROM topic_embeddings")
    void deleteAll();
}
