package com.example.daily_learn.domain.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single day's study topic group in the generated daily syllabus schedule.
 */
public class DailyTopicGroup {
    public int dayNumber;
    public String title;
    public String sectionName;
    public int lectureHours;
    public List<String> topics;

    public DailyTopicGroup() {
        this.topics = new ArrayList<>();
    }

    public DailyTopicGroup(int dayNumber, String title, String sectionName, int lectureHours, List<String> topics) {
        this.dayNumber = dayNumber;
        this.title = title != null ? title : "Day " + dayNumber;
        this.sectionName = sectionName != null ? sectionName : "";
        this.lectureHours = lectureHours;
        this.topics = topics != null ? topics : new ArrayList<>();
    }

    public String toJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("      \"day\": ").append(dayNumber).append(",\n");
        sb.append("      \"title\": \"").append(escapeJson(title)).append("\",\n");
        sb.append("      \"section\": \"").append(escapeJson(sectionName)).append("\",\n");
        sb.append("      \"lectureHours\": ").append(lectureHours).append(",\n");
        sb.append("      \"topics\": [\n");
        for (int i = 0; i < topics.size(); i++) {
            sb.append("        \"").append(escapeJson(topics.get(i))).append("\"");
            if (i < topics.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("      ]\n");
        sb.append("    }");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
