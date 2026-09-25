package com.example.daily_learn.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.domain.ai.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<ChatMessage> messages;

    public ChatAdapter() {
        this.messages = new ArrayList<>();
    }

    public ChatAdapter(List<ChatMessage> backingList) {
        this.messages = (backingList != null) ? backingList : new ArrayList<>();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessage(int index, ChatMessage message) {
        if (index >= 0 && index < messages.size()) {
            messages.set(index, message);
            notifyItemChanged(index);
        }
    }

    public ChatMessage getMessage(int index) {
        if (index >= 0 && index < messages.size()) {
            return messages.get(index);
        }
        return null;
    }

    public void appendStreamingToken(RecyclerView rv, int index, String token) {
        if (index >= 0 && index < messages.size()) {
            ChatMessage msg = messages.get(index);
            msg.text = (msg.text == null) ? token : msg.text + token;

            ChatViewHolder holder = (ChatViewHolder) rv.findViewHolderForAdapterPosition(index);
            if (holder != null && holder.tvAi != null) {
                holder.tvAi.setText(msg.text);
            } else {
                notifyItemChanged(index, "APPEND");
            }
        }
    }

    public void appendToMessage(int index, String textToAppend) {
        if (index >= 0 && index < messages.size()) {
            ChatMessage msg = messages.get(index);
            msg.text = (msg.text == null) ? textToAppend : msg.text + textToAppend;
            notifyItemChanged(index, "APPEND");
        }
    }

    public void setMessages(List<ChatMessage> list) {
        messages.clear();
        if (list != null) messages.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        onBindViewHolder(holder, position, java.util.Collections.emptyList());
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position, @NonNull List<Object> payloads) {
        ChatMessage msg = messages.get(position);
        if (!payloads.isEmpty()) {
            if (msg.isUser) {
                holder.tvUser.setText(msg.text);
            } else {
                holder.tvAi.setText(msg.text);
            }
            return;
        }
        if (msg.isUser) {
            holder.llUser.setVisibility(View.VISIBLE);
            holder.llAi.setVisibility(View.GONE);
            holder.tvUser.setText(msg.text);
        } else {
            holder.llAi.setVisibility(View.VISIBLE);
            holder.llUser.setVisibility(View.GONE);
            holder.tvAi.setText(msg.text);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        public final View llAi, llUser;
        public final TextView tvAi, tvUser;

        public ChatViewHolder(View v) {
            super(v);
            llAi    = v.findViewById(R.id.ll_ai_container);
            llUser  = v.findViewById(R.id.ll_user_container);
            tvAi    = v.findViewById(R.id.tv_ai_message);
            tvUser  = v.findViewById(R.id.tv_user_message);
        }
    }
}
