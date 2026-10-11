package com.example.androidstudio;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> {

    public interface OnClick {
        void onClick(SearchResult r);
    }

    private final List<SearchResult> items;
    private final OnClick cb;
    private final String rootPath;

    public SearchAdapter(List<SearchResult> items, String rootPath, OnClick cb) {
        this.items = items;
        this.rootPath = rootPath;
        this.cb = cb;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SearchResult r = items.get(pos);

        if (r.line == 0) {
            // ===== 文件名匹配 =====
            h.tvFile.setText("📄 " + getRelative(r.file.getAbsolutePath()));
            SpannableString sp = new SpannableString(r.lineText);
            if (r.matchStart >= 0 && r.matchEnd > r.matchStart
                    && r.matchEnd <= r.lineText.length()) {
                sp.setSpan(new BackgroundColorSpan(0xFFFFC107),
                        r.matchStart, r.matchEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new ForegroundColorSpan(Color.BLACK),
                        r.matchStart, r.matchEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            h.tvLine.setText(sp);
        } else {
            // ===== 内容匹配 =====
            h.tvFile.setText(getRelative(r.file.getAbsolutePath()) + ":" + r.line);
            String text = r.lineText;
            SpannableString sp = new SpannableString(text);
            int s = r.matchStart;
            int e = r.matchEnd;
            if (s >= 0 && e > s && e <= text.length()) {
                sp.setSpan(new BackgroundColorSpan(0xFFFFC107), s, e,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new ForegroundColorSpan(Color.BLACK), s, e,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            h.tvLine.setText(sp);
        }

        h.itemView.setOnClickListener(v -> cb.onClick(r));
    }

    private String getRelative(String path) {
        if (path.startsWith(rootPath)) {
            String p = path.substring(rootPath.length());
            if (p.startsWith("/")) p = p.substring(1);
            return p;
        }
        return path;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvFile, tvLine;
        VH(@NonNull View v) {
            super(v);
            tvFile = v.findViewById(R.id.tvSearchFile);
            tvLine = v.findViewById(R.id.tvSearchLine);
        }
    }
}