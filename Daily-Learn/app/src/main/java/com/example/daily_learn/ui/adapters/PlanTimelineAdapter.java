package com.example.daily_learn.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.StudySession;
import com.example.daily_learn.data.local.entity.StudySessionWithTopic;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlanTimelineAdapter extends RecyclerView.Adapter<PlanTimelineAdapter.ViewHolder> {

    private List<StudySessionWithTopic> sessions = new ArrayList<>();
    private final long todayStart;

    public PlanTimelineAdapter(long todayStart) {
        this.todayStart = todayStart;
    }

    public void setSessions(List<StudySessionWithTopic> sessions) {
        this.sessions = sessions != null ? sessions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_plan_session, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudySessionWithTopic item = sessions.get(position);
        holder.bind(item, todayStart, position == 0, position == sessions.size() - 1);
    }

    @Override
    public int getItemCount() { return sessions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate, tvStatus, tvTopic, tvSubject;
        final View vDot, vLineTop, vLineBottom;

        ViewHolder(View v) {
            super(v);
            tvDate        = v.findViewById(R.id.tv_session_date);
            tvStatus      = v.findViewById(R.id.tv_session_status);
            tvTopic       = v.findViewById(R.id.tv_session_topic);
            tvSubject     = v.findViewById(R.id.tv_session_subject);
            vDot          = v.findViewById(R.id.v_dot);
            vLineTop      = v.findViewById(R.id.v_line_top);
            vLineBottom   = v.findViewById(R.id.v_line_bottom);
        }

        void bind(StudySessionWithTopic item, long todayStart, boolean isFirst, boolean isLast) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
            tvDate.setText(sdf.format(new Date(item.session.scheduledDate)));

            long sessionDay  = item.session.scheduledDate;
            long todayEnd    = todayStart + 86_400_000L;
            boolean isToday  = sessionDay >= todayStart && sessionDay < todayEnd;
            boolean isPast   = sessionDay < todayStart;

            String status    = item.session.status;
            String statusText;
            int dotColor;

            if (item.session.isRevision) {
                statusText = "REVISION";
                dotColor = Color.parseColor("#FF7043");
            } else if (StudySession.STATUS_DONE.equals(status)) {
                statusText = "✓ DONE";
                dotColor = Color.parseColor("#43A047");
            } else if (StudySession.STATUS_MISSED.equals(status)) {
                statusText = "MISSED";
                dotColor = Color.parseColor("#EF5350");
            } else if (isToday) {
                statusText = "TODAY";
                dotColor = Color.parseColor("#5C6BC0");
            } else if (isPast) {
                statusText = "MISSED";
                dotColor = Color.parseColor("#EF5350");
            } else {
                statusText = "UPCOMING";
                dotColor = Color.parseColor("#9E9EAF");
            }

            tvStatus.setText(statusText);
            vDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(dotColor));

            if (item.topic != null) {
                tvTopic.setText(item.topic.name);
                tvSubject.setText(item.topic.estimatedMinutes + " min");
            } else {
                tvTopic.setText("Topic");
                tvSubject.setText("");
            }

            // Hide top line for first item, bottom line for last
            vLineTop.setVisibility(isFirst ? View.INVISIBLE : View.VISIBLE);
            vLineBottom.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);
        }
    }
}
