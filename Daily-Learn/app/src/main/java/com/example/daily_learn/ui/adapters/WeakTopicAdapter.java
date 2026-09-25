package com.example.daily_learn.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.Topic;

import java.util.ArrayList;
import java.util.List;

public class WeakTopicAdapter extends RecyclerView.Adapter<WeakTopicAdapter.ViewHolder> {

    private List<Topic> topics = new ArrayList<>();

    public void setTopics(List<Topic> topics) {
        this.topics = topics != null ? topics : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_weak_topic, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tvName.setText(topics.get(position).name);
    }

    @Override
    public int getItemCount() { return Math.min(topics.size(), 4); } // cap at 4

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        ViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_weak_topic_name);
        }
    }
}
