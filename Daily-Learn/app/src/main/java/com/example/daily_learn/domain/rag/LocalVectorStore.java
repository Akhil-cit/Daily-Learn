package com.example.daily_learn.domain.rag;

import com.example.daily_learn.data.local.dao.TopicEmbeddingDao;
import com.example.daily_learn.data.local.entity.TopicEmbedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalVectorStore {
    private final TopicEmbeddingDao topicEmbeddingDao;

    public LocalVectorStore(TopicEmbeddingDao dao) {
        this.topicEmbeddingDao = dao;
    }

    public static byte[] floatArrayToByteArray(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    public static float[] byteArrayToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    public void saveEmbeddings(List<TopicEmbedding> embeddings) {
        topicEmbeddingDao.deleteAll();
        topicEmbeddingDao.insertAll(embeddings);
    }

    public boolean exists() {
        return topicEmbeddingDao.getEmbeddingCount() > 0;
    }

    public int getChunkCount() {
        return topicEmbeddingDao.getEmbeddingCount();
    }

    public List<SearchHit> search(float[] queryVec, int topK) {
        List<TopicEmbedding> allEmbeddings = topicEmbeddingDao.getAllEmbeddings();
        List<SearchHit> hits = new ArrayList<>();

        for (TopicEmbedding metadata : allEmbeddings) {
            float[] chunkVec = byteArrayToFloatArray(metadata.vector);
            float score = computeInnerProduct(queryVec, chunkVec);
            hits.add(new SearchHit(score, metadata));
        }

        // Sort by score descending
        Collections.sort(hits, (a, b) -> Float.compare(b.score, a.score));

        if (hits.size() > topK) {
            return hits.subList(0, topK);
        }
        return hits;
    }

    private float computeInnerProduct(float[] vec1, float[] vec2) {
        float dotProduct = 0;
        int len = Math.min(vec1.length, vec2.length);
        for (int i = 0; i < len; i++) {
            dotProduct += vec1[i] * vec2[i];
        }
        return dotProduct;
    }
}
