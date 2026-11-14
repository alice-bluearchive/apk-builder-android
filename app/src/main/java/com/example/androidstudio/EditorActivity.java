package com.example.androidstudio;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class EditorActivity extends AppCompatActivity {

    private WebView webEditor;
    private TextView tvFileName, tvDirty, tvCursor, tvLang;
    private String filePath;
    private boolean isDirty = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏系统状态栏（沉浸式）
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        setContentView(R.layout.activity_editor);

        filePath = getIntent().getStringExtra("path");
        if (filePath == null) {
            finish();
            return;
        }

        File f = new File(filePath);
        tvFileName = findViewById(R.id.tvFileName);
        tvFileName.setText(f.getName());

        tvDirty = findViewById(R.id.tvDirty);
        tvCursor = findViewById(R.id.tvCursor);
        tvLang = findViewById(R.id.tvLang);

        // 识别语言
        String name = f.getName().toLowerCase();
        if (name.endsWith(".java")) tvLang.setText("Java");
        else if (name.endsWith(".xml")) tvLang.setText("XML");
        else if (name.endsWith(".html")) tvLang.setText("HTML");
        else if (name.endsWith(".js")) tvLang.setText("JS");
        else tvLang.setText("Text");

        webEditor = findViewById(R.id.webEditor);
        setupWebView();

        // 按钮绑定
        findViewById(R.id.btnClose).setOnClickListener(v -> checkUnsavedAndFinish());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveFile());

        findViewById(R.id.btnUndo).setOnClickListener(v -> webEditor.evaluateJavascript("editor.undo();", null));
        findViewById(R.id.btnRedo).setOnClickListener(v -> webEditor.evaluateJavascript("editor.redo();", null));
        findViewById(R.id.btnFormat).setOnClickListener(v -> webEditor.evaluateJavascript("window.formatCode();", null));
        findViewById(R.id.btnComment).setOnClickListener(v -> webEditor.evaluateJavascript("window.toggleComment();", null));
    }

    private void setupWebView() {
        WebSettings ws = webEditor.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        webEditor.setFocusable(true);
        webEditor.setFocusableInTouchMode(true);
        webEditor.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadFileContent();
            }
        });
        webEditor.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webEditor.loadUrl("file:///android_asset/editor/editor_multi.html");
    }

    private void loadFileContent() {
        try {
            String content = readText(new File(filePath));
            String mode = modeForFile(filePath);
            String js = "window.openFile(" + escapeJsString(filePath) + "," + escapeJsString(content) + "," + escapeJsString(mode) + ");";
            webEditor.evaluateJavascript(js, null);

            // 强制重置状态
            isDirty = false;
            tvDirty.setVisibility(View.GONE);

            webEditor.postDelayed(() -> {
                isDirty = false;
                tvDirty.setVisibility(View.GONE);
            }, 500);

        } catch (Exception e) {
            Toast.makeText(this, "打开失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void saveFile() {
        webEditor.evaluateJavascript("window.getContent();", value -> {
            String content = unescapeJs(value);
            try {
                writeText(new File(filePath), content);
                isDirty = false;
                tvDirty.setVisibility(View.GONE);
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkUnsavedAndFinish() {
        if (!isDirty) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("未保存的更改")
                .setMessage("是否保存后退出？")
                .setPositiveButton("保存并退出", (d, w) -> {
                    webEditor.evaluateJavascript("window.getContent();", value -> {
                        String content = unescapeJs(value);
                        try {
                            writeText(new File(filePath), content);
                        } catch (Exception ignored) {}
                        finish();
                    });
                })
                .setNegativeButton("直接退出", (d, w) -> finish())
                .setNeutralButton("取消", null)
                .show();
    }

    public class Bridge {
        @JavascriptInterface
        public void onCursorChange(final int line, final int col) {
            runOnUiThread(() -> tvCursor.setText("Ln " + line + ", Col " + col));
        }

        @JavascriptInterface
        public void onDocChanged(String path) {
            runOnUiThread(() -> {
                if (!isDirty) {
                    isDirty = true;
                    tvDirty.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private static String modeForFile(String path) {
        String n = path.toLowerCase();
        if (n.endsWith(".java")) return "text/x-java";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".xml")) return "application/xml";
        if (n.endsWith(".html") || n.endsWith(".htm")) return "htmlmixed";
        if (n.endsWith(".js")) return "javascript";
        if (n.endsWith(".css")) return "css";

        // 补上 C/C++ 高亮
        if (n.endsWith(".c") || n.endsWith(".h") || n.endsWith(".cpp") || n.endsWith(".hpp") || n.endsWith(".cc")) {
            return "text/x-c++src";
        }

        return "text/plain";
    }

    private static String readText(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            return new String(buf, 0, off, "UTF-8");
        }
    }

    private static void writeText(File f, String content) throws Exception {
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(content.getBytes("UTF-8"));
        }
    }

    private static String escapeJsString(String s) {
        if (s == null) return "''";
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("'");
        return sb.toString();
    }

    private static String unescapeJs(String s) {
        if (s == null || s.equals("null")) return "";
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case 'n': sb.append('\n'); i += 2; break;
                    case 'r': sb.append('\r'); i += 2; break;
                    case 't': sb.append('\t'); i += 2; break;
                    case 'b': sb.append('\b'); i += 2; break;
                    case 'f': sb.append('\f'); i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    case '"': sb.append('"'); i += 2; break;
                    case '\'': sb.append('\''); i += 2; break;
                    case '/': sb.append('/'); i += 2; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                int code = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                sb.append((char) code);
                                i += 6;
                            } catch (NumberFormatException e) {
                                sb.append(n); i += 2;
                            }
                        } else {
                            sb.append(n); i += 2;
                        }
                        break;
                    default: sb.append(n); i += 2;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}