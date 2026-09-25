package com.example.daily_learn.domain;

/**
 * Phase 5 readiness interface — allows swapping local static content
 * for an AI-backed explanation source without rewriting the UI/ViewModel.
 */
public interface ExplanationSource {

    /** Short explanation paragraph for a topic */
    String getExplanation(String topicName);

    /** Comma-separated key concepts */
    String getKeyConcepts(String topicName);

    /** One worked example */
    String getExample(String topicName);

    /** Bullet-point important notes */
    String getImportantPoints(String topicName);

    /** A practice question (not quiz MCQ) */
    String getPracticeQuestion(String topicName);

    // Subject/domain-aware overloads
    default String getExplanation(String topicName, String subjectName) {
        return getExplanation(topicName);
    }

    default String getKeyConcepts(String topicName, String subjectName) {
        return getKeyConcepts(topicName);
    }

    default String getExample(String topicName, String subjectName) {
        return getExample(topicName);
    }

    default String getImportantPoints(String topicName, String subjectName) {
        return getImportantPoints(topicName);
    }

    default String getPracticeQuestion(String topicName, String subjectName) {
        return getPracticeQuestion(topicName);
    }
}
