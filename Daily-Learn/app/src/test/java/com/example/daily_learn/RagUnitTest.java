package com.example.daily_learn;

import android.content.Context;
import android.content.res.AssetManager;

import com.example.daily_learn.data.local.dao.TopicEmbeddingDao;
import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.TopicEmbedding;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.local.entity.UnitWithTopics;
import com.example.daily_learn.domain.rag.EmbeddingService;
import com.example.daily_learn.domain.rag.LocalVectorStore;
import com.example.daily_learn.domain.rag.RagChunk;
import com.example.daily_learn.domain.rag.RagChunker;
import com.example.daily_learn.domain.rag.SearchHit;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RagUnitTest {

    private EmbeddingService embeddingService;
    private LocalVectorStore vectorStore;
    private StubTopicEmbeddingDao dao;

    // Simple stub for testing VectorStore offline
    static class StubTopicEmbeddingDao implements TopicEmbeddingDao {
        List<TopicEmbedding> store = new ArrayList<>();

        @Override
        public void insertAll(List<TopicEmbedding> embeddings) {
            store.addAll(embeddings);
        }

        @Override
        public List<TopicEmbedding> getAllEmbeddings() {
            return store;
        }

        @Override
        public int getEmbeddingCount() {
            return store.size();
        }

        @Override
        public void deleteAll() {
            store.clear();
        }
    }

    @Before
    public void setup() throws Exception {
        Context mockContext = mock(Context.class);
        AssetManager mockAssetManager = mock(AssetManager.class);

        when(mockContext.getAssets()).thenReturn(mockAssetManager);
        
        // Point to the actual downloaded files in the app folder
        File vocabFile = new File("src/main/assets/vocab.txt");
        File modelFile = new File("src/main/assets/model.onnx");

        when(mockAssetManager.open("vocab.txt")).thenReturn(new FileInputStream(vocabFile));
        when(mockAssetManager.open("model.onnx")).thenReturn(new FileInputStream(modelFile));

        embeddingService = new EmbeddingService(mockContext);
        dao = new StubTopicEmbeddingDao();
        vectorStore = new LocalVectorStore(dao);
    }

    @Test
    public void testEmbeddingDimensionAndNorm() {
        float[] vector = embeddingService.embedQuery("Object Oriented Programming");
        assertEquals(384, vector.length);
        
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        assertEquals(1.0f, norm, 0.01f); // Check L2 normalization
    }

    @Test
    public void testChunkingAndRetrievalOOP() {
        // Build mock OOP syllabus
        Subject subject = new Subject(1, "Object Oriented Programming in Java", 1, 2, System.currentTimeMillis());
        subject.id = 1;

        Unit u1 = new Unit(1, "Introduction to OOP", 1);
        u1.orderIndex = 1;
        u1.id = 1;
        Topic t1_1 = new Topic(1, 1, "Classes and Objects", 1, 30, 1);
        t1_1.id = 1;
        Topic t1_2 = new Topic(1, 1, "Encapsulation", 1, 30, 2);
        t1_2.id = 2;

        Unit u2 = new Unit(1, "Advanced OOP", 2);
        u2.orderIndex = 2;
        u2.id = 2;
        Topic t2_1 = new Topic(2, 1, "Inheritance", 2, 45, 1);
        t2_1.id = 3;
        Topic t2_2 = new Topic(2, 1, "Polymorphism", 3, 60, 2);
        t2_2.id = 4;
        Topic t2_3 = new Topic(2, 1, "Abstract Classes and Interfaces", 3, 60, 3);
        t2_3.id = 5;

        UnitWithTopics uwt1 = new UnitWithTopics();
        uwt1.unit = u1;
        uwt1.topics = Arrays.asList(t1_1, t1_2);

        UnitWithTopics uwt2 = new UnitWithTopics();
        uwt2.unit = u2;
        uwt2.topics = Arrays.asList(t2_1, t2_2, t2_3);

        SubjectWithUnitsAndTopics syllabus = new SubjectWithUnitsAndTopics();
        syllabus.subject = subject;
        syllabus.units = Arrays.asList(uwt1, uwt2);

        // 1. Chunking
        List<RagChunk> chunks = RagChunker.createChunks(syllabus);
        assertEquals(5, chunks.size());
        assertEquals("u1_t2", chunks.get(1).chunkId);
        assertTrue(chunks.get(1).text.contains("Encapsulation"));

        // 2. Embedding & Indexing
        String[] texts = new String[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            texts[i] = chunks.get(i).text;
        }

        float[][] embeddings = embeddingService.embedTexts(texts);
        List<TopicEmbedding> topicEmbeddings = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            byte[] vecBytes = LocalVectorStore.floatArrayToByteArray(embeddings[i]);
            topicEmbeddings.add(new TopicEmbedding(
                    (long) (i + 1), // mock topic id
                    chunks.get(i).chunkId,
                    chunks.get(i).text,
                    vecBytes
            ));
        }

        vectorStore.saveEmbeddings(topicEmbeddings);
        assertTrue(vectorStore.exists());

        // 3. Retrieval
        float[] queryVec = embeddingService.embedQuery("What is data hiding and encapsulation?");
        List<SearchHit> hits = vectorStore.search(queryVec, 3);

        assertNotNull(hits);
        assertEquals(3, hits.size());
        
        // The most relevant topic should be "Encapsulation"
        SearchHit topHit = hits.get(0);
        assertTrue(topHit.metadata.text.contains("Encapsulation"));
        
        System.out.println("Top Hit: " + topHit.metadata.text + "\nScore: " + topHit.score);
    }
}
