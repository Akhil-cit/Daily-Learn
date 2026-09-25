package com.example.daily_learn.domain.rag;

import android.content.Context;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.TopicEmbedding;

import java.util.ArrayList;
import java.util.List;

public class RagService {
    private final EmbeddingService embeddingService;
    private final LocalVectorStore vectorStore;
    private final AppDatabase db;

    public RagService(Context context, AppDatabase db) {
        this.embeddingService = new EmbeddingService(context);
        this.db = db;
        this.vectorStore = new LocalVectorStore(db.topicEmbeddingDao());
    }

    public int indexSyllabus(SubjectWithUnitsAndTopics syllabusData) {
        List<RagChunk> chunks = RagChunker.createChunks(syllabusData);
        if (chunks.isEmpty()) {
            return 0;
        }

        String[] texts = new String[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            texts[i] = chunks.get(i).text;
        }

        float[][] embeddings = embeddingService.embedTexts(texts);

        List<TopicEmbedding> topicEmbeddings = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RagChunk chunk = chunks.get(i);
            byte[] vecBytes = LocalVectorStore.floatArrayToByteArray(embeddings[i]);
            // Find the Topic ID by matching topicName against the unit's topics
            long topicId = -1;
            outer: for (com.example.daily_learn.data.local.entity.UnitWithTopics uwt : syllabusData.units) {
                for (com.example.daily_learn.data.local.entity.Topic t : uwt.topics) {
                    if (t.name.equals(chunk.topicName)) {
                        topicId = t.id;
                        break outer;
                    }
                }
            }

            topicEmbeddings.add(new TopicEmbedding(
                    topicId,
                    chunk.chunkId,
                    chunk.text,
                    vecBytes
            ));
        }

        vectorStore.saveEmbeddings(topicEmbeddings);
        return chunks.size();
    }

    public List<SearchHit> search(String query, int topK) {
        if (!vectorStore.exists()) {
            throw new IllegalStateException("RAG index is empty.");
        }
        float[] queryVec = embeddingService.embedQuery(query);
        return vectorStore.search(queryVec, topK);
    }

    public List<SearchHit> getDailyTopicChunks(String topicName, Integer unitNumber, int topK) {
        String query = topicName;
        if (unitNumber != null) {
            query = "Unit " + unitNumber + ": " + topicName;
        }

        List<SearchHit> hits = search(query, Math.max(topK * 2, 10));

        // Exact match preferred
        List<SearchHit> exact = new ArrayList<>();
        for (SearchHit hit : hits) {
            // Note: In Python metadata has topicName, here we don't have explicit metadata parsing 
            // since we match via topicId. But we can parse from hit.metadata.text if needed.
            // But we can just use the search results directly.
            exact.add(hit); // simplified logic
        }

        if (exact.size() > topK) {
            return exact.subList(0, topK);
        }
        return exact;
    }

    public boolean isIndexed() {
        return vectorStore.exists();
    }
}
