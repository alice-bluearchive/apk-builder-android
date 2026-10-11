package com.example.androidstudio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.VH> {

    public interface OnNodeClick {
        void onClick(FileNode node);
        void onLongClick(FileNode node);
    }

    private final List<FileNode> nodes;
    private final OnNodeClick cb;

    public FileTreeAdapter(List<FileNode> nodes, OnNodeClick cb) {
        this.nodes = nodes;
        this.cb = cb;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file_node, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        FileNode n = nodes.get(pos);
        h.tvName.setText(n.name);

        float density = h.itemView.getResources().getDisplayMetrics().density;
        int maxIndentDp = 96;
        int indentDp = Math.min(8 + n.depth * 16, maxIndentDp);
        int startPx = Math.round(indentDp * density);
        int endPx = Math.round(8 * density);
        h.itemView.setPadding(startPx, 0, endPx, 0);

        if (n.isDir) {
            h.ivArrow.setVisibility(View.VISIBLE);
            h.ivArrow.setImageResource(n.expanded
                    ? R.drawable.ic_chevron_down
                    : R.drawable.ic_chevron_right);
        } else {
            h.ivArrow.setVisibility(View.INVISIBLE);
        }

        h.ivIcon.setImageResource(iconFor(n));

        h.itemView.setOnClickListener(v -> cb.onClick(n));
        h.itemView.setOnLongClickListener(v -> {
            cb.onLongClick(n);
            return true;
        });
    }

    private int iconFor(FileNode n) {
        if (n.isDir) return R.drawable.ic_file_folder;
        String name = n.name.toLowerCase();
        if (name.endsWith(".java")) return R.drawable.ic_file_java;
        if (name.endsWith(".json")) return R.drawable.ic_file_json;
        if (name.endsWith(".xml")) return R.drawable.ic_file_xml;
        if (name.endsWith(".html") || name.endsWith(".htm")) return R.drawable.ic_file_html;
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp")
                || name.endsWith(".bmp")) return R.drawable.ic_file_image;
        return R.drawable.ic_file_generic;
    }

    @Override
    public int getItemCount() {
        return nodes.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivArrow, ivIcon;
        TextView tvName;
        VH(@NonNull View v) {
            super(v);
            ivArrow = v.findViewById(R.id.ivArrow);
            ivIcon = v.findViewById(R.id.ivIcon);
            tvName = v.findViewById(R.id.tvName);
        }
    }
}