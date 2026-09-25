package com.example.daily_learn.domain;

import com.example.daily_learn.data.local.entity.StudySession;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.domain.ai.AiTutorEngine;
import com.example.daily_learn.domain.scheduler.AdaptiveRescheduler;
import com.example.daily_learn.domain.scheduler.RevisionPlanner;
import com.example.daily_learn.domain.scheduler.StudyPlanGenerator;
import com.example.daily_learn.domain.parser.SyllabusTextStructurer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SchedulerAndParserTest {

    @Test
    public void testStudyPlanGenerator() {
        StudyPlanGenerator generator = new StudyPlanGenerator();

        List<Topic> topics = new ArrayList<>();
        // 5 topics across 2 subjects
        topics.add(new Topic(1L, 10L, "Topic 1", 3, 60, 1));
        topics.add(new Topic(1L, 10L, "Topic 2", 2, 45, 2));
        topics.add(new Topic(2L, 20L, "Topic 3", 1, 30, 1));
        topics.add(new Topic(2L, 20L, "Topic 4", 3, 60, 2));
        topics.add(new Topic(1L, 10L, "Topic 5", 2, 40, 3));

        for (int i = 0; i < topics.size(); i++) {
            topics.get(i).id = i + 1;
        }

        long today = StudyPlanGenerator.todayMidnightMillis();
        long examDate = today + (30L * 86_400_000L); // 30 days

        List<StudySession> sessions = generator.generate(100L, topics, examDate, 120);

        assertNotNull(sessions);
        assertFalse(sessions.isEmpty());

        // Verify learning and revision sessions exist
        boolean hasLearning = false;
        boolean hasRevision = false;
        for (StudySession s : sessions) {
            assertEquals(100L, s.studyPlanId);
            if (s.isRevision) {
                hasRevision = true;
            } else {
                hasLearning = true;
            }
        }
        assertTrue("Should have learning sessions", hasLearning);
        assertTrue("Should have revision sessions", hasRevision);
    }

    @Test
    public void testAdaptiveRescheduler() {
        AdaptiveRescheduler rescheduler = new AdaptiveRescheduler();

        long today = StudyPlanGenerator.todayMidnightMillis();
        long yesterday = today - 86_400_000L;
        long tomorrow = today + 86_400_000L;

        List<StudySession> sessions = new ArrayList<>();
        StudySession s1 = new StudySession(1L, 1L, yesterday, false);
        s1.status = StudySession.STATUS_SCHEDULED; // Missed yesterday

        StudySession s2 = new StudySession(1L, 2L, today, false);
        s2.status = StudySession.STATUS_SCHEDULED;

        StudySession s3 = new StudySession(1L, 3L, tomorrow, false);
        s3.status = StudySession.STATUS_SCHEDULED;

        sessions.add(s1);
        sessions.add(s2);
        sessions.add(s3);

        List<Topic> topics = new ArrayList<>();
        Topic t1 = new Topic(1L, 10L, "T1", 2, 45, 1); t1.id = 1L;
        Topic t2 = new Topic(1L, 10L, "T2", 2, 45, 1); t2.id = 2L;
        Topic t3 = new Topic(1L, 10L, "T3", 2, 45, 1); t3.id = 3L;
        topics.add(t1); topics.add(t2); topics.add(t3);

        AdaptiveRescheduler.RescheduleResult result = rescheduler.reschedule(sessions, today, 120, topics);

        assertEquals("Should detect 1 missed session", 1, result.missedCount);
        assertEquals(StudySession.STATUS_MISSED, s1.status);
    }

    @Test
    public void testRevisionPlanner() {
        RevisionPlanner planner = new RevisionPlanner();

        List<Topic> completedTopics = new ArrayList<>();
        Topic t1 = new Topic(1L, 10L, "Easy Topic", 1, 30, 1); t1.id = 1L;
        Topic t2 = new Topic(1L, 10L, "Hard Topic", 3, 60, 2); t2.id = 2L;
        Topic t3 = new Topic(1L, 10L, "Topic Needs Revision", 2, 45, 3); t3.id = 3L;
        t3.needsRevision = true;

        completedTopics.add(t1);
        completedTopics.add(t2);
        completedTopics.add(t3);

        long revisionStart = System.currentTimeMillis();
        List<Float> quizScores = new ArrayList<>();
        quizScores.add(90f);
        quizScores.add(40f);
        quizScores.add(60f);

        List<StudySession> revSessions = planner.planRevision(1L, completedTopics, revisionStart, 5, 120, quizScores);

        assertNotNull(revSessions);
        assertFalse(revSessions.isEmpty());
        for (StudySession s : revSessions) {
            assertTrue(s.isRevision);
        }
    }

    @Test
    public void testSyllabusTextStructurer() {
        SyllabusTextStructurer structurer = new SyllabusTextStructurer();

        String rawSyllabus = "DATA STRUCTURES\n" +
                "UNIT I – INTRODUCTION\n" +
                "1. Introduction to Data Structures\n" +
                "2. Algorithm Analysis\n" +
                "3. Arrays\n" +
                "4. Linked Lists\n\n" +
                "UNIT II – STACKS AND QUEUES\n" +
                "1. Stack\n" +
                "2. Applications of Stack\n" +
                "3. Queue\n" +
                "4. Circular Queue";

        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(rawSyllabus);

        assertNotNull(subjects);
        assertFalse(subjects.isEmpty());

        SyllabusTextStructurer.ParsedSubject ds = subjects.get(0);
        assertEquals("Data Structures", ds.name);
        assertEquals(2, ds.units.size());
        assertEquals(4, ds.units.get(0).topics.size());
        assertEquals("Introduction to Data Structures", ds.units.get(0).topics.get(0));
    }

    @Test
    public void testAiTutorEngine() {
        // AiTutorEngine now uses LocalLLMEngine + RagService (RAG-grounded). With no
        // engine/rag injected it must handle the offline case gracefully rather than
        // fabricating rule-based answers.
        AiTutorEngine tutor = new AiTutorEngine(null, null);

        String emptyQuery = tutor.generateResponse("AVL Trees", "Data Structures", "");
        assertNotNull(emptyQuery);

        String offlineResp = tutor.generateResponse("Binary Search", "Data Structures", "Explain binary search");
        assertNotNull(offlineResp);
    }
}
