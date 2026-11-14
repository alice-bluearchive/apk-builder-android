package com.example.androidstudio;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class TerminalActivity extends AppCompatActivity {

    private TextView tvOutput, tvTitle;
    private EditText etInput;
    private ScrollView scrollOutput;
    private ImageButton btnBack, btnClear, btnHistoryUp, btnHistoryDown;

    private File projectDir;
    private String currentWorkingDir = "";
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        String projectName = getIntent().getStringExtra("project");
        if (projectName != null) {
            projectDir = ProjectStore.dir(projectName);
        } else {
            projectDir = new File("/sdcard");
        }

        bindViews();
        setupTerminal();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvOutput = findViewById(R.id.tvOutput);
        etInput = findViewById(R.id.etInput);
        scrollOutput = findViewById(R.id.scrollOutput);
        btnBack = findViewById(R.id.btnBack);
        btnClear = findViewById(R.id.btnClear);
        btnHistoryUp = findViewById(R.id.btnHistoryUp);
        btnHistoryDown = findViewById(R.id.btnHistoryDown);

        tvTitle.setText(projectDir.getName() + " - Terminal");
    }

    private void setupTerminal() {
        btnBack.setOnClickListener(v -> finish());
        btnClear.setOnClickListener(v -> tvOutput.setText(""));

        appendOutput("Welcome to APK Builder Terminal!");
        appendOutput("Project: " + projectDir.getAbsolutePath());
        appendOutput("Type 'help' for basic commands. Type 'cd <dir>' to navigate.\n");

        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                executeCommand(etInput.getText().toString().trim());
                etInput.setText("");
                return true;
            }
            return false;
        });

        btnHistoryUp.setOnClickListener(v -> {
            if (history.isEmpty()) return;
            if (historyIndex == -1) historyIndex = history.size() - 1;
            else if (historyIndex > 0) historyIndex--;
            etInput.setText(history.get(historyIndex));
            etInput.setSelection(etInput.getText().length());
        });

        btnHistoryDown.setOnClickListener(v -> {
            if (history.isEmpty() || historyIndex == -1) return;
            if (historyIndex < history.size() - 1) {
                historyIndex++;
                etInput.setText(history.get(historyIndex));
            } else {
                historyIndex = -1;
                etInput.setText("");
            }
            etInput.setSelection(etInput.getText().length());
        });
    }

    private void executeCommand(String cmdLine) {
        if (TextUtils.isEmpty(cmdLine)) return;
        if (isRunning) {
            appendOutput("Wait for current command to finish...");
            return;
        }

        // 记录历史
        history.add(cmdLine);
        historyIndex = -1;

        // 回显
        appendOutput("> " + cmdLine);

        // 拦截内置命令
        if (cmdLine.equals("clear") || cmdLine.equals("cls")) {
            tvOutput.setText("");
            return;
        }
        if (cmdLine.equals("exit")) {
            finish();
            return;
        }
        if (cmdLine.equals("help")) {
            appendOutput("Basic Linux commands supported via toybox:\n" +
                    "  ls, cat, cp, mv, rm, mkdir, grep, find, echo, touch, df, du\n" +
                    "  cd <dir>  - change directory\n" +
                    "  pwd       - print working directory\n" +
                    "  clear     - clear screen\n" +
                    "  exit      - close terminal");
            return;
        }

        // 处理 cd（因为 ProcessBuilder 的目录无法热更新，必须在 Java 层记录）
        if (cmdLine.startsWith("cd ")) {
            String target = cmdLine.substring(3).trim();
            handleCd(target);
            return;
        }
        if (cmdLine.equals("cd")) {
            currentWorkingDir = "";
            appendOutput("Now at: " + projectDir.getAbsolutePath());
            return;
        }
        if (cmdLine.equals("pwd")) {
            appendOutput(getFullPath(currentWorkingDir));
            return;
        }

        // 执行系统命令
        runProcess(cmdLine);
    }

    private void handleCd(String target) {
        File newDir;
        if (target.startsWith("/")) {
            newDir = new File(target);
        } else {
            newDir = new File(projectDir, currentWorkingDir + File.separator + target);
        }

        try {
            String canonicalProject = projectDir.getCanonicalPath();
            String canonicalNewDir = newDir.getCanonicalPath();

            // 安全沙盒：强制限制在 projectDir 内
            if (!canonicalNewDir.startsWith(canonicalProject)) {
                appendOutput("❌ Permission denied: Cannot leave project directory.");
                return;
            }
            if (!newDir.exists() || !newDir.isDirectory()) {
                appendOutput("❌ cd: no such directory: " + target);
                return;
            }

            // 计算相对路径
            if (canonicalNewDir.equals(canonicalProject)) {
                currentWorkingDir = "";
            } else {
                currentWorkingDir = canonicalNewDir.substring(canonicalProject.length() + 1);
            }
            appendOutput("Now at: " + canonicalNewDir);

        } catch (Exception e) {
            appendOutput("❌ " + e.getMessage());
        }
    }

    private String getFullPath(String relative) {
        if (relative.isEmpty()) return projectDir.getAbsolutePath();
        return new File(projectDir, relative).getAbsolutePath();
    }

    private void runProcess(String cmdLine) {
        isRunning = true;
        new Thread(() -> {
            try {
                // 使用 sh -c 来支持管道和重定向
                ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", cmdLine);
                pb.redirectErrorStream(true);

                // 设置当前运行目录
                File runDir = currentWorkingDir.isEmpty() ? projectDir : new File(projectDir, currentWorkingDir);
                pb.directory(runDir);

                // 注入 toybox 的 PATH
                pb.environment().put("PATH", "/system/bin:/system/xbin");
                pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());

                Process p = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    final String out = line;
                    runOnUiThread(() -> appendOutput(out));
                }
                p.waitFor();

            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> appendOutput("❌ Error: " + err));
            } finally {
                isRunning = false;
            }
        }).start();
    }

    private void appendOutput(String text) {
        tvOutput.append(text + "\n");
        scrollOutput.post(() -> scrollOutput.fullScroll(View.FOCUS_DOWN));
    }
}