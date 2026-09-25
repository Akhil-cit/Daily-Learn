package com.example.daily_learn.domain.rag;

import java.util.List;

public class RagChunk {
    public String chunkId;
    public String text;
    
    // Metadata fields directly mirrored from Python
    public String courseName;
    public String courseCode;
    public int unitNumber;
    public String unitName;
    public String topicName;
    public List<String> subtopics;
    
    public RagChunk(String chunkId, String text, String courseName, String courseCode, 
                    int unitNumber, String unitName, String topicName, List<String> subtopics) {
        this.chunkId = chunkId;
        this.text = text;
        this.courseName = courseName;
        this.courseCode = courseCode;
        this.unitNumber = unitNumber;
        this.unitName = unitName;
        this.topicName = topicName;
        this.subtopics = subtopics;
    }
}
