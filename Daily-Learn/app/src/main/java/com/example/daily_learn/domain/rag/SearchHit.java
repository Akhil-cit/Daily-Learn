package com.example.daily_learn.domain.rag;

import com.example.daily_learn.data.local.entity.TopicEmbedding;

public class SearchHit {
    public float score;
    public TopicEmbedding metadata;

    public SearchHit(float score, TopicEmbedding metadata) {
        this.score = score;
        this.metadata = metadata;
    }
}
