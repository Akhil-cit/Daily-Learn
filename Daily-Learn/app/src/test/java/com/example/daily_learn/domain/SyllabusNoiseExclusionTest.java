package com.example.daily_learn.domain;

import com.example.daily_learn.domain.ai.DailyTopicGroup;
import com.example.daily_learn.domain.ai.SyllabusAnalysisResult;
import com.example.daily_learn.domain.ai.SyllabusAnalyzer;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Ensures that metadata, course-outcome (CO) lists, prerequisites, and exam-lens
 * text never leak into study-plan topics. Only real syllabus content units may
 * become topics.
 */
public class SyllabusNoiseExclusionTest {

    private SyllabusAnalyzer analyzer;

    @Before
    public void setUp() {
        analyzer = new SyllabusAnalyzer(null);
    }

    private static final String SYLLABUS_WITH_CO_PRECOND_EXAM =
            "25SS212 / OBJECT ORIENTED PROGRAMMING\n" +
            "Semester II Year December 2025 - May 2026  L T P C\n" +
            "Course Type: Professional Core   4 0 0 4\n" +
            "Prerequisite : CS8201 Problem Solving and Programming in C\n" +
            "COURSE OBJECTIVE\n" +
            "1. Understand object oriented programming paradigms.\n" +
            "\n" +
            "COURSE OUTCOME\n" +
            "CO1: Understand the features of C++ and its applications (BT2 Understand)\n" +
            "CO2: Apply classes and objects to model real world problems (BT3 Apply)\n" +
            "CO3: Analyze inheritance and polymorphism constructs (BT4 Analyze)\n" +
            "\n" +
            "At the end of this course, students will be able to:\n" +
            "1. write classes and work with objects\n" +
            "2. differentiate static and dynamic polymorphism\n" +
            "\n" +
            "iv) Topic/Unit/Chapter Layout\n" +
            "Topic Lecture Hours\n" +
            "PRINCIPLES OF OBJECT ORIENTED PROGRAMMING\n" +
            "Software crisis, Software Evolution - Object Oriented Programming Paradigm - Basic\n" +
            "Concepts and Benefits of OOP - Structure of C++ 3\n" +
            "FUNCTIONS IN C++\n" +
            "Function Prototyping - Call by Reference - Inline functions - Function Overloading -\n" +
            "Friend Functions 5\n" +
            "INHERITANCE AND POLYMORPHISM\n" +
            "Inheritance: Introduction and benefits - Types of Inheritance - Virtual base class.\n" +
            "Polymorphism: Operator Overloading - Virtual functions - Pure virtual function. 5\n" +
            "\n" +
            "Question Paper Pattern (Exam Lens)\n" +
            "Unit CO Bloom's Level Marks\n" +
            "1 CO1 Remember 8\n" +
            "2 CO2 Understand 10\n" +
            "Marks: 100  Time: 3 Hours\n" +
            "\n" +
            "v) Evaluation Scheme\n" +
            "Sessional Exam 1 60\n" +
            "Sessional Exam 2 60\n" +
            "Internal Marks 40\n" +
            "External Exam 100\n";

    private List<String> allTopics(SyllabusAnalysisResult result) {
        List<String> topics = new ArrayList<>();
        for (DailyTopicGroup day : result.days) {
            for (String t : day.topics) {
                topics.add(t.toLowerCase());
            }
        }
        return topics;
    }

    private boolean anyContains(List<String> topics, String needle) {
        for (String t : topics) {
            if (t.contains(needle)) return true;
        }
        return false;
    }

    private boolean anyEquals(String prefix, List<String> topics) {
        for (String t : topics) {
            if (t.startsWith(prefix)) return true;
        }
        return false;
    }

    @Test
    public void testMetadataPrereqCoExamLensDoNotBecomeTopics() {
        SyllabusAnalysisResult result = analyzer.processRawText(SYLLABUS_WITH_CO_PRECOND_EXAM, 20);
        List<String> topics = allTopics(result);

        // Real content must survive
        assertTrue("Software crisis must be a topic", anyContains(topics, "software crisis"));
        assertTrue("Inheritance must be a topic", anyContains(topics, "inheritance"));
        assertTrue("Polymorphism must be a topic", anyContains(topics, "polymorphism"));

        // Metadata / course-code / credits must NOT become topics
        assertFalse("CO1 must not be a topic", anyEquals("co1", topics));
        assertFalse("CO2 must not be a topic", anyEquals("co2", topics));
        assertFalse("CO3 must not be a topic", anyEquals("co3", topics));
        assertFalse("Bloom's level must not be a topic", anyContains(topics, "bloom"));
        assertFalse("BT2 must not be a topic", anyContains(topics, "bt2"));
        assertFalse("Course type must not be a topic", anyContains(topics, "course type"));
        assertFalse("Prerequisite must not be a topic", anyContains(topics, "prerequisite"));
        assertFalse("At the end of this course must not be a topic", anyContains(topics, "at the end of this course"));
        assertFalse("Students will be able to must not be a topic", anyContains(topics, "students will be able"));
        assertFalse("Marks must not be a topic", anyContains(topics, "marks"));
        assertFalse("Semester must not be a topic", anyContains(topics, "semester"));
        assertFalse("Time 3 Hours must not be a topic", anyContains(topics, "time: 3 hours"));
        assertFalse("L T P C must not be a topic", anyContains(topics, "l t p c"));
    }
}
