package com.example.daily_learn.domain;

import com.example.daily_learn.domain.parser.SyllabusTextStructurer;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Regression test using a realistic OCR-corrupted syllabus corpus that
 * matches the ACTUAL broken JSON output described in the bug report.
 *
 * This test uses the 20MSS33 Advanced Data Structures syllabus verbatim —
 * including ALL the OCR corruption variants that produced the broken topics:
 *   "20MSS22", "Col :", "C02:", "C04:", "COS:", "LTPc 3003",
 *   "Contact Hours Definition", "way search trees", "trees",
 *   "Definition", "operations", "applications", fragments, etc.
 *
 * MUST NOT add to topic/unit/subject names:
 *   course codes, CO lines, prerequisites, contact hours, LTPC, reference books,
 *   random fragments, course-header lines.
 *
 * MUST keep as topics:
 *   Binary Trees, Traversal methods, AVL Tree, Splay Trees, Graph Representation,
 *   BFS, DFS, Topological Sort, Red Black Tree, Trie Structures, B+ Trees,
 *   Heap data structures, Hashing methods, Collision resolution, etc.
 */
public class SyllabusRealSyllabusRegressionTest {

    private SyllabusTextStructurer structurer;

    @Before
    public void setUp() {
        structurer = new SyllabusTextStructurer();
    }

    /**
     * This string is the realistic OCR-extracted text from the 20MSS33
     * Advanced Data Structures syllabus, including all corruption variants
     * that were observed in the actual broken JSON output.
     */
    private static final String ADS_SYLLABUS_OCR =
            // ── Header / metadata noise (must all be filtered) ──────────────────
            "20MSS33\n" +
            "20mss33 - Advanced Data Structures\n" +
            "20mss33 -Advanced Data Structures - Part 1\n" +
            "20mss33 -Advanced Data Structures - Part 2\n" +
            "20mss33 -Advanced Data Structures - Part 3\n" +
            "20MSS22\n" +             // different code that appeared in broken output
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
            // ── Prerequisites noise (various OCR spellings) ──────────────────────
            "Pre-Requisites\n" +
            "Prerequisites : Data Structures\n" +
            "Pre-Reouisites : Basic Data Structures\n" +
            // ── Course Outcome section (must be filtered) ────────────────────────
            "COURSE OUTCOME\n" +
            "Col : llustrate the applications af advanced data structures\n" +     // C01 OCR as Col
            "C02: Understand the ADT and use it to design algorithms for a given problem\n" +
            "C04: Choose the right algorithm to solve a given problem\n" +
            "COS: Assess the trade\n" +        // CO5 OCR as COS
            "off on the choice af data structures and algorithms for performance analysis.\n" + // continuation fragment
            // ── Real curriculum starts here ──────────────────────────────────────
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
            // ── Fragments that appeared in broken output ─────────────────────────
            "way search trees\n" +
            "trees\n" +
            "Definition\n" +
            "operations\n" +
            "applications\n" +
            // ── Tail section (all must be dropped) ──────────────────────────────
            "Reference Books\n" +
            "1. Thomas H. Cormen, Introduction to Algorithms\n" +
            "2. Robert Lafore, Data Structures and Algorithms in Java\n" +
            "Question Paper Pattern\n" +
            "Unit CO Bloom's Level Marks\n" +
            "1 C01 BT1 8\n" +
            "2 C02 BT3 10\n" +
            "Marks: 100 Time: 3 Hours\n";

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private List<String> allSubjectNames(List<SyllabusTextStructurer.ParsedSubject> subjects) {
        List<String> names = new ArrayList<>();
        for (SyllabusTextStructurer.ParsedSubject s : subjects) {
            names.add(s.name.toLowerCase());
        }
        return names;
    }

    private List<String> allUnitNames(List<SyllabusTextStructurer.ParsedSubject> subjects) {
        List<String> names = new ArrayList<>();
        for (SyllabusTextStructurer.ParsedSubject s : subjects) {
            for (SyllabusTextStructurer.ParsedUnit u : s.units) {
                names.add(u.name.toLowerCase());
            }
        }
        return names;
    }

    private List<String> allTopics(List<SyllabusTextStructurer.ParsedSubject> subjects) {
        List<String> topics = new ArrayList<>();
        for (SyllabusTextStructurer.ParsedSubject s : subjects) {
            for (SyllabusTextStructurer.ParsedUnit u : s.units) {
                for (String t : u.topics) {
                    topics.add(t.toLowerCase());
                }
            }
        }
        return topics;
    }

    private boolean anyContains(List<String> list, String needle) {
        String n = needle.toLowerCase();
        for (String s : list) if (s.contains(n)) return true;
        return false;
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    /**
     * Phase 2 verification: the parser must produce subjects/units/topics.
     */
    @Test
    public void testParserProducesContent() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        assertNotNull(subjects);
        assertFalse("Parser must produce at least one subject", subjects.isEmpty());

        List<String> topics = allTopics(subjects);
        assertFalse("Parser must produce at least one topic", topics.isEmpty());
    }

    /**
     * CRITICAL: Course codes must NEVER appear in any name.
     */
    @Test
    public void testCourseCodesNeverAppearInNames() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        List<String> subjectNames = allSubjectNames(subjects);
        List<String> unitNames    = allUnitNames(subjects);
        List<String> topics       = allTopics(subjects);

        List<String> invalidCodes = Arrays.asList(
                "20mss33", "20mss22", "mss33", "mss22"
        );

        for (String code : invalidCodes) {
            assertFalse("Course code [" + code + "] must not be a subject name",
                    anyContains(subjectNames, code));
            assertFalse("Course code [" + code + "] must not be a unit name",
                    anyContains(unitNames, code));
            assertFalse("Course code [" + code + "] must not be a topic",
                    anyContains(topics, code));
        }
    }

    /**
     * CRITICAL: CO/outcome lines and all OCR variants must never appear.
     */
    @Test
    public void testCoLinesNeverAppearInTopics() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        List<String> topics = allTopics(subjects);

        // Direct CO code variants — must not appear as STANDALONE topic start.
        // A real CO code is C + O/0 + digit-or-OCR-digit (l, I, S, Z, 5) + optional extra digits + delimiter.
        // Pattern is intentionally narrow: only the third character being a digit-like character is accepted.
        // This prevents false positives on legitimate topics like "collision resolution methods".
        java.util.regex.Pattern coPattern = java.util.regex.Pattern.compile(
                // c + o/0 + one digit-or-OCR-digit-substitute + optional digit(s) + delimiter
                "^c[o0][0-9ilsz5](\\d*)\\s*([:.,;\\-]|\\s|$).*",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        for (String topic : topics) {
            // Only flag if the whole topic starts with the CO token
            if (coPattern.matcher(topic.trim()).matches()) {
                fail("CO code variant leaked as topic: " + topic);
            }
        }

        // Outcome prose that was inside the CO section
        assertFalse("'illustrate the applications' must not be a topic",
                anyContains(topics, "illustrate the applications"));
        assertFalse("'understand the adt' must not be a topic",
                anyContains(topics, "understand the adt"));
        assertFalse("'choose the right algorithm' must not be a topic",
                anyContains(topics, "choose the right algorithm"));
        assertFalse("'assess the trade' must not be a topic",
                anyContains(topics, "assess the trade"));
    }

    /**
     * CRITICAL: Prerequisites must never appear in any name.
     */
    @Test
    public void testPrerequisitesNeverAppear() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        List<String> subjectNames = allSubjectNames(subjects);
        List<String> topics       = allTopics(subjects);

        assertFalse("'prerequisite' must not be a subject", anyContains(subjectNames, "prerequisite"));
        assertFalse("'pre-requisite' must not be a subject", anyContains(subjectNames, "pre-requisite"));
        assertFalse("'prerequisite' must not be a topic", anyContains(topics, "prerequisite"));
        assertFalse("'pre-requisite' must not be a topic", anyContains(topics, "pre-requisite"));
        assertFalse("'prereouisite' must not be a topic", anyContains(topics, "prereouisite"));
    }

    /**
     * CRITICAL: LTPC / credit metadata must never appear.
     */
    @Test
    public void testLtpcNeverAppears() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        List<String> topics = allTopics(subjects);

        assertFalse("'ltpc' must not be a topic", anyContains(topics, "ltpc"));
        assertFalse("'3003' must not be a topic", anyContains(topics, "3003"));
        assertFalse("'l t p c' must not be a topic", anyContains(topics, "l t p c"));
        assertFalse("'3 0 0 3' must not be a topic", anyContains(topics, "3 0 0 3"));
    }

    /**
     * CRITICAL: Contact Hours must never appear.
     */
    @Test
    public void testContactHoursNeverAppears() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        List<String> subjectNames = allSubjectNames(subjects);
        List<String> topics       = allTopics(subjects);

        assertFalse("'contact hours' must not be a subject", anyContains(subjectNames, "contact hours"));
        assertFalse("'contact hours' must not be a topic", anyContains(topics, "contact hours"));
        assertFalse("'contact hours definition' must not be a topic",
                anyContains(topics, "contact hours definition"));
    }

    /**
     * CRITICAL: Reference books section must never appear.
     */
    @Test
    public void testReferenceBooksNeverAppear() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        List<String> topics = allTopics(subjects);

        assertFalse("'reference books' must not be a topic", anyContains(topics, "reference books"));
        assertFalse("'thomas h. cormen' must not be a topic", anyContains(topics, "cormen"));
        assertFalse("'robert lafore' must not be a topic", anyContains(topics, "lafore"));
    }

    /**
     * CRITICAL: OCR-produced continuation fragments must never be topics.
     */
    @Test
    public void testFragmentsNeverAppear() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        List<String> topics = allTopics(subjects);

        assertFalse("Fragment 'off on the choice' must not be a topic",
                anyContains(topics, "off on the choice"));
        // Standalone noise words
        for (String frag : Arrays.asList("way search trees", "definition", "operations",
                "applications", "types")) {
            boolean found = false;
            for (String t : topics) {
                if (t.equals(frag)) { found = true; break; }
            }
            assertFalse("Standalone fragment [" + frag + "] must not be a topic", found);
        }
    }

    /**
     * CRITICAL: Exam-lens lines must never appear.
     */
    @Test
    public void testExamLensNeverAppears() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        List<String> topics = allTopics(subjects);

        assertFalse("'bloom' must not be a topic", anyContains(topics, "bloom"));
        assertFalse("'marks' must not be a topic", anyContains(topics, "marks: 100"));
        assertFalse("'question paper' must not be a topic", anyContains(topics, "question paper"));
    }

    /**
     * POSITIVE: Real syllabus content must survive the filter.
     * These are the genuine topics from the 20MSS33 curriculum.
     */
    @Test
    public void testRealTopicsSurvive() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);
        List<String> topics = allTopics(subjects);

        // Trees unit
        assertTrue("'binary trees' must be a topic", anyContains(topics, "binary trees"));
        assertTrue("'threaded binary trees' must be a topic", anyContains(topics, "threaded binary trees"));
        assertTrue("'avl tree' must be a topic", anyContains(topics, "avl tree"));
        assertTrue("'splay trees' must be a topic", anyContains(topics, "splay trees"));
        assertTrue("'red black tree' must be a topic", anyContains(topics, "red black tree"));
        assertTrue("'trie structures' must be a topic", anyContains(topics, "trie structures"));
        assertTrue("'b+ trees' or 'b trees' must be a topic", anyContains(topics, "b+") || anyContains(topics, "b tree"));

        // Graphs unit
        assertTrue("'graph representation' must be a topic", anyContains(topics, "graph representation"));
        assertTrue("'breadth first search' must be a topic", anyContains(topics, "breadth first search"));
        assertTrue("'depth first search' must be a topic", anyContains(topics, "depth first search"));
        assertTrue("'topological sort' must be a topic", anyContains(topics, "topological sort"));

        // Heaps & Hashing unit
        assertTrue("'heap' must be a topic", anyContains(topics, "heap"));
        assertTrue("'hashing' must be a topic", anyContains(topics, "hashing"));
        assertTrue("'collision resolution' must be a topic", anyContains(topics, "collision resolution"));
    }

    /**
     * POSITIVE: Unit structure must be correct — Trees topics are in Trees unit,
     * not Graphs, not Heaps.
     */
    @Test
    public void testUnitAssignmentIsCorrect() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);

        // Find the Trees unit and ensure it contains tree topics
        boolean avlInTreesUnit = false;
        boolean bfsInGraphsUnit = false;
        boolean hashingInHeapsUnit = false;

        for (SyllabusTextStructurer.ParsedSubject s : subjects) {
            for (SyllabusTextStructurer.ParsedUnit u : s.units) {
                String unitLower = u.name.toLowerCase();
                for (String t : u.topics) {
                    String tl = t.toLowerCase();
                    if (unitLower.contains("tree") || unitLower.contains("unit i")) {
                        if (tl.contains("avl")) avlInTreesUnit = true;
                    }
                    if (unitLower.contains("graph") || unitLower.contains("unit ii")) {
                        if (tl.contains("breadth first") || tl.contains("bfs")) bfsInGraphsUnit = true;
                    }
                    if (unitLower.contains("heap") || unitLower.contains("hash") || unitLower.contains("unit iii")) {
                        if (tl.contains("hash")) hashingInHeapsUnit = true;
                    }
                }
            }
        }

        assertTrue("AVL Tree should be in the Trees unit (Unit I)", avlInTreesUnit);
        assertTrue("BFS should be in the Graphs unit (Unit II)", bfsInGraphsUnit);
        assertTrue("Hashing should be in the Heaps/Hashing unit (Unit III)", hashingInHeapsUnit);
    }

    /**
     * Print the parsed structure for visual Phase 2 inspection (always passes).
     * In a CI environment, this output can be checked in the test log.
     */
    @Test
    public void testPrintParsedStructure() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(ADS_SYLLABUS_OCR);

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== PHASE 2: PARSED STRUCTURE ===\n");
        for (SyllabusTextStructurer.ParsedSubject s : subjects) {
            sb.append("SUBJECT: ").append(s.name).append("\n");
            for (SyllabusTextStructurer.ParsedUnit u : s.units) {
                sb.append("  UNIT: ").append(u.name).append("\n");
                for (String t : u.topics) {
                    sb.append("    TOPIC: ").append(t).append("\n");
                }
            }
        }
        sb.append("=================================\n");
        System.out.println(sb.toString());
        // Always passes — output is examined in the log
        assertTrue(true);
    }
}
