package com.example.androidstudio;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CommandPaletteDialog extends Dialog {

    public interface OnCommandSelected {
        void onCommand(String commandId, Object payload);
        void onFileSelected(File file);
    }

    private static class PaletteItem {
        String type; // "cmd" or "file"
        String id;   // cmd id, or file path
        String title;
        String desc;
        String icon;
        Object payload;
    }

    private final Context ctx;
    private final OnCommandSelected listener;
    private final File projectRoot;
    private final List<PaletteItem> allItems = new ArrayList<>();
    private final List<PaletteItem> filteredItems = new ArrayList<>();

    private EditText etInput;
    private RecyclerView listCommands;

    public CommandPaletteDialog(@NonNull Context context, File projectRoot, OnCommandSelected listener) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        this.ctx = context;
        this.projectRoot = projectRoot;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_command_palette);

        etInput = findViewById(R.id.etCommandInput);
        listCommands = findViewById(R.id.listCommands);

        listCommands.setLayoutManager(new LinearLayoutManager(ctx));
        buildIndex(); // 构建命令和文件索引

        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });

        // 点击背景关闭
        findViewById(R.id.etCommandInput).setOnClickListener(v -> {});
        findViewById(android.R.id.content).setOnClickListener(v -> dismiss());

        filter(""); // 初始显示所有命令
        etInput.requestFocus();
    }

    private void buildIndex() {
        // 1. 注册系统命令
        allItems.add(createCmd("build", "Build APK (构建项目)", "> build", "▶️", "build"));
        allItems.add(createCmd("terminal", "Open Terminal (打开终端)", "> terminal", "💻", "terminal"));
        allItems.add(createCmd("settings", "Project Settings (项目设置)", "> settings", "⚙️", "settings"));
        allItems.add(createCmd("language", "Switch Language (切换语言)", "> language", "🌐", "language"));
        allItems.add(createCmd("extensions", "Extensions (扩展商店)", "> extensions", "🧩", "extensions"));
        allItems.add(createCmd("reload", "Reload File Tree (刷新文件树)", "> reload", "🔄", "reload"));
        allItems.add(createCmd("clear", "Clear Terminal (清空终端)", "> clear", "🧹", "clear"));

        // 2. 递归扫描文件
        scanFiles(projectRoot);
    }

    private PaletteItem createCmd(String id, String title, String desc, String icon, Object payload) {
        PaletteItem item = new PaletteItem();
        item.type = "cmd";
        item.id = id;
        item.title = title;
        item.desc = desc;
        item.icon = icon;
        item.payload = payload;
        return item;
    }

    private void scanFiles(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String name = f.getName();
                if (name.startsWith(".") || name.equals("build") || name.equals("output")) continue;
                scanFiles(f);
            } else {
                PaletteItem item = new PaletteItem();
                item.type = "file";
                item.id = f.getAbsolutePath();
                item.title = f.getName();
                // 显示相对路径
                item.desc = f.getParentFile().getAbsolutePath().replace(projectRoot.getAbsolutePath(), "");
                item.icon = f.getName().endsWith(".java") ? "☕" : (f.getName().endsWith(".xml") ? "📄" : "📄");
                item.payload = f;
                allItems.add(item);
            }
        }
    }

    private void filter(String query) {
        filteredItems.clear();
        String q = query.toLowerCase().trim();

        if (q.isEmpty()) {
            // 不输入时，默认显示常用命令
            for (PaletteItem item : allItems) {
                if ("cmd".equals(item.type)) filteredItems.add(item);
            }
        } else if (q.startsWith(">")) {
            // 输入 >，只搜命令
            String cmdQuery = q.substring(1).trim();
            for (PaletteItem item : allItems) {
                if ("cmd".equals(item.type) && (item.id.contains(cmdQuery) || item.title.toLowerCase().contains(cmdQuery))) {
                    filteredItems.add(item);
                }
            }
        } else {
            // 普通搜索，优先搜命令和文件名
            for (PaletteItem item : allItems) {
                if (item.title.toLowerCase().contains(q)) {
                    filteredItems.add(item);
                }
            }
        }

        listCommands.setAdapter(new CommandAdapter());
    }

    private class CommandAdapter extends RecyclerView.Adapter<CommandAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_command, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            PaletteItem item = filteredItems.get(position);
            h.tvIcon.setText(item.icon);
            h.tvTitle.setText(item.title);
            h.tvDesc.setText(item.desc);
            h.itemView.setOnClickListener(v -> {
                dismiss();
                if ("file".equals(item.type)) {
                    listener.onFileSelected((File) item.payload);
                } else {
                    listener.onCommand(item.id, item.payload);
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredItems.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvTitle, tvDesc;
            VH(@NonNull View v) {
                super(v);
                tvIcon = v.findViewById(R.id.tvIcon);
                tvTitle = v.findViewById(R.id.tvTitle);
                tvDesc = v.findViewById(R.id.tvDesc);
            }
        }
    }
}