package com.example.daily_learn.domain;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LocalExplanationSourceTest {

    private LocalExplanationSource source;

    @Before
    public void setUp() {
        source = new LocalExplanationSource();
    }

    @Test
    public void testWebTechnologyKeyConceptsDoesNotContainTimeComplexity() {
        String keyConcepts = source.getKeyConcepts("Introduction - Part 2", "Fundamentals Of Web Technology");
        assertNotNull(keyConcepts);
        assertFalse("FWT should not contain Time Complexity", keyConcepts.toLowerCase().contains("time complexity"));
        assertTrue("FWT should contain Web/Architecture concepts", 
                keyConcepts.contains("Client-Server Architecture") || keyConcepts.contains("Web"));

        String importantPoints = source.getImportantPoints("Introduction - Part 2", "Fundamentals Of Web Technology");
        assertNotNull(importantPoints);
        assertFalse("FWT important points should not talk about time complexity", 
                importantPoints.toLowerCase().contains("time complexity"));
        assertFalse("FWT important points should not mention data structures", 
                importantPoints.toLowerCase().contains("data structures"));
        assertTrue("FWT important points should mention client-side or web standards",
                importantPoints.contains("client-side") || importantPoints.contains("standards"));
    }

    @Test
    public void testHtmlTopicKeyConcepts() {
        String keyConcepts = source.getKeyConcepts("Introduction to HTML5: Basic Tags", "Fundamentals Of Web Technology");
        assertNotNull(keyConcepts);
        assertTrue("HTML topic should mention Semantic Tags or DOM", 
                keyConcepts.contains("Semantic Tags") || keyConcepts.contains("DOM"));
        assertFalse("HTML topic should not have Time Complexity", 
                keyConcepts.toLowerCase().contains("time complexity"));
    }

    @Test
    public void testDsaTopicStillHasComplexity() {
        String keyConcepts = source.getKeyConcepts("Binary Search Trees", "Data Structures");
        assertNotNull(keyConcepts);
        assertTrue("BST key concepts should contain tree terms", keyConcepts.contains("Root"));

        String importantPoints = source.getImportantPoints("Binary Search Trees", "Data Structures");
        assertTrue("DSA should mention time complexity", importantPoints.toLowerCase().contains("time complexity"));
    }

    @Test
    public void testGenericFallbackDoesNotContainTimeComplexity() {
        String keyConcepts = source.getKeyConcepts("Ethics and Cyber Laws", "General Studies");
        assertNotNull(keyConcepts);
        assertFalse("Generic fallback should not contain Time Complexity", 
                keyConcepts.toLowerCase().contains("time complexity"));
        assertTrue("Generic fallback should contain Core Principles or Concepts", 
                keyConcepts.contains("Core Principles") || keyConcepts.contains("Architecture"));

        String importantPoints = source.getImportantPoints("Ethics and Cyber Laws", "General Studies");
        assertFalse("Generic fallback should not contain time complexity", 
                importantPoints.toLowerCase().contains("time complexity"));
    }
}
