package com.example.daily_learn.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.UnitWithTopics;

import java.util.ArrayList;
import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.ViewHolder> {

    public interface OnSubjectClickListener {
        void onSubjectClick(long subjectId);
    }

    private List<SubjectWithUnitsAndTopics> subjects = new ArrayList<>();
    private final OnSubjectClickListener listener;

    public SubjectAdapter(OnSubjectClickListener listener) {
        this.listener = listener;
    }

    public void setSubjects(List<SubjectWithUnitsAndTopics> subjects) {
        this.subjects = subjects != null ? subjects : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_subject, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SubjectWithUnitsAndTopics item = subjects.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() { return subjects.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvPercent, tvTopicsDone;
        final ProgressBar pb;

        ViewHolder(View v) {
            super(v);
            tvName       = v.findViewById(R.id.tv_subject_name);
            tvPercent    = v.findViewById(R.id.tv_subject_percent);
            tvTopicsDone = v.findViewById(R.id.tv_subject_topics_done);
            pb           = v.findViewById(R.id.pb_subject);
        }

        void bind(SubjectWithUnitsAndTopics item, OnSubjectClickListener listener) {
            tvName.setText(item.subject.name);

            // Count topics
            int total = 0, done = 0;
            if (item.units != null) {
                for (UnitWithTopics u : item.units) {
                    if (u.topics != null) {
                        for (Topic t : u.topics) {
                            total++;
                            if (t.isCompleted) done++;
                        }
                    }
                }
            }

            int pct = (total > 0) ? (int) ((done * 100f) / total) : 0;
            tvPercent.setText(pct + "%");
            pb.setProgress(pct);
            tvTopicsDone.setText(done + " / " + total + " topics");

            itemView.setOnClickListener(v -> listener.onSubjectClick(item.subject.id));
        }
    }
}
