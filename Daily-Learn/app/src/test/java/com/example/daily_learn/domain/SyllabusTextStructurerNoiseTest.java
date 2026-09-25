package com.example.daily_learn.domain;

import com.example.daily_learn.domain.parser.SyllabusTextStructurer;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Ensures SyllabusTextStructurer (the persistence path that creates actual
 * study-plan topics) never turns metadata, course outcomes, prerequisites, or
 * exam-lens text into subjects/units/topics.
 */
public class SyllabusTextStructurerNoiseTest {

    private SyllabusTextStructurer structurer;

    @Before
    public void setUp() {
        structurer = new SyllabusTextStructurer();
    }

    private static final String SYLLABUS =
            "COURSE OUTCOME\n" +
            "CO1: Understand the features of C++ (BT2)\n" +
            "CO2: Apply classes and objects (BT3)\n" +
            "Prerequisite : CS8201 Programming in C\n" +
            "Course Type: Professional Core\n" +
            "Semester II L T P C\n" +
            "\n" +
            "UNIT I - PRINCIPLES OF OOP\n" +
            "1. Software crisis\n" +
            "2. Object Oriented Programming Paradigm\n" +
            "3. Basic Concepts and Benefits of OOP\n" +
            "\n" +
            "UNIT II - FUNCTIONS\n" +
            "1. Function Overloading\n" +
            "2. Inline functions\n" +
            "\n" +
            "Question Paper Pattern\n" +
            "Unit CO Bloom's Level Marks\n" +
            "1 CO1 Remember 8\n" +
            "Marks: 100 Time: 3 Hours\n" +
            "\n" +
            "Reference Books\n" +
            "1. Bjarne Stroustrup, C++ Programming\n";

    private List<String> allTopics(List<SyllabusTextStructurer.ParsedSubject> subjects) {
        List<String> topics = new ArrayList<>();
        for (SyllabusTextStructurer.ParsedSubject s : subjects) {
            for (SyllabusTextStructurer.ParsedUnit u : s.units) {
                topics.addAll(u.topics);
            }
        }
        return topics;
    }

    private List<String> allSubjectNames(List<SyllabusTextStructurer.ParsedSubject> subjects) {
        List<String> names = new ArrayList<>();
        for (SyllabusTextStructurer.ParsedSubject s : subjects) {
            names.add(s.name.toLowerCase());
        }
        return names;
    }

    private boolean anyContains(List<String> list, String needle) {
        for (String s : list) if (s.toLowerCase().contains(needle)) return true;
        return false;
    }

    @Test
    public void testMetadataAndOutcomesDoNotBecomeTopicsOrSubjects() {
        List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(SYLLABUS);
        assertNotNull(subjects);
        assertFalse("Should parse at least one subject", subjects.isEmpty());

        List<String> topics = allTopics(subjects);
        List<String> subjectNames = allSubjectNames(subjects);

        // Real content must survive
        assertTrue("Software crisis must be a topic", anyContains(topics, "software crisis"));
        assertTrue("Function overloading must be a topic", anyContains(topics, "function overloading"));
        assertTrue("Object oriented programming paradigm must be a topic", anyContains(topics, "object oriented programming paradigm"));

        // CO / Course Outcome must NOT leak (as subject or topic)
        assertFalse("Course Outcome must not be a subject name", anyContains(subjectNames, "course outcome"));
        assertFalse("CO1 must not be a topic", anyContains(topics, "co1"));
        assertFalse("CO2 must not be a topic", anyContains(topics, "co2"));
        assertFalse("Bloom's must not be a topic", anyContains(topics, "bloom"));

        // Metadata / prereq / course type / semester must NOT leak
        assertFalse("Prerequisite must not be a topic", anyContains(topics, "prerequisite"));
        assertFalse("Prerequisite must not be a subject", anyContains(subjectNames, "prerequisite"));
        assertFalse("Course type must not be a topic", anyContains(topics, "course type"));
        assertFalse("Semester must not be a topic", anyContains(topics, "semester"));
        assertFalse("Marks must not be a topic", anyContains(topics, "marks"));

        // Reference books must not leak
        assertFalse("Reference books must not be a topic", anyContains(topics, "reference books"));
        assertFalse("Bjarne must not be a topic", anyContains(topics, "bjarne"));
    }
}
