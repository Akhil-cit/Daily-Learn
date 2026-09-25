package com.example.daily_learn.domain.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured output model for syllabus analysis.
 */
public class SyllabusAnalysisResult {
    public String courseName;
    public String courseCode;
    public int totalDays;
    public int totalTopicsCount;
    public List<DailyTopicGroup> days;
    public String rawText;

    public SyllabusAnalysisResult() {
        this.days = new ArrayList<>();
    }

    public SyllabusAnalysisResult(String courseName, String courseCode, int totalDays, List<DailyTopicGroup> days) {
        this.courseName = courseName != null ? courseName : "Course Syllabus";
        this.courseCode = courseCode != null ? courseCode : "";
        this.totalDays = totalDays;
        this.days = days != null ? days : new ArrayList<>();
        int count = 0;
        for (DailyTopicGroup g : this.days) {
            count += g.topics.size();
        }
        this.totalTopicsCount = count;
    }

    public String toJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"courseName\": \"").append(escapeJson(courseName)).append("\",\n");
        sb.append("  \"courseCode\": \"").append(escapeJson(courseCode)).append("\",\n");
        sb.append("  \"totalDays\": ").append(totalDays).append(",\n");
        sb.append("  \"totalTopics\": ").append(totalTopicsCount).append(",\n");
        sb.append("  \"days\": [\n");
        for (int i = 0; i < days.size(); i++) {
            sb.append("    ").append(days.get(i).toJsonString());
            if (i < days.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
