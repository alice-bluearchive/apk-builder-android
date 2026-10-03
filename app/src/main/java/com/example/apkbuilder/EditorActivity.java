package com.example.apkbuilder;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorActivity extends AppCompatActivity {

    private WebView webEditor;
    private TextView tvEditorFile;
    private Button btnEditorBack, btnEditorSave;

    private File editingFile = null;
    private String originalText = "";
    private boolean contentLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        webEditor = findViewById(R.id.webEditor);
        tvEditorFile = findViewById(R.id.tvEditorFile);
        btnEditorBack = findViewById(R.id.btnEditorBack);
        btnEditorSave = findViewById(R.id.btnEditorSave);

        String path = getIntent().getStringExtra("file");
        if (path == null) {
            toast("No file");
            finish();
            return;
        }
        editingFile = new File(path);
        if (!editingFile.exists()) {
            toast("File not found");
            finish();
            return;
        }

        tvEditorFile.setText(editingFile.getName());

        try {
            originalText = readText(editingFile);
        } catch (Exception e) {
            originalText = "";
        }

        WebSettings ws = webEditor.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        webEditor.setWebViewClient(new WebViewClient());

        webEditor.addJavascriptInterface(new Bridge(), "AndroidBridge");

        webEditor.loadUrl("file:///android_asset/editor/editor.html");

        btnEditorSave.setOnClickListener(v -> save());
        btnEditorBack.setOnClickListener(v -> confirmExit());
    }

    private String detectMode(String name) {
        if (name.endsWith(".java")) return "text/x-java";
        if (name.endsWith(".html") || name.endsWith(".htm")) return "htmlmixed";
        if (name.endsWith(".js")) return "javascript";
        if (name.endsWith(".css")) return "css";
        if (name.endsWith(".xml")) return "xml";
        return "text/plain";
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String unescapeJs(String s) {
        if (s == null || s.equals("null")) return "";
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private void loadIntoEditor() {
        String mode = detectMode(editingFile.getName());
        String esc = escapeJs(originalText);
        String js = "window.loadContent('" + esc + "', '" + mode + "');";
        webEditor.evaluateJavascript(js, null);
        contentLoaded = true;
    }

    /** 从 .java 内容里提取 public class 名 */
    private String extractJavaClassName(String content) {
        // 匹配 public class XXX 或 class XXX
        Pattern p = Pattern.compile(
                "(?:public\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)");
        Matcher m = p.matcher(content);
        if (m.find()) return m.group(1);
        return null;
    }

    /** 保存时：如果是 .java，自动把文件名改成 ClassName.java */
    private File resolveSaveTarget(String content) {
        if (!editingFile.getName().endsWith(".java")) {
            return editingFile;
        }
        String cls = extractJavaClassName(content);
        if (cls == null) return editingFile;

        String expected = cls + ".java";
        if (editingFile.getName().equals(expected)) {
            return editingFile;
        }
        // 需要重命名
        File newFile = new File(editingFile.getParentFile(), expected);
        return newFile;
    }

    private void save() {
        webEditor.evaluateJavascript("window.getContent();", value -> {
            String text = unescapeJs(value);
            try {
                File target = resolveSaveTarget(text);

                // 写内容
                writeText(target, text);

                // 如果目标文件和原文件不同，删除原文件
                if (!target.getAbsolutePath().equals(editingFile.getAbsolutePath())) {
                    if (editingFile.exists()) editingFile.delete();
                    editingFile = target;
                    tvEditorFile.setText(editingFile.getName());
                }

                originalText = text;
                toast("Saved: " + editingFile.getName());
            } catch (Exception e) {
                toast("Save failed: " + e.getMessage());
            }
        });
    }

    private void confirmExit() {
        webEditor.evaluateJavascript("window.getContent();", value -> {
            String text = unescapeJs(value);
            boolean changed = !text.equals(originalText);

            if (!changed) {
                finish();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.editor_unsaved_title)
                    .setMessage(R.string.editor_unsaved_msg)
                    .setPositiveButton(R.string.editor_save, (d, w) -> {
                        save();
                        finish();
                    })
                    .setNegativeButton(R.string.editor_discard, (d, w) -> finish())
                    .setNeutralButton(R.string.btn_cancel, null)
                    .show();
        });
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    private String readText(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            return new String(buf, "UTF-8");
        }
    }

    private void writeText(File f, String content) throws Exception {
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(content.getBytes("UTF-8"));
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    public class Bridge {
        @JavascriptInterface
        public void onReady() {
            runOnUiThread(() -> {
                if (!contentLoaded) loadIntoEditor();
            });
        }
    }
}