package com.example.daily_learn.ui.adapters;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.UnitWithTopics;

import java.util.ArrayList;
import java.util.List;

/**
 * Flat adapter for Subject Detail: renders unit headers interspersed with topic rows.
 * Item types: TYPE_UNIT_HEADER and TYPE_TOPIC_ROW.
 */
public class UnitTopicAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnTopicClickListener {
        void onTopicClick(Topic topic);
    }

    private static final int TYPE_UNIT   = 0;
    private static final int TYPE_TOPIC  = 1;

    // Flattened list item (either a unit name or a topic)
    private static class ListItem {
        int type;
        String unitName;
        int unitCompleted;
        int unitTotal;
        Topic topic;
    }

    private final List<ListItem> items = new ArrayList<>();
    private final OnTopicClickListener listener;

    public UnitTopicAdapter(OnTopicClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<UnitWithTopics> units) {
        items.clear();
        if (units == null) { notifyDataSetChanged(); return; }
        for (UnitWithTopics u : units) {
            ListItem header = new ListItem();
            header.type = TYPE_UNIT;
            header.unitName = u.unit.name;
            int done = 0;
            if (u.topics != null) {
                for (Topic t : u.topics) if (t.isCompleted) done++;
                header.unitCompleted = done;
                header.unitTotal     = u.topics.size();
            }
            items.add(header);
            if (u.topics != null) {
                for (Topic t : u.topics) {
                    ListItem row = new ListItem();
                    row.type  = TYPE_TOPIC;
                    row.topic = t;
                    items.add(row);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) { return items.get(position).type; }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_UNIT) {
            return new UnitViewHolder(inflater.inflate(R.layout.item_unit_header, parent, false));
        } else {
            return new TopicViewHolder(inflater.inflate(R.layout.item_topic_row, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = items.get(position);
        if (holder instanceof UnitViewHolder) {
            UnitViewHolder h = (UnitViewHolder) holder;
            h.tvName.setText(item.unitName);
            h.tvCompletion.setText(item.unitCompleted + "/" + item.unitTotal);
        } else if (holder instanceof TopicViewHolder) {
            TopicViewHolder h = (TopicViewHolder) holder;
            h.tvName.setText(item.topic.name);

            String diff = item.topic.difficulty == 1 ? "Easy" : item.topic.difficulty == 2 ? "Medium" : "Hard";
            h.tvMeta.setText(diff + " • " + item.topic.estimatedMinutes + " min");

            if (item.topic.isCompleted) {
                h.tvStatus.setText("✓");
                h.tvName.setPaintFlags(h.tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                h.tvStatus.setText("○");
                h.tvName.setPaintFlags(h.tvName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }

            h.tvRevision.setVisibility(item.topic.needsRevision ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTopicClick(item.topic);
                }
            });
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class UnitViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvCompletion;
        UnitViewHolder(View v) {
            super(v);
            tvName       = v.findViewById(R.id.tv_unit_name);
            tvCompletion = v.findViewById(R.id.tv_unit_completion);
        }
    }

    static class TopicViewHolder extends RecyclerView.ViewHolder {
        final TextView tvStatus, tvName, tvMeta, tvRevision;
        TopicViewHolder(View v) {
            super(v);
            tvStatus   = v.findViewById(R.id.tv_topic_status_icon);
            tvName     = v.findViewById(R.id.tv_topic_name);
            tvMeta     = v.findViewById(R.id.tv_topic_meta);
            tvRevision = v.findViewById(R.id.tv_topic_needs_revision);
        }
    }
}
