package com.example.daily_learn.domain.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory manager that preserves chat discussion history across bottom sheet opens/closes
 * for the entire duration of the app session (until the user exits the app).
 */
public class ChatSessionManager {

    private static final Map<String, List<ChatMessage>> topicHistories =
            Collections.synchronizedMap(new LinkedHashMap<>());

    public static List<ChatMessage> getHistory(String topicName) {
        String key = (topicName != null && !topicName.trim().isEmpty()) ? topicName.trim() : "General";
        List<ChatMessage> list = topicHistories.get(key);
        if (list == null) {
            list = new ArrayList<>();
            list.add(new ChatMessage(
                    "👋 Hello! I'm your AI Study Tutor for **" + key + "**.\n" +
                    "Ask me anything you're confused about, or tap one of the chips above to explore!", false));
            topicHistories.put(key, list);
        }
        return list;
    }

    public static void clearAll() {
        topicHistories.clear();
    }
}
