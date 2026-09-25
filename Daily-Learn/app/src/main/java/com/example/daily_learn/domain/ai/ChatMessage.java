package com.example.daily_learn.domain.ai;

public class ChatMessage {
    public String text;
    public final boolean isUser;
    public final long timestamp;

    public ChatMessage(String text, boolean isUser) {
        this.text = text;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
    }
}
