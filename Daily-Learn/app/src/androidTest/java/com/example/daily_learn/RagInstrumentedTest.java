package com.example.daily_learn;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.dao.SubjectDao;
import com.example.daily_learn.data.local.entity.Student;
import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.local.entity.UnitWithTopics;
import com.example.daily_learn.domain.rag.EmbeddingService;
import com.example.daily_learn.domain.rag.RagService;
import com.example.daily_learn.domain.rag.SearchHit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class RagInstrumentedTest {

    private AppDatabase db;
    private RagService ragService;
    private EmbeddingService embeddingService;
    private Context context;

    @Before
    public void createDb() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        ragService = new RagService(context, db);
        embeddingService = new EmbeddingService(context);
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void testEmbeddingShape() {
        float[] vector = embeddingService.embedQuery("Object Oriented Programming");
        assertEquals("Embedding dimension should be 384", 384, vector.length);
        
        // Check L2 norm is approximately 1.0
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        assertEquals(1.0f, norm, 0.01f);
    }

    @Test
    public void testRagIndexAndRetrieveOOP() {
        SubjectDao subjectDao = db.subjectDao();

        // Insert a real Student first so the Subject -> Student FK is satisfied.
        long studentId = db.studentDao().insert(
                new Student("Test", "OOP", "II", 120));

        // Build the OOP syllabus through the real DAOs, letting Room assign FK-safe ids.
        Subject subject = new Subject(studentId, "Object Oriented Programming in Java", 1, 2, System.currentTimeMillis());
        long subjectId = subjectDao.insertSubject(subject);

        Unit u1 = new Unit(subjectId, "Introduction to OOP", 1);
        long u1Id = subjectDao.insertUnit(u1);
        Topic t1_1 = new Topic(u1Id, subjectId, "Classes and Objects", 1, 30, 1);
        Topic t1_2 = new Topic(u1Id, subjectId, "Encapsulation", 1, 30, 2);
        subjectDao.insertTopic(t1_1);
        subjectDao.insertTopic(t1_2);

        Unit u2 = new Unit(subjectId, "Advanced OOP", 2);
        long u2Id = subjectDao.insertUnit(u2);
        Topic t2_1 = new Topic(u2Id, subjectId, "Inheritance", 2, 45, 1);
        Topic t2_2 = new Topic(u2Id, subjectId, "Polymorphism", 3, 60, 2);
        Topic t2_3 = new Topic(u2Id, subjectId, "Abstract Classes and Interfaces", 3, 60, 3);
        subjectDao.insertTopic(t2_1);
        subjectDao.insertTopic(t2_2);
        subjectDao.insertTopic(t2_3);

        // Read back the full tree exactly as the production RAG-index path does.
        List<SubjectWithUnitsAndTopics> trees = subjectDao.getAllSubjectsWithUnitsAndTopicsSync(studentId);
        assertEquals(1, trees.size());
        SubjectWithUnitsAndTopics syllabus = trees.get(0);

        // 1. Test Indexing
        int count = ragService.indexSyllabus(syllabus);
        assertEquals(5, count);
        assertTrue(ragService.isIndexed());

        // 2. Test Search Retrieval
        List<SearchHit> hits = ragService.search("What is encapsulation and data hiding?", 3);

        assertNotNull(hits);
        assertTrue(hits.size() > 0);

        // The most relevant topic should be "Encapsulation"
        SearchHit topHit = hits.get(0);
        assertTrue(topHit.metadata.text.contains("Encapsulation"));

        // 3. Test Daily Topic Match (Exact/Semantic fallback)
        List<SearchHit> dailyHits = ragService.getDailyTopicChunks("Polymorphism", 2, 3);
        assertTrue(dailyHits.size() > 0);
        assertTrue(dailyHits.get(0).metadata.text.contains("Polymorphism"));
    }
}
