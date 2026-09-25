package com.example.daily_learn.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.domain.ai.DailyTopicGroup;

import java.util.ArrayList;
import java.util.List;

public class DailyTopicAdapter extends RecyclerView.Adapter<DailyTopicAdapter.ViewHolder> {

    private final List<DailyTopicGroup> items = new ArrayList<>();

    public void setItems(List<DailyTopicGroup> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_topic_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyTopicGroup group = items.get(position);
        holder.bind(group);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvDayBadge;
        private final TextView tvSectionTag;
        private final TextView tvLectureHours;
        private final TextView tvDayTitle;
        private final LinearLayout layoutSubtopics;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayBadge = itemView.findViewById(R.id.tv_day_badge);
            tvSectionTag = itemView.findViewById(R.id.tv_section_tag);
            tvLectureHours = itemView.findViewById(R.id.tv_lecture_hours);
            tvDayTitle = itemView.findViewById(R.id.tv_day_title);
            layoutSubtopics = itemView.findViewById(R.id.layout_subtopics);
        }

        void bind(DailyTopicGroup group) {
            tvDayBadge.setText("DAY " + group.dayNumber);
            tvDayTitle.setText(group.title);

            if (group.sectionName != null && !group.sectionName.isEmpty()) {
                tvSectionTag.setText(group.sectionName);
                tvSectionTag.setVisibility(View.VISIBLE);
            } else {
                tvSectionTag.setVisibility(View.GONE);
            }

            if (group.lectureHours > 0) {
                tvLectureHours.setText("⏱ " + group.lectureHours + "h");
                tvLectureHours.setVisibility(View.VISIBLE);
            } else {
                tvLectureHours.setVisibility(View.GONE);
            }

            // Populate bulleted subtopics
            layoutSubtopics.removeAllViews();
            Context ctx = itemView.getContext();

            if (group.topics != null) {
                for (String sub : group.topics) {
                    TextView tv = new TextView(ctx);
                    tv.setText("•  " + sub);
                    tv.setTextSize(13.5f);
                    tv.setTextColor(Color.parseColor("#CCCCCC"));
                    tv.setPadding(0, 4, 0, 4);
                    layoutSubtopics.addView(tv);
                }
            }
        }
    }
}
