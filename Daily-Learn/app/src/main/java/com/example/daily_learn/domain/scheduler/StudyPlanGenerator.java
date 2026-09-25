package com.example.daily_learn.domain.scheduler;

import com.example.daily_learn.data.local.entity.StudySession;
import com.example.daily_learn.data.local.entity.Topic;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure-Java scheduling algorithm — no Android imports.
 *
 * Algorithm (per spec §5):
 *  1. availableDays  = examDate − today
 *  2. revisionDays   = max(3, floor(availableDays × 0.18))
 *  3. learningDays   = availableDays − revisionDays
 *  4. Assign topics to days weighted by difficulty × estimatedMinutes,
 *     respecting dailyMinutes budget per day. Interleave subjects.
 *  5. Remaining days before exam become REVISION sessions.
 */
public class StudyPlanGenerator {

    // Minimum revision window regardless of total days
    private static final int MIN_REVISION_DAYS = 3;
    // Fraction of total days reserved for revision
    private static final double REVISION_FRACTION = 0.18;

    /**
     * @param planId           ID of the StudyPlan row already inserted
     * @param topics           Incomplete topics to schedule (must have id set)
     * @param examDateMillis   Target exam date (epoch millis, normalized to midnight)
     * @param dailyMinutes     Student's available study time per day
     * @return                 List of StudySession objects (not yet inserted — caller inserts)
     */
    public List<StudySession> generate(long planId, List<Topic> topics,
                                       long examDateMillis, int dailyMinutes) {
        List<StudySession> sessions = new ArrayList<>();

        if (topics == null || topics.isEmpty()) return sessions;

        long todayStart = todayMidnightMillis();
        long availableMs = examDateMillis - todayStart;
        int availableDays = (int) (availableMs / 86_400_000L);

        if (availableDays <= 0) return sessions; // exam already passed

        int revisionDays = Math.max(MIN_REVISION_DAYS,
                           (int) Math.floor(availableDays * REVISION_FRACTION));
        int learningDays = Math.max(1, availableDays - revisionDays);

        // Sort topics: higher difficulty first, then longer estimated time
        // Then interleave subjects (round-robin by subjectId)
        List<Topic> sorted = interleaveBySubject(sortByWeight(topics));

        // Assign topics to learning days respecting the daily minute budget
        int dayOffset = 0;
        int minutesUsedToday = 0;

        for (Topic topic : sorted) {
            if (dayOffset >= learningDays) break; // no more learning days

            long scheduledDate = addDays(todayStart, dayOffset);
            sessions.add(new StudySession(planId, topic.id, scheduledDate, false));

            minutesUsedToday += topic.estimatedMinutes;

            // Move to next day if budget exceeded
            if (minutesUsedToday >= dailyMinutes) {
                dayOffset++;
                minutesUsedToday = 0;
            }
        }

        // If we ran out of learning days before all topics were scheduled,
        // push remaining topics into the last available learning day (they'll be
        // caught by the adaptive rescheduler at runtime)
        int scheduled = sessions.size();
        for (int i = scheduled; i < sorted.size(); i++) {
            long scheduledDate = addDays(todayStart, learningDays - 1);
            sessions.add(new StudySession(planId, sorted.get(i).id, scheduledDate, false));
        }

        // Revision sessions — one session per revision day pointing to
        // the first incomplete topic (AdaptiveRescheduler will refine these
        // at runtime based on quiz scores and needsRevision flags)
        for (int r = 0; r < revisionDays; r++) {
            int revDay = availableDays - revisionDays + r;
            long revDate = addDays(todayStart, revDay);
            // Use first topic as placeholder; RevisionPlanner will replace
            if (!sorted.isEmpty()) {
                sessions.add(new StudySession(planId, sorted.get(0).id, revDate, true));
            }
        }

        return sessions;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Sort topics: weight = difficulty × estimatedMinutes. Higher weight → earlier.
     */
    private List<Topic> sortByWeight(List<Topic> topics) {
        List<Topic> copy = new ArrayList<>(topics);
        Collections.sort(copy, new Comparator<Topic>() {
            @Override
            public int compare(Topic a, Topic b) {
                int wa = a.difficulty * a.estimatedMinutes;
                int wb = b.difficulty * b.estimatedMinutes;
                return Integer.compare(wb, wa); // descending
            }
        });
        return copy;
    }

    /**
     * Round-robin interleave by subjectId so subjects are balanced per day.
     * Assumption: input is already sorted by weight within each subject group.
     */
    private List<Topic> interleaveBySubject(List<Topic> sorted) {
        // Group by subjectId
        List<List<Topic>> groups = new ArrayList<>();
        List<Long> seenSubjects = new ArrayList<>();

        for (Topic t : sorted) {
            int idx = seenSubjects.indexOf(t.subjectId);
            if (idx < 0) {
                seenSubjects.add(t.subjectId);
                groups.add(new ArrayList<>());
                idx = groups.size() - 1;
            }
            groups.get(idx).add(t);
        }

        // Round-robin pick one from each group
        List<Topic> result = new ArrayList<>();
        boolean added = true;
        while (added) {
            added = false;
            for (List<Topic> group : groups) {
                if (!group.isEmpty()) {
                    result.add(group.remove(0));
                    added = true;
                }
            }
        }
        return result;
    }

    /** Returns epoch millis for today at 00:00:00.000 local time */
    public static long todayMidnightMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Add N days (in milliseconds) to a base epoch */
    public static long addDays(long baseMillis, int days) {
        return baseMillis + (long) days * 86_400_000L;
    }
}
