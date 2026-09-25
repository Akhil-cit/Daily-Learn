package com.example.daily_learn.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.StudySessionWithTopic;

import java.util.ArrayList;
import java.util.List;

public class UpcomingTopicAdapter extends RecyclerView.Adapter<UpcomingTopicAdapter.ViewHolder> {

    private List<StudySessionWithTopic> sessions = new ArrayList<>();

    public void setSessions(List<StudySessionWithTopic> sessions) {
        this.sessions = sessions != null ? sessions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_upcoming_topic, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudySessionWithTopic item = sessions.get(position);
        if (item.topic != null) {
            holder.tvName.setText(item.topic.name);
            holder.tvTime.setText(item.topic.estimatedMinutes + " min");
        }
        // Subject name would need a join — use placeholder
        holder.tvSubject.setText("Upcoming");
    }

    @Override
    public int getItemCount() { return Math.min(sessions.size(), 5); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvSubject, tvTime;
        ViewHolder(View v) {
            super(v);
            tvName    = v.findViewById(R.id.tv_upcoming_topic_name);
            tvSubject = v.findViewById(R.id.tv_upcoming_subject);
            tvTime    = v.findViewById(R.id.tv_upcoming_time);
        }
    }
}
