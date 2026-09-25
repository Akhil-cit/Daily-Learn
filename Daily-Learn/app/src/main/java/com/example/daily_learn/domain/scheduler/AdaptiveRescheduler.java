package com.example.daily_learn.domain.scheduler;

import com.example.daily_learn.data.local.entity.StudySession;
import com.example.daily_learn.data.local.entity.Topic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure-Java adaptive rescheduler — no Android imports.
 *
 * Handles (per spec §5.6 and §5.7):
 *  - Missed-day detection: overdue SCHEDULED sessions → MISSED
 *  - Push remaining sessions forward day by day
 *  - Combine small topics if needed to prevent overloading
 */
public class AdaptiveRescheduler {

    /**
     * Detect missed sessions and reschedule remaining SCHEDULED sessions.
     *
     * @param allSessions      All sessions for the plan (any status), sorted by scheduledDate
     * @param todayMillis      Today midnight epoch millis
     * @param dailyMinutes     Student's daily budget
     * @param topicsById       Map-like lookup of topic by id (pass as parallel list)
     * @return                 Updated session list with MISSED status set and new dates assigned
     */
    public RescheduleResult reschedule(List<StudySession> allSessions,
                                       long todayMillis,
                                       int dailyMinutes,
                                       List<Topic> topicsById) {
        List<StudySession> missed    = new ArrayList<>();
        List<StudySession> toReschedule = new ArrayList<>();
        List<StudySession> keep      = new ArrayList<>(); // DONE, already MISSED, REVISION

        for (StudySession s : allSessions) {
            if (s.status.equals(StudySession.STATUS_SCHEDULED) && s.scheduledDate < todayMillis) {
                s.status = StudySession.STATUS_MISSED;
                missed.add(s);
            } else if (s.status.equals(StudySession.STATUS_SCHEDULED)) {
                toReschedule.add(s);
            } else {
                keep.add(s);
            }
        }

        if (missed.isEmpty()) {
            // Nothing to reschedule
            RescheduleResult r = new RescheduleResult();
            r.updatedSessions = allSessions;
            r.missedCount = 0;
            return r;
        }

        // Re-distribute toReschedule sessions starting from today,
        // respecting daily minutes budget
        long currentDay = todayMillis;
        int minutesUsed = 0;

        for (StudySession s : toReschedule) {
            Topic t = findTopic(topicsById, s.topicId);
            int topicMinutes = (t != null) ? t.estimatedMinutes : 30;

            // If adding this topic exceeds today's budget, move to next day
            if (minutesUsed + topicMinutes > dailyMinutes && minutesUsed > 0) {
                currentDay += 86_400_000L;
                minutesUsed = 0;
            }

            s.scheduledDate = currentDay;
            s.status = StudySession.STATUS_RESCHEDULED;
            minutesUsed += topicMinutes;
        }

        List<StudySession> result = new ArrayList<>();
        result.addAll(missed);
        result.addAll(toReschedule);
        result.addAll(keep);

        Collections.sort(result, new Comparator<StudySession>() {
            @Override
            public int compare(StudySession a, StudySession b) {
                return Long.compare(a.scheduledDate, b.scheduledDate);
            }
        });

        RescheduleResult r = new RescheduleResult();
        r.updatedSessions = result;
        r.missedCount = missed.size();
        r.rescheduledSessions = toReschedule;
        return r;
    }

    private Topic findTopic(List<Topic> topics, long topicId) {
        for (Topic t : topics) {
            if (t.id == topicId) return t;
        }
        return null;
    }

    public static class RescheduleResult {
        public List<StudySession> updatedSessions  = new ArrayList<>();
        public List<StudySession> rescheduledSessions = new ArrayList<>();
        public int missedCount = 0;
    }
}
