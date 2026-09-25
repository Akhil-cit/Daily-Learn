package com.example.daily_learn.domain.ai;

import android.util.Log;

import com.example.daily_learn.domain.rag.RagService;
import com.example.daily_learn.domain.rag.SearchHit;

import java.util.List;

public class AiTutorEngine {
    private static final String TAG = "AiTutorEngine";

    private final LocalLLMEngine localLLMEngine;
    private final RagService ragService;

    public interface ResponseCallback {
        void onResponse(String answer);
    }

    public AiTutorEngine(LocalLLMEngine localLLMEngine, RagService ragService) {
        this.localLLMEngine = localLLMEngine;
        this.ragService = ragService;
    }

    /**
     * Generates a context-aware pedagogical response for the student's doubt.
     * Uses the production default of 128 max tokens.
     */
    public String generateResponse(String topicName, String subjectName, String userQuery) {
        return generateResponse(topicName, subjectName, userQuery, 128);
    }

    /**
     * Generates a context-aware pedagogical response with configurable max tokens.
     */
    public String generateResponse(String topicName, String subjectName, String userQuery, int maxTokens) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return "How can I help you understand " + topicName + " better? Feel free to ask a question!";
        }

        if (localLLMEngine == null || !localLLMEngine.isLoaded()) {
            return "Error: AI engine is not loaded.";
        }

        StringBuilder contextBuilder = new StringBuilder();
        int chunkCount = 0;

        if (ragService != null && ragService.isIndexed()) {
            // Retrieve syllabus context
            String searchQ = (subjectName != null && !subjectName.isEmpty() ? subjectName + " " : "") + topicName + " " + userQuery;
            List<SearchHit> hits = ragService.search(searchQ, 6);
            
            for (SearchHit hit : hits) {
                    if (subjectName != null && !subjectName.isEmpty() && !subjectName.equalsIgnoreCase("Subject") && hit.metadata.text != null) {
                        if (hit.metadata.text.startsWith("Course: ") && !hit.metadata.text.toLowerCase().contains(subjectName.toLowerCase())) {
                            continue;
                        }
                    }
                    contextBuilder.append("- ").append(hit.metadata.text).append("\n");
                    chunkCount++;
                    if (chunkCount >= 3) break;
                }
            }

        String course = (subjectName != null && !subjectName.isEmpty() && !subjectName.equals("Subject")) ? subjectName : "the enrolled subject";
        String topic = (topicName != null && !topicName.isEmpty()) ? topicName : "the current topic";

        String retrievedContext = contextBuilder.toString().trim();
        if (retrievedContext.isEmpty()) {
            retrievedContext = "Course: " + course + "\nTopic: " + topic;
        }

        Log.i(TAG, "RAG retrieved " + chunkCount + " chunks for query: " + userQuery);

        String prompt = "<|im_start|>system\n" +
                "You are an expert, helpful AI study tutor for the course: " + course + ".\n" +
                "The current lesson topic is: " + topic + ".\n" +
                "Answer questions and provide examples strictly relevant to " + course + ". Do NOT mix in other subjects.\n" +
                "Provide detailed, comprehensive, and well-explained answers with examples. Conclude thoughts clearly.\n" +
                "CONTEXT:\n" + retrievedContext + "<|im_end|>\n" +
                "<|im_start|>user\n" +
                userQuery + "<|im_end|>\n" +
                "<|im_start|>assistant\n";

        String response = localLLMEngine.generate(prompt, maxTokens);
        if (response != null && response.trim().startsWith("NOT_FOUND")) {
            return "The provided study material does not contain enough information to answer this.";
        }
        return response;
    }

    /**
     * Generates a context-aware pedagogical response progressively via streaming callback.
     */
    public void generateResponseStreaming(String topicName, String subjectName, String userQuery, int maxTokens, LlamaCppPoC.StreamCallback callback) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            if (callback != null) callback.onComplete("How can I help you understand " + topicName + " better? Feel free to ask a question!");
            return;
        }

        if (localLLMEngine == null || !localLLMEngine.isLoaded()) {
            if (callback != null) callback.onError("Error: AI engine is not loaded.");
            return;
        }

        StringBuilder contextBuilder = new StringBuilder();
        int chunkCount = 0;

        if (ragService != null && ragService.isIndexed()) {
            String searchQ = (subjectName != null && !subjectName.isEmpty() ? subjectName + " " : "") + topicName + " " + userQuery;
            List<SearchHit> hits = ragService.search(searchQ, 6);
            
            for (SearchHit hit : hits) {
                if (hit.metadata != null) {
                    if (subjectName != null && !subjectName.isEmpty() && !subjectName.equalsIgnoreCase("Subject") && hit.metadata.text != null) {
                        if (hit.metadata.text.startsWith("Course: ") && !hit.metadata.text.toLowerCase().contains(subjectName.toLowerCase())) {
                            continue;
                        }
                    }
                    contextBuilder.append("- ").append(hit.metadata.text).append("\n");
                    chunkCount++;
                    if (chunkCount >= 3) break;
                }
            }
        }

        String course = (subjectName != null && !subjectName.isEmpty() && !subjectName.equals("Subject")) ? subjectName : "the enrolled subject";
        String topic = (topicName != null && !topicName.isEmpty()) ? topicName : "the current topic";

        String retrievedContext = contextBuilder.toString().trim();
        if (retrievedContext.isEmpty()) {
            retrievedContext = "Course: " + course + "\nTopic: " + topic;
        }

        Log.i(TAG, "RAG retrieved " + chunkCount + " chunks for streaming query: " + userQuery);

        String prompt = "<|im_start|>system\n" +
                "You are an expert, helpful AI study tutor for the course: " + course + ".\n" +
                "The current lesson topic is: " + topic + ".\n" +
                "Answer questions and provide examples strictly relevant to " + course + ". Do NOT mix in other subjects.\n" +
                "Provide detailed, comprehensive, and well-explained answers with examples. Conclude thoughts clearly.\n" +
                "CONTEXT:\n" + retrievedContext + "<|im_end|>\n" +
                "<|im_start|>user\n" +
                userQuery + "<|im_end|>\n" +
                "<|im_start|>assistant\n";

        localLLMEngine.generateStreaming(prompt, maxTokens, callback);
    }
}
