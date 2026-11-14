package com.example.androidstudio;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.androidstudio.build.ErrorCollector;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProblemAdapter extends RecyclerView.Adapter<ProblemAdapter.VH> {

    public interface OnItemClick {
        void onClick(String filePath, int line);
    }

    private final List<ErrorCollector.Line> items;
    private final OnItemClick cb;

    public ProblemAdapter(List<ErrorCollector.Line> items, OnItemClick cb) {
        this.items = items;
        this.cb = cb;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_problem, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ErrorCollector.Line item = items.get(position);
        String text = item.text;

        // 简单解析：提取文件名、行号和错误描述
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_\\-\\./\\\\]+\\.java):(\\d+)");
        Matcher matcher = pattern.matcher(text);

        String fileStr = "Unknown File";
        int line = 0;
        String desc = text;

        if (matcher.find()) {
            fileStr = matcher.group(1);
            line = Integer.parseInt(matcher.group(2));
            desc = text.substring(matcher.end()).replace(":", "").trim();
        }

        File f = new File(fileStr);
        h.tvFile.setText(f.getName() + ":" + line);
        h.tvDesc.setText(desc);
        h.tvStage.setText("[" + item.stage + "]");

        final int finalLine = line;
        final String finalPath = fileStr;

        h.itemView.setOnClickListener(v -> {
            if (finalLine > 0) {
                cb.onClick(finalPath, finalLine);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvStage, tvFile, tvDesc;

        VH(@NonNull View v) {
            super(v);
            tvStage = v.findViewById(R.id.tvProblemStage);
            tvFile = v.findViewById(R.id.tvProblemFile);
            tvDesc = v.findViewById(R.id.tvProblemDesc);
        }
    }
}