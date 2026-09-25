package com.example.daily_learn.domain.scheduler;

import com.example.daily_learn.data.local.entity.StudySession;
import com.example.daily_learn.data.local.entity.Topic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure-Java revision planner — no Android imports.
 *
 * Builds revision sessions for the final N days before the exam, prioritizing:
 *  1. Topics with needsRevision = true
 *  2. Topics with low quiz scores (passed in as a list of topicId → avgScore)
 *  3. Topics not recently reviewed (oldest lastReviewedDate first)
 *  4. Topics with highest difficulty
 */
public class RevisionPlanner {

    /**
     * @param planId        StudyPlan ID
     * @param allTopics     ALL completed topics (candidates for revision)
     * @param revisionStart Epoch millis of first revision day
     * @param revisionDays  Number of revision days
     * @param dailyMinutes  Student's daily study budget
     * @param quizScores    Parallel list: entry i = average score% for allTopics.get(i), or -1 if no quiz taken
     * @return              List of revision StudySessions
     */
    public List<StudySession> planRevision(long planId,
                                            List<Topic> allTopics,
                                            long revisionStart,
                                            int revisionDays,
                                            int dailyMinutes,
                                            List<Float> quizScores) {
        // Score each topic for revision priority (lower = higher priority)
        List<TopicScore> scored = new ArrayList<>();
        for (int i = 0; i < allTopics.size(); i++) {
            Topic t = allTopics.get(i);
            float score = computePriorityScore(t,
                (quizScores != null && i < quizScores.size()) ? quizScores.get(i) : -1f);
            scored.add(new TopicScore(t, score));
        }

        // Sort ascending (lowest score = highest revision priority)
        Collections.sort(scored, new Comparator<TopicScore>() {
            @Override
            public int compare(TopicScore a, TopicScore b) {
                return Float.compare(a.score, b.score);
            }
        });

        List<StudySession> sessions = new ArrayList<>();
        int dayOffset = 0;
        int minutesUsed = 0;
        int topicIdx = 0;

        while (dayOffset < revisionDays && topicIdx < scored.size()) {
            Topic t = scored.get(topicIdx).topic;
            long revDate = revisionStart + (long) dayOffset * 86_400_000L;

            sessions.add(new StudySession(planId, t.id, revDate, true));
            minutesUsed += t.estimatedMinutes;
            topicIdx++;

            if (minutesUsed >= dailyMinutes) {
                dayOffset++;
                minutesUsed = 0;
            }
        }

        return sessions;
    }

    /**
     * Priority score — lower = more urgent to revise.
     * Combines: needsRevision flag, quiz score, lastReviewedDate, difficulty.
     */
    private float computePriorityScore(Topic t, float quizScore) {
        float score = 0f;

        // needsRevision flag: big negative (urgent)
        if (t.needsRevision) score -= 100f;

        // Low quiz score: subtract up to 80 points
        if (quizScore >= 0) {
            score -= (100f - quizScore) * 0.8f;
        } else {
            // No quiz taken — treat as medium priority loss
            score -= 40f;
        }

        // Oldest last reviewed = higher priority (smaller score)
        if (t.lastReviewedDate > 0) {
            long daysSinceReview = (System.currentTimeMillis() - t.lastReviewedDate) / 86_400_000L;
            score -= Math.min(daysSinceReview * 2f, 50f); // cap at 50
        } else {
            score -= 50f; // never reviewed
        }

        // Higher difficulty = more important to revise
        score -= t.difficulty * 10f;

        return score;
    }

    private static class TopicScore {
        Topic topic;
        float score;
        TopicScore(Topic t, float s) { topic = t; score = s; }
    }
}
