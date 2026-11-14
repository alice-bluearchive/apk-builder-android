package com.example.androidstudio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.VH> {

    public interface OnClick {
        void onClick(String name);
        void onDelete(String name);
    }

    private final List<String> items;
    private final OnClick cb;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    public ProjectAdapter(List<String> items, OnClick cb) {
        this.items = items;
        this.cb = cb;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String name = items.get(position);
        h.tvName.setText(name);

        ProjectConfig cfg = ProjectStore.loadConfig(name);
        String mode = cfg.mode == null ? "HTML" : cfg.mode;

        // 修复：判断具体的 Java 模式
        String modeLabel;
        String icon;
        if (mode.contains("JAVA_EMPTY_VIEW")) {
            modeLabel = "Java · Empty View";
            icon = "☕";
        } else if (mode.contains("JAVA_EMPTY")) {
            modeLabel = "Java · Empty";
            icon = "☕";
        } else if (mode.contains("HTML")) {
            modeLabel = "HTML";
            icon = "🌐";
        } else {
            modeLabel = mode;
            icon = "📄";
        }

        h.ivType.setText(icon);

        File dir = ProjectStore.dir(name);
        long ts = dir.lastModified();
        String time = dateFmt.format(new Date(ts));

        h.tvMeta.setText(modeLabel + " · " + time);

        h.itemView.setOnClickListener(v -> cb.onClick(name));
        h.btnDelete.setOnClickListener(v -> cb.onDelete(name));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvMeta, ivType;
        ImageButton btnDelete;

        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvMeta = v.findViewById(R.id.tvMeta);
            ivType = v.findViewById(R.id.ivType);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}