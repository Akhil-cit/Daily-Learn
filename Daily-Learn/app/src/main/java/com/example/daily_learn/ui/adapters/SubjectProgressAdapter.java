package com.example.daily_learn.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.Progress;

import java.util.ArrayList;
import java.util.List;

public class SubjectProgressAdapter extends RecyclerView.Adapter<SubjectProgressAdapter.ViewHolder> {

    // Subject names must be supplied externally (Progress entity has subjectId not name)
    public static class ProgressItem {
        public Progress progress;
        public String subjectName;
        public ProgressItem(Progress p, String name) { progress = p; subjectName = name; }
    }

    private List<ProgressItem> items = new ArrayList<>();

    public void setItems(List<ProgressItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_subject_progress, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProgressItem item = items.get(position);
        holder.tvName.setText(item.subjectName);

        int pct = item.progress.totalTopics > 0
            ? (int) ((item.progress.completedTopics * 100f) / item.progress.totalTopics)
            : 0;
        holder.tvPercent.setText(pct + "%");
        holder.pb.setProgress(pct);
        holder.tvTopics.setText(item.progress.completedTopics + " / " + item.progress.totalTopics + " topics");
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvPercent, tvTopics;
        final ProgressBar pb;
        ViewHolder(View v) {
            super(v);
            tvName    = v.findViewById(R.id.tv_sp_subject_name);
            tvPercent = v.findViewById(R.id.tv_sp_percent);
            tvTopics  = v.findViewById(R.id.tv_sp_topics);
            pb        = v.findViewById(R.id.pb_sp);
        }
    }
}
