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
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.local.entity.UnitWithTopics;
import com.example.daily_learn.domain.parser.SyllabusTextStructurer;
import com.example.daily_learn.domain.rag.RagService;
import com.example.daily_learn.domain.rag.SearchHit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * PHASE 3: Real Syllabus -> Real Parser -> Real Room DB -> Real RAG Index -> Real RAG Search.
 *
 * Uses the EXACT ADS OCR corpus that previously generated broken JSON output.
 * Verifies the full pipeline end-to-end WITHOUT invoking the LLM.
 */
@RunWith(AndroidJUnit4.class)
public class Phase3RealDbRagTest {

    private static final String TAG = "Phase3RealDbRagTest";

    // Exact OCR corpus from the broken-output bug report.
    private static final String ADS_SYLLABUS_OCR =
            // Header / metadata noise (must all be filtered)
            "20MSS33\n" +
            "20mss33 - Advanced Data Structures\n" +
            "20mss33 -Advanced Data Structures - Part 1\n" +
            "20mss33 -Advanced Data Structures - Part 2\n" +
            "20mss33 -Advanced Data Structures - Part 3\n" +
            "20MSS22\n" +
            "LTPc 3003\n" +
            "L T P C\n" +
            "3 0 0 3\n" +
            "3003\n" +
            "Contact Hours\n" +
            "Contact Hours Definition\n" +
            "Course Code : 20MSS33\n" +
            "Course Title : Advanced Data Structures\n" +
            "Semester : V\n" +
            "Year : 2023-2024\n" +
            "Course Type : Professional Core\n" +
            // Prerequisites noise
            "Pre-Requisites\n" +
            "Prerequisites : Data Structures\n" +
            "Pre-Reouisites : Basic Data Structures\n" +
            // Course Outcome section
            "COURSE OUTCOME\n" +
            "Col : llustrate the applications af advanced data structures\n" +
            "C02: Understand the ADT and use it to design algorithms for a given problem\n" +
            "C04: Choose the right algorithm to solve a given problem\n" +
            "COS: Assess the trade\n" +
            "off on the choice af data structures and algorithms for performance analysis.\n" +
            // Real curriculum
            "UNIT I - TREES\n" +
            "1. Binary Trees: Representations\n" +
            "2. Traversal\n" +
            "3. Properties\n" +
            "4. Threaded Binary Trees\n" +
            "5. AVL Tree: Balancing trees, node operations\n" +
            "6. Splay Trees\n" +
            "7. Red Black Tree\n" +
            "8. Trie Structures\n" +
            "9. B+ Trees\n" +
            "UNIT II - GRAPHS\n" +
            "1. Graph Representation\n" +
            "2. Breadth First Search\n" +
            "3. Depth First Search\n" +
            "4. Topological Sort\n" +
            "5. Shortest Path Algorithms\n" +
            "6. Spanning Trees\n" +
            "UNIT III - HEAPS AND HASHING\n" +
            "1. Heap data structures\n" +
            "2. Heap algorithms\n" +
            "3. Hashing methods\n" +
            "4. Collision resolution methods\n" +
            // Continuation/fragment noise
            "way search trees\n" +
            "trees\n" +
            "Definition\n" +
            "operations\n" +
            "applications\n" +
            // Tail (must be fully dropped)
            "Reference Books\n" +
            "1. Thomas H. Cormen, Introduction to Algorithms\n" +
            "2. Robert Lafore, Data Structures and Algorithms in Java\n" +
            "Question Paper Pattern\n" +
            "Unit CO Bloom's Level Marks\n" +
            "1 C01 BT1 8\n" +
            "2 C02 BT3 10\n" +
            "Marks: 100 Time: 3 Hours\n";

    // Noise tokens that MUST NOT appear anywhere in DB or RAG chunks
    private static final Set<String> NOISE_TOKENS = new HashSet<>(Arrays.asList(
            "20mss22", "20mss33", "ltpc", "l t p c", "3 0 0 3",
            "contact hours",
            "pre-requisites", "prerequisites", "pre-reouisites",
            "course outcome",
            "illustrate the applications", "understand the adt",
            "choose the right algorithm", "assess the trade",
            "off on the choice",
            "reference books", "question paper pattern",
            "way search trees"
    ));

    // Topics that MUST be present (from the real curriculum)
    private static final Set<String> MUST_HAVE_TOPICS = new HashSet<>(Arrays.asList(
            "binary trees: representations",
            "threaded binary trees",
            "avl tree: balancing trees, node operations",
            "splay trees",
            "red black tree",
            "trie structures",
            "b+ trees",
            "graph representation",
            "breadth first search",
            "depth first search",
            "topological sort",
            "shortest path algorithms",
            "spanning trees",
            "heap data structures",
            "heap algorithms",
            "hashing methods",
            "collision resolution methods"
    ));

    private AppDatabase db;
    private RagService ragService;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        ragService = new RagService(context, db);
    }

    @After
    public void tearDown() {
        if (db != null && db.isOpen()) db.close();
    }

    @Test
    public void phase3_realSyllabus_realDb_realRag() {

        Log.i(TAG, "=== PHASE 3 START ===");

        // ------------------------------------------------------------------
        // STEP 1: Parse with production SyllabusTextStructurer
        // ------------------------------------------------------------------
        SyllabusTextStructurer structurer = new SyllabusTextStructurer();
        List<SyllabusTextStructurer.ParsedSubject> parsed = structurer.parse(ADS_SYLLABUS_OCR);

        assertFalse("Parser must return at least one subject", parsed.isEmpty());

        int parserUnits  = 0;
        int parserTopics = 0;
        for (SyllabusTextStructurer.ParsedSubject s : parsed) {
            parserUnits += s.units.size();
            for (SyllabusTextStructurer.ParsedUnit u : s.units) {
                parserTopics += u.topics.size();
            }
        }

        Log.i(TAG, String.format("Parser: %d subjects, %d units, %d topics",
                parsed.size(), parserUnits, parserTopics));

        assertEquals("Expected exactly 3 curriculum units", 3, parserUnits);
        assertTrue("Expected >= 17 clean topics, got " + parserTopics, parserTopics >= 17);

        // ------------------------------------------------------------------
        // STEP 2: Persist into Room DB via the exact production DAO path
        // ------------------------------------------------------------------
        SubjectDao subjectDao = db.subjectDao();

        long studentId = db.studentDao().insert(
                new Student("Phase3", "ADS", "V", 120));

        for (SyllabusTextStructurer.ParsedSubject ps : parsed) {
            Subject subject = new Subject(studentId, ps.name, 2, 2, System.currentTimeMillis());
            long subId = subjectDao.insertSubject(subject);

            int unitOrder = 1;
            for (SyllabusTextStructurer.ParsedUnit pu : ps.units) {
                Unit unit = new Unit(subId, pu.name, unitOrder++);
                long unitId = subjectDao.insertUnit(unit);

                int topicOrder = 1;
                for (String topicName : pu.topics) {
                    Topic topic = new Topic(unitId, subId, topicName, 2, 30, topicOrder++);
                    subjectDao.insertTopic(topic);
                }
            }
        }

        // ------------------------------------------------------------------
        // STEP 3: Read back via the exact same production DAO path
        // ------------------------------------------------------------------
        List<SubjectWithUnitsAndTopics> trees =
                subjectDao.getAllSubjectsWithUnitsAndTopicsSync(studentId);

        assertFalse("DB must have at least one subject", trees.isEmpty());

        int dbSubjects = trees.size();
        int dbUnits    = 0;
        int dbTopics   = 0;

        for (SubjectWithUnitsAndTopics swt : trees) {
            assertNotNull("Subject must not be null", swt.subject);
            assertNotNull("Units list must not be null", swt.units);
            dbUnits += swt.units.size();

            for (UnitWithTopics uwt : swt.units) {
                assertNotNull("Unit must not be null", uwt.unit);
                assertNotNull("Topics list must not be null", uwt.topics);
                dbTopics += uwt.topics.size();

                for (Topic t : uwt.topics) {
                    String lower = t.name.toLowerCase();

                    // Must not be a CO code.
                    // A real CO code is: c + o/0 + ONE digit-or-OCR-digit (l,I,s,z,5)
                    // followed immediately by a delimiter (:.,;-) or whitespace or end.
                    // This deliberately does NOT match "collision" (next char after 'l' is 'l').
                    assertFalse("CO code variant in DB as topic: '" + t.name + "'",
                            lower.matches("^c[o0][0-9ils5z](\\s*[:.,;\\-]|\\s|$).*"));

                    // Must not match any noise token
                    for (String noise : NOISE_TOKENS) {
                        assertFalse(
                                "NOISE topic in DB [unit=" + uwt.unit.name + "]: '"
                                        + t.name + "' matched noise='" + noise + "'",
                                lower.equals(noise) || lower.startsWith(noise + " ")
                                        || lower.startsWith(noise + ":")
                        );
                    }
                }
            }
        }

        Log.i(TAG, String.format("DB read-back: %d subjects, %d units, %d topics",
                dbSubjects, dbUnits, dbTopics));

        assertEquals("DB unit count must match parser", parserUnits, dbUnits);
        assertEquals("DB topic count must match parser", parserTopics, dbTopics);

        // ------------------------------------------------------------------
        // STEP 4: Index via RagService.indexSyllabus()
        // ------------------------------------------------------------------
        SubjectWithUnitsAndTopics primaryTree = trees.get(0);
        int ragChunkCount = ragService.indexSyllabus(primaryTree);

        Log.i(TAG, "RAG indexed: " + ragChunkCount + " chunks");

        int expectedChunks = 0;
        for (UnitWithTopics uwt : primaryTree.units) {
            expectedChunks += uwt.topics.size();
        }
        assertEquals("RAG chunk count must equal topic count", expectedChunks, ragChunkCount);
        assertTrue("RAG index must be non-empty", ragService.isIndexed());

        // ------------------------------------------------------------------
        // STEP 5: Real RAG search — 5 representative queries
        // ------------------------------------------------------------------
        String[][] searchTests = {
                {"tree balancing and AVL rotations",    "avl tree"},
                {"graph traversal techniques BFS DFS", "breadth first search"},
                {"hashing collision resolution",        "collision resolution"},
                {"heap data structure algorithms",      "heap"},
                {"red black trie splay trees",          "splay trees"}
        };

        StringBuilder report = new StringBuilder();
        report.append("\n=== PHASE 3 RAG SEARCH RESULTS ===\n");

        for (String[] st : searchTests) {
            String query     = st[0];
            String expectKey = st[1];

            List<SearchHit> hits = ragService.search(query, 3);

            assertNotNull("Search must return non-null for: " + query, hits);
            assertFalse("Search must return >= 1 result for: " + query, hits.isEmpty());

            SearchHit top = hits.get(0);
            assertNotNull("Top hit metadata must not be null", top.metadata);
            assertNotNull("Top hit text must not be null", top.metadata.text);

            // Noise must not appear in any returned chunk
            for (SearchHit h : hits) {
                String chunkLower = h.metadata.text.toLowerCase();
                for (String noise : NOISE_TOKENS) {
                    assertFalse(
                            "NOISE in RAG chunk! query='" + query + "' noise='" + noise
                                    + "'\nChunk:\n" + h.metadata.text,
                            chunkLower.contains(noise)
                    );
                }
                // CO code must not appear as a Topic: line
                // (same narrow pattern: c+o/0+OCR-digit+delimiter, not arbitrary letters)
                assertFalse(
                        "CO code in RAG chunk topic line: " + h.metadata.text,
                        h.metadata.text.toLowerCase().matches(
                                "(?s).*\ntopic: c[o0][0-9ils5z](\\s*[:.,;\\-]|\\s|$).*")
                );
            }

            boolean topRelevant = top.metadata.text.toLowerCase().contains(expectKey);
            report.append(String.format("\nQ: '%s'\n", query));
            report.append(String.format("  score=%.4f  text=%s\n",
                    top.score, top.metadata.text.replace("\n", " | ")));
            report.append(String.format("  relevant (contains '%s'): %s\n",
                    expectKey, topRelevant ? "YES" : "NO (semantic fallback ok)"));
        }

        // ------------------------------------------------------------------
        // STEP 6: All must-have topics must be in the DB
        // ------------------------------------------------------------------
        Set<String> foundTopics = new HashSet<>();
        for (SubjectWithUnitsAndTopics swt : trees) {
            for (UnitWithTopics uwt : swt.units) {
                for (Topic t : uwt.topics) {
                    foundTopics.add(t.name.toLowerCase());
                }
            }
        }

        StringBuilder missing = new StringBuilder();
        for (String must : MUST_HAVE_TOPICS) {
            if (!foundTopics.contains(must)) {
                missing.append("  MISSING: '").append(must).append("'\n");
            }
        }

        // Print full unit -> topic mapping
        report.append("\n=== DB UNIT -> TOPIC MAPPING ===\n");
        for (SubjectWithUnitsAndTopics swt : trees) {
            report.append("SUBJECT: ").append(swt.subject.name).append("\n");
            for (UnitWithTopics uwt : swt.units) {
                report.append("  UNIT: ").append(uwt.unit.name).append("\n");
                for (Topic t : uwt.topics) {
                    report.append("    TOPIC: ").append(t.name).append("\n");
                }
            }
        }

        report.append("\n=== PHASE 3 SUMMARY ===\n");
        report.append("  Subjects  : ").append(dbSubjects).append("\n");
        report.append("  Units     : ").append(dbUnits).append("\n");
        report.append("  Topics    : ").append(dbTopics).append("\n");
        report.append("  RAG chunks: ").append(ragChunkCount).append("\n");
        if (missing.length() > 0) {
            report.append("  MISSING must-have topics:\n").append(missing);
        } else {
            report.append("  All must-have topics: PRESENT\n");
        }

        Log.i(TAG, report.toString());

        // Final: no must-have topics may be missing
        assertTrue("Must-have topics missing from DB:\n" + missing, missing.length() == 0);

        Log.i(TAG, "=== PHASE 3 PASS ===");
    }
}
