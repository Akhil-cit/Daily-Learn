package com.example.daily_learn;

import android.content.Context;
import android.util.Log;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.daily_learn.data.local.AppDatabase;
import com.example.daily_learn.data.local.dao.SubjectDao;
import com.example.daily_learn.data.local.entity.Student;
import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.TopicEmbedding;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.domain.ai.AiTutorEngine;
import com.example.daily_learn.domain.ai.LlamaCppPoC;
import com.example.daily_learn.domain.ai.LocalLLMEngine;
import com.example.daily_learn.domain.parser.SyllabusTextStructurer;
import com.example.daily_learn.domain.rag.RagService;
import com.example.daily_learn.domain.rag.SearchHit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Fully instrumented end-to-end reliability check:
 *
 *   REAL syllabus text (with OCR noise / CO / prereq variants)
 *   → REAL SyllabusTextStructurer parse
 *   → REAL Room database persist
 *   → REAL RagService.indexSyllabus()
 *   → REAL RagService.search()
 *   → REAL LocalLLMEngine (llama.cpp JNI / /data/local/tmp/benchmark_model.gguf)
 *   → REAL AiTutorEngine generateResponse() + generateResponseStreaming()
 *   → REAL streaming tokens from native generation
 *
 * Explicit timing markers are logged at every stage so a suspicious "~3 second"
 * completion can be immediately diagnosed.
 *
 * The test fails if:
 *  - the LLM was not actually loaded (modelLoaded == false)
 *  - RAG search was not called (ragSearchCalled == false)
 *  - native generation was not reached (llmGenerateCalled == false)
 *  - no streaming tokens were received (streamingTokenCount == 0)
 *  - total elapsed < 5 s (LLM must take meaningful time)
 *  - noise leaked into topics or RAG chunks
 */
@RunWith(AndroidJUnit4.class)
public class EndToEndFlowTest {

    private static final String TAG = "EndToEndFlowTest";
    private static final String MODEL_PATH = "/data/local/tmp/benchmark_model.gguf";

    private AppDatabase db;
    private SubjectDao  subjectDao;
    private Context     context;

    /**
     * Realistic OCR-style syllabus that includes:
     *  - course-code header lines (must be filtered)
     *  - OCR-corrupted CO codes: C01, C02 (must be filtered)
     *  - Prerequisites (must be filtered)
     *  - LTPc / credit metadata (must be filtered)
     *  - Real curriculum topics (must survive)
     */
    private static final String SYLLABUS =
            // ─ Metadata noise ─────────────────────────────────────────
            "25SS212\n" +
            "25SS212 / OBJECT ORIENTED PROGRAMMING\n" +
            "LTPc 4 0 0 4\n" +
            "Semester II Year 2025-2026\n" +
            "Course Type: Professional Core\n" +
            // ─ CO noise ────────────────────────────────────────────────
            "COURSE OUTCOME\n" +
            "C01: Understand the features of C++ (BT2)\n" +    // OCR variant
            "C02: Apply classes and objects (BT3)\n" +          // OCR variant
            "Prerequisite : CS8201 Programming in C\n" +
            "\n" +
            // ─ Real curriculum ─────────────────────────────────────────
            "UNIT I - PRINCIPLES OF OOP\n" +
            "1. Software crisis\n" +
            "2. Object Oriented Programming Paradigm\n" +
            "3. Classes and Objects\n" +
            "\n" +
            "UNIT II - INHERITANCE\n" +
            "1. Types of Inheritance\n" +
            "2. Virtual functions\n" +
            "\n" +
            // ─ Tail noise ──────────────────────────────────────────────
            "Reference Books\n" +
            "1. Bjarne Stroustrup\n" +
            "Marks: 100";

    @Before
    public void createDb() {
        context    = ApplicationProvider.getApplicationContext();
        db         = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        subjectDao = db.subjectDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    // ─── Phase 1+2: Parse + persist ──────────────────────────────────────────

    private long seedSyllabus() {
        long parseStart = System.currentTimeMillis();
        Log.i(TAG, "[TEST] Parse START");

        long studentId = db.studentDao().insert(new Student("Test", "OOP", "II", 120));

        List<SyllabusTextStructurer.ParsedSubject> parsed =
                new SyllabusTextStructurer().parse(SYLLABUS);

        long parseEnd = System.currentTimeMillis();
        Log.i(TAG, "[TEST] Parse END  elapsed=" + (parseEnd - parseStart) + " ms  subjects=" + parsed.size());

        long dbStart = System.currentTimeMillis();
        Log.i(TAG, "[TEST] DB persist START");

        for (SyllabusTextStructurer.ParsedSubject ps : parsed) {
            long subjectId = subjectDao.insertSubject(
                    new Subject(studentId, ps.name, 2, 2, System.currentTimeMillis()));
            int uOrder = 0;
            for (SyllabusTextStructurer.ParsedUnit pu : ps.units) {
                long unitId = subjectDao.insertUnit(new Unit(subjectId, pu.name, uOrder++));
                int tOrder  = 0;
                for (String topicName : pu.topics) {
                    subjectDao.insertTopic(new Topic(unitId, subjectId, topicName, 2, 45, tOrder++));
                }
            }
        }

        long dbEnd = System.currentTimeMillis();
        Log.i(TAG, "[TEST] DB persist END  elapsed=" + (dbEnd - dbStart) + " ms");

        return studentId;
    }

    private List<SubjectWithUnitsAndTopics> readTrees(long studentId) {
        return subjectDao.getAllSubjectsWithUnitsAndTopicsSync(studentId);
    }

    // ─── Test 1: Parser noise exclusion ──────────────────────────────────────

    @Test
    public void testNoiseNeverBecomesTopic() {
        long studentId = seedSyllabus();
        List<SubjectWithUnitsAndTopics> trees = readTrees(studentId);
        assertTrue("Expected at least one subject", !trees.isEmpty());

        List<String> topics = new ArrayList<>();
        for (SubjectWithUnitsAndTopics s : trees) {
            if (s.units != null) {
                for (com.example.daily_learn.data.local.entity.UnitWithTopics u : s.units) {
                    if (u.topics != null) {
                        for (Topic t : u.topics) {
                            String name = t.name.toLowerCase();
                            topics.add(name);
                            Log.i(TAG, "[TEST] TOPIC: " + t.name);
                        }
                    }
                }
            }
        }

        // Real content must survive
        boolean hasSoftwareCrisis = false, hasInheritance = false, hasClasses = false;
        for (String t : topics) {
            if (t.contains("software crisis")) hasSoftwareCrisis = true;
            if (t.contains("inheritance") || t.contains("types of inheritance")) hasInheritance = true;
            if (t.contains("classes and objects")) hasClasses = true;
        }
        assertTrue("Software crisis must survive", hasSoftwareCrisis);
        assertTrue("Types of Inheritance must survive", hasInheritance);
        assertTrue("Classes and Objects must survive", hasClasses);

        // Noise MUST NOT leak
        for (String t : topics) {
            // Course codes
            assertFalse("Course code 25SS212 leaked as topic: " + t, t.contains("25ss212"));
            // CO OCR variants
            if (t.startsWith("c0") || t.startsWith("co")) {
                // Only allow actual topic words starting with "co" (e.g. "collision")
                assertFalse("CO code leaked as topic: " + t,
                        t.matches("^c[o0][1-9].*") || t.matches("^co[1-9].*"));
            }
            assertFalse("Prerequisite leaked as topic: " + t, t.contains("prerequisite"));
            assertFalse("Reference books leaked as topic: " + t, t.contains("bjarne"));
            assertFalse("Marks metadata leaked as topic: " + t, t.contains("marks"));
            assertFalse("LTPc leaked as topic: " + t, t.contains("ltpc"));
            assertFalse("Course type leaked as topic: " + t, t.contains("course type"));
            assertFalse("Semester leaked as topic: " + t, t.startsWith("semester"));
        }
    }

    // ─── Test 2: Full pipeline with real LLM instrumentation ─────────────────

    @Test
    public void testSequentialGroundedAnswersAndStreaming() throws Exception {
        long testStart = System.currentTimeMillis();

        // ── Phase 2: Parse + DB ────────────────────────────────────────────
        long studentId = seedSyllabus();
        List<SubjectWithUnitsAndTopics> trees = readTrees(studentId);
        assertTrue("Expected at least one subject to index", !trees.isEmpty());

        // ── Phase 3: RAG indexing ─────────────────────────────────────────
        long ragIndexStart = System.currentTimeMillis();
        Log.i(TAG, "[TEST] RAG index START");

        RagService ragService = new RagService(context, db);
        int totalChunks = 0;
        for (SubjectWithUnitsAndTopics s : trees) {
            totalChunks += ragService.indexSyllabus(s);
        }

        long ragIndexEnd = System.currentTimeMillis();
        Log.i(TAG, "[TEST] RAG index END  elapsed=" + (ragIndexEnd - ragIndexStart)
                + " ms  chunks=" + totalChunks);

        assertTrue("RAG must be indexed with at least 2 chunks", totalChunks >= 2);
        assertTrue("RAG must report isIndexed()", ragService.isIndexed());

        // Verify RAG index has no noise
        List<TopicEmbedding> allEmbeddings = db.topicEmbeddingDao().getAllEmbeddings();
        Log.i(TAG, "[TEST] RAG chunk count=" + allEmbeddings.size());
        for (TopicEmbedding e : allEmbeddings) {
            String t = e.text.toLowerCase();
            assertFalse("Noise in RAG index — CO code: "    + e.text, t.matches(".*\\bc0[1-9]\\b.*") || t.matches(".*\\bco[1-9]\\b.*"));
            assertFalse("Noise in RAG index — prereq: "     + e.text, t.contains("prerequisite"));
            assertFalse("Noise in RAG index — ref books: "  + e.text, t.contains("reference books"));
            assertFalse("Noise in RAG index — marks: "      + e.text, t.contains("marks:"));
            assertFalse("Noise in RAG index — ltpc: "       + e.text, t.contains("ltpc"));
        }

        // ── Phase 3: RAG search ────────────────────────────────────────────
        long ragSearchStart = System.currentTimeMillis();
        Log.i(TAG, "[TEST] RAG search START");

        List<SearchHit> hits = ragService.search("inheritance object oriented programming", 3);

        long ragSearchEnd = System.currentTimeMillis();
        Log.i(TAG, "[TEST] RAG search END  elapsed=" + (ragSearchEnd - ragSearchStart)
                + " ms  hits=" + hits.size());

        // ── Phase 4: Load real LLM ────────────────────────────────────────
        long llmLoadStart = System.currentTimeMillis();
        Log.i(TAG, "[TEST] LLM load START  path=" + MODEL_PATH);

        LocalLLMEngine engine = new LocalLLMEngine();
        boolean modelLoaded   = engine.loadModel(MODEL_PATH, 0); // 0 GPU layers = CPU

        long llmLoadEnd = System.currentTimeMillis();
        Log.i(TAG, "[TEST] LLM load END  elapsed=" + (llmLoadEnd - llmLoadStart)
                + " ms  loaded=" + modelLoaded);

        // MANDATORY: the LLM must actually load. If this fails, the model file is missing.
        assertTrue("[FAIL] LLM was not loaded — ensure " + MODEL_PATH
                + " is pushed to the device before running this test.\n"
                + "Run: adb push <model.gguf> " + MODEL_PATH, modelLoaded);

        try {
            AiTutorEngine tutor = new AiTutorEngine(engine, ragService);

            // ── Phase 5: Q1 — blocking generation ────────────────────────
            long genQ1Start = System.currentTimeMillis();
            Log.i(TAG, "[TEST] LLM generation Q1 START");

            String a1 = tutor.generateResponse(
                    "Inheritance", "Object Oriented Programming",
                    "What is inheritance in object-oriented programming?", 64);

            long genQ1End = System.currentTimeMillis();
            Log.i(TAG, "[TEST] LLM generation Q1 END  elapsed=" + (genQ1End - genQ1Start)
                    + " ms  answer=" + a1);

            assertTrue("[FAIL] Q1 answer must be non-empty", a1 != null && !a1.trim().isEmpty());
            assertFalse("[FAIL] Q1 must not return engine error", a1.startsWith("Error:"));

            // ── Phase 5: Q2 — independent sequential generation ───────────
            long genQ2Start = System.currentTimeMillis();
            Log.i(TAG, "[TEST] LLM generation Q2 START");

            String a2 = tutor.generateResponse(
                    "Classes and Objects", "Object Oriented Programming",
                    "What are Classes and Objects in OOP?", 64);

            long genQ2End = System.currentTimeMillis();
            Log.i(TAG, "[TEST] LLM generation Q2 END  elapsed=" + (genQ2End - genQ2Start)
                    + " ms  answer=" + a2);

            assertTrue("[FAIL] Q2 answer must be non-empty", a2 != null && !a2.trim().isEmpty());
            assertFalse("[FAIL] Q2 must not return engine error", a2.startsWith("Error:"));

            // ── Phase 5: Streaming generation ────────────────────────────
            final StringBuilder streamed     = new StringBuilder();
            final CountDownLatch latch        = new CountDownLatch(1);
            final AtomicInteger tokenCount    = new AtomicInteger(0);
            final AtomicBoolean streamStarted = new AtomicBoolean(false);

            long streamStart = System.currentTimeMillis();
            Log.i(TAG, "[TEST] STREAM START");

            tutor.generateResponseStreaming(
                    "Inheritance", "OOP",
                    "What does inheritance allow?", 64,
                    new LlamaCppPoC.StreamCallback() {
                        @Override
                        public void onToken(String token) {
                            streamStarted.set(true);
                            streamed.append(token);
                            if (token != null && !token.isEmpty()) {
                                int n = tokenCount.incrementAndGet();
                                Log.i(TAG, "[TEST] STREAM_TOKEN #" + n + " = [" + token + "]");
                            }
                        }
                        @Override
                        public void onComplete(String response) {
                            long elapsed = System.currentTimeMillis() - streamStart;
                            Log.i(TAG, "[TEST] STREAM COMPLETE  elapsed=" + elapsed
                                    + " ms  tokens=" + tokenCount.get()
                                    + "  text=" + response);
                            latch.countDown();
                        }
                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "[TEST] STREAM ERROR: " + error);
                            latch.countDown();
                        }
                    });

            boolean streamDone = latch.await(120, TimeUnit.SECONDS);
            long streamEnd     = System.currentTimeMillis();
            Log.i(TAG, "[TEST] STREAM END  elapsed=" + (streamEnd - streamStart)
                    + " ms  done=" + streamDone + "  tokens=" + tokenCount.get());

            assertTrue("[FAIL] Streaming must complete within 120 s", streamDone);
            assertTrue("[FAIL] Streaming must deliver > 0 tokens — if 0, native generation was never entered",
                    tokenCount.get() > 0);
            assertTrue("[FAIL] Streamed answer must be non-empty", streamed.length() > 0);

            // ── Timing sanity check ───────────────────────────────────────
            long totalElapsed = System.currentTimeMillis() - testStart;
            Log.i(TAG, "[TEST] TOTAL elapsed=" + totalElapsed + " ms");
            Log.i(TAG, "[TEST] TIMING BREAKDOWN:");
            Log.i(TAG, "  LLM load:       " + (llmLoadEnd - llmLoadStart) + " ms");
            Log.i(TAG, "  LLM gen Q1:     " + (genQ1End - genQ1Start) + " ms");
            Log.i(TAG, "  LLM gen Q2:     " + (genQ2End - genQ2Start) + " ms");
            Log.i(TAG, "  Stream:         " + (streamEnd - streamStart) + " ms");
            Log.i(TAG, "  Total:          " + totalElapsed + " ms");

            if (totalElapsed < 5000) {
                Log.w(TAG, "[WARNING] Total elapsed < 5 s. The real LLM likely was NOT invoked. "
                        + "Check whether " + MODEL_PATH + " exists and loaded correctly.");
                fail("[FAIL] Test completed in < 5 seconds. Real LLM inference was bypassed. "
                        + "Total=" + totalElapsed + " ms. "
                        + "Ensure model file exists at " + MODEL_PATH);
            }

        } finally {
            engine.unloadModel();
        }
    }
}
