package com.example.daily_learn.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.daily_learn.R;
import com.example.daily_learn.data.local.entity.Subject;
import com.example.daily_learn.data.local.entity.SubjectWithUnitsAndTopics;
import com.example.daily_learn.data.local.entity.Topic;
import com.example.daily_learn.data.local.entity.Unit;
import com.example.daily_learn.data.local.entity.UnitWithTopics;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class ManualEditorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SUBJECT = 0;
    private static final int TYPE_UNIT    = 1;
    private static final int TYPE_TOPIC   = 2;

    public interface EditorActionListener {
        void onAddUnit(Subject subject);
        void onDeleteSubject(Subject subject);
        void onAddTopic(Unit unit, long subjectId);
        void onDeleteUnit(Unit unit);
        void onEditTopic(Topic topic);
        void onDeleteTopic(Topic topic);
    }

    private static class EditorItem {
        int type;
        Subject subject;
        Unit unit;
        Topic topic;
        long subjectId;
    }

    private final List<EditorItem> items = new ArrayList<>();
    private final EditorActionListener listener;

    public ManualEditorAdapter(EditorActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<SubjectWithUnitsAndTopics> subjectTrees) {
        items.clear();
        if (subjectTrees != null) {
            for (SubjectWithUnitsAndTopics swu : subjectTrees) {
                EditorItem sItem = new EditorItem();
                sItem.type = TYPE_SUBJECT;
                sItem.subject = swu.subject;
                items.add(sItem);

                if (swu.units != null) {
                    for (UnitWithTopics u : swu.units) {
                        EditorItem uItem = new EditorItem();
                        uItem.type = TYPE_UNIT;
                        uItem.unit = u.unit;
                        uItem.subjectId = swu.subject.id;
                        items.add(uItem);

                        if (u.topics != null) {
                            for (Topic t : u.topics) {
                                EditorItem tItem = new EditorItem();
                                tItem.type = TYPE_TOPIC;
                                tItem.topic = t;
                                items.add(tItem);
                            }
                        }
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SUBJECT) {
            return new SubjectViewHolder(inflater.inflate(R.layout.item_editor_subject, parent, false));
        } else if (viewType == TYPE_UNIT) {
            return new UnitViewHolder(inflater.inflate(R.layout.item_editor_unit, parent, false));
        } else {
            return new TopicViewHolder(inflater.inflate(R.layout.item_editor_topic, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        EditorItem item = items.get(position);
        if (holder instanceof SubjectViewHolder) {
            SubjectViewHolder h = (SubjectViewHolder) holder;
            h.tvName.setText(item.subject.name);
            h.btnAddUnit.setOnClickListener(v -> listener.onAddUnit(item.subject));
            h.btnDelete.setOnClickListener(v -> listener.onDeleteSubject(item.subject));
        } else if (holder instanceof UnitViewHolder) {
            UnitViewHolder h = (UnitViewHolder) holder;
            h.tvName.setText(item.unit.name);
            h.btnAddTopic.setOnClickListener(v -> listener.onAddTopic(item.unit, item.subjectId));
            h.btnDelete.setOnClickListener(v -> listener.onDeleteUnit(item.unit));
        } else if (holder instanceof TopicViewHolder) {
            TopicViewHolder h = (TopicViewHolder) holder;
            h.tvName.setText(item.topic.name);
            String diff = item.topic.difficulty == 1 ? "Easy" : item.topic.difficulty == 2 ? "Medium" : "Hard";
            h.tvInfo.setText(diff + " • " + item.topic.estimatedMinutes + " min" + (item.topic.isCompleted ? " • ✓ Done" : ""));
            h.btnEdit.setOnClickListener(v -> listener.onEditTopic(item.topic));
            h.btnDelete.setOnClickListener(v -> listener.onDeleteTopic(item.topic));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SubjectViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final MaterialButton btnAddUnit;
        final ImageButton btnDelete;
        SubjectViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_editor_subject_name);
            btnAddUnit = v.findViewById(R.id.btn_add_unit_to_subject);
            btnDelete = v.findViewById(R.id.btn_delete_subject);
        }
    }

    static class UnitViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final MaterialButton btnAddTopic;
        final ImageButton btnDelete;
        UnitViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_editor_unit_name);
            btnAddTopic = v.findViewById(R.id.btn_add_topic_to_unit);
            btnDelete = v.findViewById(R.id.btn_delete_unit);
        }
    }

    static class TopicViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvInfo;
        final ImageButton btnEdit, btnDelete;
        TopicViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_editor_topic_name);
            tvInfo = v.findViewById(R.id.tv_editor_topic_info);
            btnEdit = v.findViewById(R.id.btn_edit_topic);
            btnDelete = v.findViewById(R.id.btn_delete_topic);
        }
    }
}
