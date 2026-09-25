package com.example.daily_learn.domain.rag;

import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.UnitWithTopics;

import java.util.ArrayList;
import java.util.List;

public class RagChunker {

    /**
     * Converts a Subject with its Units and Topics into a list of RagChunks.
     */
    public static List<RagChunk> createChunks(SubjectWithUnitsAndTopics data) {
        List<RagChunk> chunks = new ArrayList<>();
        if (data == null || data.subject == null || data.units == null) {
            return chunks;
        }

        String courseName = data.subject.name;
        String courseCode = "N/A"; // Not stored explicitly in Android DB

        for (int i = 0; i < data.units.size(); i++) {
            UnitWithTopics unitWithTopics = data.units.get(i);
            if (unitWithTopics == null || unitWithTopics.unit == null || unitWithTopics.topics == null) {
                continue;
            }

            int unitNumber = unitWithTopics.unit.orderIndex > 0 ? unitWithTopics.unit.orderIndex : i + 1;
            String unitName = unitWithTopics.unit.name;

            for (int j = 0; j < unitWithTopics.topics.size(); j++) {
                Topic topic = unitWithTopics.topics.get(j);
                
                String chunkId = "u" + unitNumber + "_t" + (j + 1);
                
                // Subtopics are not explicitly modeled in Android Topic entity currently
                List<String> subtopics = new ArrayList<>();
                String subtopicsStr = "None";

                String text = "Course: " + courseName + " (" + courseCode + ")\n" +
                              "Unit " + unitNumber + ": " + unitName + "\n" +
                              "Topic: " + topic.name + "\n" +
                              "Subtopics: " + subtopicsStr;

                chunks.add(new RagChunk(
                        chunkId,
                        text,
                        courseName,
                        courseCode,
                        unitNumber,
                        unitName,
                        topic.name,
                        subtopics
                ));
            }
        }

        return chunks;
    }
}
