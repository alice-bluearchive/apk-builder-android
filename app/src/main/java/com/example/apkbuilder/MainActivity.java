package com.example.apkbuilder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PICK_HTML = 1001;
    private static final int REQ_PICK_ICON = 1002;
    private static final String PREF = "settings";
    private static final String KEY_LANG = "lang";

    private RadioButton modeHtml;
    private EditText etPackage, etAppName;
    private Button btnPickIcon, btnPickSource, btnBuild, btnTestToolchain, btnBack;
    private Button btnLang, btnCopyLog, btnClearLog;
    private Switch swDisableOnBuild, swCleanTemp;
    private ImageView ivIcon;
    private CheckBox permInternet, permStorage, permCamera, permLocation;
    private EditText tvLog;
    private TextView tvSourceName, tvIconName, tvProjectName;

    private File selectedHtml = null;
    private File pickedIcon = null;
    private String projectName = null;
    private volatile boolean building = false;

    private String snapshotPkg = "";
    private String snapshotName = "";

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREF, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "system");
        if ("system".equals(lang)) {
            super.attachBaseContext(newBase);
            return;
        }
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        modeHtml = findViewById(R.id.modeHtml);
        etPackage = findViewById(R.id.etPackage);
        etAppName = findViewById(R.id.etAppName);
        btnPickIcon = findViewById(R.id.btnPickIcon);
        btnPickSource = findViewById(R.id.btnPickSource);
        btnBuild = findViewById(R.id.btnBuild);
        btnTestToolchain = findViewById(R.id.btnTestToolchain);
        btnLang = findViewById(R.id.btnLang);
        btnCopyLog = findViewById(R.id.btnCopyLog);
        btnClearLog = findViewById(R.id.btnClearLog);
        btnBack = findViewById(R.id.btnBack);
        ivIcon = findViewById(R.id.ivIcon);
        permInternet = findViewById(R.id.permInternet);
        permStorage = findViewById(R.id.permStorage);
        permCamera = findViewById(R.id.permCamera);
        permLocation = findViewById(R.id.permLocation);
        tvLog = findViewById(R.id.tvLog);
        tvSourceName = findViewById(R.id.tvSourceName);
        tvIconName = findViewById(R.id.tvIconName);
        tvProjectName = findViewById(R.id.tvProjectName);
        swDisableOnBuild = findViewById(R.id.swDisableOnBuild);
        swCleanTemp = findViewById(R.id.swCleanTemp);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        tvLog.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        btnBack.setOnClickListener(v -> finish());

        btnLang.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
            String cur = sp.getString(KEY_LANG, "system");
            String next;
            if ("zh".equals(cur)) next = "en";
            else if ("en".equals(cur)) next = "system";
            else next = "zh";
            sp.edit().putString(KEY_LANG, next).apply();
            recreate();
        });

        btnCopyLog.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("apkbuilder-log", tvLog.getText().toString()));
            toast(getString(R.string.copied));
        });

        btnClearLog.setOnClickListener(v -> {
            tvLog.setText("");
            toast(getString(R.string.logging_cleared));
        });

        log("APK Builder ready.");

        projectName = getIntent().getStringExtra("project");
        if (projectName != null) {
            tvProjectName.setText(projectName);
            loadProjectConfig();
        } else {
            tvProjectName.setText("(no project)");
        }

        btnPickIcon.setOnClickListener(v -> {
            if (building) return;
            saveSnapshot();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_icon_title)), REQ_PICK_ICON);
        });

        btnPickSource.setOnClickListener(v -> {
            if (building) return;
            saveSnapshot();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            if (modeHtml.isChecked()) {
                startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_html_title)), REQ_PICK_HTML);
            } else {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_java_title)), REQ_PICK_HTML);
            }
        });

        btnBuild.setOnClickListener(v -> {
            if (building) return;
            tvLog.setText("");
            restoreSnapshot();

            String pkg = etPackage.getText().toString().trim();
            String name = etAppName.getText().toString().trim();
            String mode = modeHtml.isChecked() ? "HTML" : "Java";

            log("---- Build clicked ----");
            log("mode = " + mode);
            log("pkg  = " + pkg);
            log("name = " + name);

            if (pkg.isEmpty() || !pkg.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")) {
                log("[ERR] 包名不合法: " + pkg);
                toast("请填写包名（如 com.example.myapp）");
                return;
            }

            if (name.isEmpty()) {
                log("[ERR] 应用名为空");
                toast("请填写应用名");
                return;
            }

            if (selectedHtml == null || !selectedHtml.exists()) {
                log("[ERR] 未选源文件");
                toast(getString(R.string.source_none));
                return;
            }

            // A. Java 模式 package 一致性检查
            if ("Java".equals(mode)) {
                String err = checkJavaPackage(pkg);
                if (err != null) {
                    log("[ERR] " + err);
                    toast(err);
                    return;
                }
            }

            List<String> perms = new ArrayList<>();
            if (permInternet.isChecked()) perms.add("INTERNET");
            if (permStorage.isChecked()) perms.add("READ_EXTERNAL_STORAGE");
            if (permCamera.isChecked()) perms.add("CAMERA");
            if (permLocation.isChecked()) perms.add("ACCESS_FINE_LOCATION");

            final File src = selectedHtml;
            final File icon = pickedIcon;
            final String fPkg = pkg;
            final String fName = name;
            final String fMode = mode;
            final List<String> fPerms = perms;

            if (projectName != null) {
                try {
                    ProjectConfig cfg = ProjectStore.loadConfig(projectName);
                    cfg.pkg = fPkg;
                    cfg.appName = fName;
                    cfg.mode = fMode;
                    cfg.permInternet = permInternet.isChecked();
                    cfg.permStorage = permStorage.isChecked();
                    cfg.permCamera = permCamera.isChecked();
                    cfg.permLocation = permLocation.isChecked();
                    ProjectStore.saveConfig(cfg);
                } catch (Exception e) {
                    log("保存 config 失败: " + e.getMessage());
                }
            }

            // D. 禁用按钮
            setBuilding(true);

            new Thread(() -> {
                try {
                    File androidJar = new File(getFilesDir(), "android.jar");
                    if (!androidJar.exists()) {
                        log("解压 android.jar ...");
                        try (InputStream in = getAssets().open("template/android.jar");
                             OutputStream out = new FileOutputStream(androidJar)) {
                            byte[] buf = new byte[65536];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                    }

                    ToolchainManager tm = new ToolchainManager(MainActivity.this);
                    ApkBuilder builder = new ApkBuilder(MainActivity.this, tm, MainActivity.this::log);

                    File result;
                    if ("HTML".equals(fMode)) {
                        result = builder.build(src, fPkg, fName, icon, fPerms);
                    } else {
                        log("=== Java mode ===");
                        log("--- Step 1: ECJ compile ---");
                        EcjCompiler ecj = new EcjCompiler(MainActivity.this, MainActivity.this::log);
                        File classOut = new File(getCacheDir(), "class_out");
                        deleteRec(classOut);
                        classOut.mkdirs();

                        File srcDir = src.getParentFile();
                        boolean ok = ecj.compileDir(srcDir, classOut, androidJar);
                        log("ECJ 结果: " + ok);
                        if (!ok) throw new Exception("ECJ 编译失败");

                        log("--- Step 2: D8 class -> dex ---");
                        D8Wrapper d8 = new D8Wrapper(MainActivity.this::log);
                        File dexOut = new File(getCacheDir(), "dex_out");
                        deleteRec(dexOut);
                        dexOut.mkdirs();
                        d8.classToDex(classOut, androidJar, dexOut);

                        File[] dexes = dexOut.listFiles();
                        if (dexes == null || dexes.length == 0) throw new Exception("D8 没生成 dex");
                        File userDex = dexes[0];
                        log("用户 dex: " + userDex.length() + " bytes");

                        log("--- Step 3: assemble APK ---");
                        result = builder.buildJava(userDex, fPkg, fName, icon, fPerms);
                    }

                    log("✓ Succeeded: " + result.getAbsolutePath());
                    final File finalResult = result;
                    runOnUiThread(() -> {
                        setBuilding(false);
                        showResultDialog(finalResult);
                    });
                } catch (Exception e) {
                    log("[ERR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    e.printStackTrace();
                    runOnUiThread(() -> setBuilding(false));
                }
            }).start();
        });

        btnTestToolchain.setOnClickListener(v -> testToolchain());
    }

    // D. 构建中禁用按钮
    private void setBuilding(boolean b) {
        building = b;
        if (swDisableOnBuild.isChecked()) {
            btnBuild.setEnabled(!b);
            btnPickSource.setEnabled(!b);
            btnPickIcon.setEnabled(!b);
            btnBack.setEnabled(!b);
        }
    }

    // A. 检查 Java 源文件的 package
    private String checkJavaPackage(String expectedPkg) {
        File dir = selectedHtml.getParentFile();
        if (dir == null) dir = new File(getCacheDir(), "java_src");
        File[] files = dir.listFiles();
        if (files == null) return null;
        Pattern p = Pattern.compile("^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;", Pattern.MULTILINE);
        for (File f : files) {
            if (!f.getName().endsWith(".java")) continue;
            String content = readText(f);
            Matcher m = p.matcher(content);
            if (m.find()) {
                String found = m.group(1);
                if (!found.equals(expectedPkg)) {
                    return getString(R.string.pkg_mismatch, f.getName(), found, expectedPkg);
                }
            }
        }
        return null;
    }

    // B. 显示结果 + 询问清理临时文件
    private void showResultDialog(File apk) {
        String path = apk.getAbsolutePath();
        String size = humanSize(apk.length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.build_success_title)
                .setMessage(getString(R.string.build_success_msg, path, size))
                .setPositiveButton(R.string.btn_install, (d, w) -> {
                    installApk(apk);
                    maybeCleanTemp();
                })
                .setNeutralButton(R.string.btn_open_dir, (d, w) -> {
                    openApkDir();
                    maybeCleanTemp();
                })
                .setNegativeButton(R.string.btn_close, (d, w) -> maybeCleanTemp())
                .show();
    }

    private void maybeCleanTemp() {
        if (!swCleanTemp.isChecked()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.clean_temp_title)
                .setMessage(R.string.clean_temp_msg)
                .setPositiveButton(R.string.btn_yes, (d, w) -> {
                    int n = 0;
                    File[] kids = getCacheDir().listFiles();
                    if (kids != null) {
                        for (File f : kids) {
                            String name = f.getName();
                            if (name.startsWith("build_") || name.equals("class_out") || name.equals("dex_out") || name.equals("ecj_out")) {
                                deleteRec(f);
                                n++;
                            }
                        }
                    }
                    log("已清理 " + n + " 个临时目录");
                    toast("已清理 " + n + " 个临时目录");
                })
                .setNegativeButton(R.string.btn_no, null)
                .show();
    }

    private void openApkDir() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload");
            intent.setDataAndType(uri, "resource/folder");
            startActivity(intent);
        } catch (Exception e) {
            toast("No file manager");
        }
    }

    private static String humanSize(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.US, "%.1f KB", n / 1024.0);
        return String.format(Locale.US, "%.2f MB", n / 1024.0 / 1024.0);
    }

    private void loadProjectConfig() {
        try {
            ProjectConfig cfg = ProjectStore.loadConfig(projectName);
            if (cfg.pkg != null && !cfg.pkg.isEmpty()) etPackage.setText(cfg.pkg);
            if (cfg.appName != null && !cfg.appName.isEmpty()) etAppName.setText(cfg.appName);
            if ("Java".equals(cfg.mode)) {
                RadioButton modeJava = findViewById(R.id.modeJava);
                modeJava.setChecked(true);
            }
            permInternet.setChecked(cfg.permInternet);
            permStorage.setChecked(cfg.permStorage);
            permCamera.setChecked(cfg.permCamera);
            permLocation.setChecked(cfg.permLocation);
            log("已加载项目配置: " + projectName);
        } catch (Exception e) {
            log("加载项目配置失败: " + e.getMessage());
        }
    }

    private void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }

    private void installApk(File apk) {
        try {
            File priv = new File(getExternalFilesDir(null), "apk");
            if (!priv.exists()) priv.mkdirs();
            File privApk = new File(priv, apk.getName());
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = new FileOutputStream(privApk)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }

            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", privApk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            toast(getString(R.string.install_permission_required));
            log("[ERR] install: " + e.getMessage());
        }
    }

    private void saveSnapshot() {
        snapshotPkg = etPackage.getText().toString();
        snapshotName = etAppName.getText().toString();
    }

    private void restoreSnapshot() {
        if (!snapshotPkg.equals(etPackage.getText().toString())) {
            etPackage.setText(snapshotPkg);
            etPackage.setSelection(snapshotPkg.length());
        }
        if (!snapshotName.equals(etAppName.getText().toString())) {
            etAppName.setText(snapshotName);
            etAppName.setSelection(snapshotName.length());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        restoreSnapshot();

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_PICK_HTML) {
            try {
                List<Uri> uris = new ArrayList<>();
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        uris.add(data.getClipData().getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }
                if (uris.isEmpty()) return;

                Uri firstUri = uris.get(0);
                log("Selected " + uris.size() + " file(s)");

                if (modeHtml.isChecked()) {
                    File dst = new File(getCacheDir(), "picked_index.html");
                    try (InputStream in = getContentResolver().openInputStream(firstUri);
                         OutputStream out = new FileOutputStream(dst)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    selectedHtml = dst;
                    String display = getDisplayName(firstUri, dst.getName());
                    tvSourceName.setText(getString(R.string.source_selected, display));
                    log("HTML: " + dst.getAbsolutePath() + " (" + dst.length() + " bytes)");
                    toast("已选: " + display);
                } else {
                    File javaDir = new File(getCacheDir(), "java_src");
                    deleteRec(javaDir);
                    javaDir.mkdirs();

                    File mainFile = null;
                    for (Uri u : uris) {
                        String display = getDisplayName(u, "unknown.java");
                        File dst = new File(javaDir, display);
                        try (InputStream in = getContentResolver().openInputStream(u);
                             OutputStream out = new FileOutputStream(dst)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                        log("  " + dst.getName() + " (" + dst.length() + " bytes)");
                        String content = readText(dst);
                        if (content.contains("onCreate") && (content.contains("Activity") || content.contains("AppCompatActivity"))) {
                            mainFile = dst;
                        }
                    }
                    if (mainFile == null) mainFile = new File(javaDir, getDisplayName(firstUri, "MainActivity.java"));
                    selectedHtml = mainFile;
                    tvSourceName.setText(getString(R.string.source_selected, uris.size() + " 个文件"));
                    toast("已选 " + uris.size() + " 个文件");
                }
            } catch (Exception e) {
                log("[ERR] read failed: " + e.getMessage());
                toast("Read failed: " + e.getMessage());
            }
        } else if (requestCode == REQ_PICK_ICON) {
            if (data.getData() == null) return;
            Uri uri = data.getData();
            log("Icon URI: " + uri);
            try {
                Bitmap src;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    src = BitmapFactory.decodeStream(in);
                }
                if (src == null) {
                    log("[ERR] icon decode failed");
                    toast("Icon decode failed");
                    return;
                }
                int w = src.getWidth();
                int h = src.getHeight();
                float scale = Math.min(192f / w, 192f / h);
                if (scale > 1f) scale = 1f;
                int nw = Math.max(1, Math.round(w * scale));
                int nh = Math.max(1, Math.round(h * scale));
                Bitmap scaled = Bitmap.createScaledBitmap(src, nw, nh, true);

                File dst = new File(getCacheDir(), "picked_icon.png");
                try (FileOutputStream fos = new FileOutputStream(dst)) {
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                if (scaled != src) scaled.recycle();
                src.recycle();

                pickedIcon = dst;
                ivIcon.setImageBitmap(BitmapFactory.decodeFile(dst.getAbsolutePath()));
                String display = getDisplayName(uri, dst.getName());
                tvIconName.setText(getString(R.string.icon_selected, display));
                log("Icon: " + nw + "x" + nh + " -> " + dst.length() + " bytes");
                toast("图标已选");
            } catch (Exception e) {
                log("[ERR] icon failed: " + e.getMessage());
                toast("Icon failed: " + e.getMessage());
            }
        }
    }

    private String readText(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
    }

    private String getDisplayName(Uri uri, String fallback) {
        try {
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (c.moveToFirst() && idx >= 0) {
                    String name = c.getString(idx);
                    c.close();
                    return name != null ? name : fallback;
                }
                c.close();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private void testToolchain() {
        String nat = getApplicationInfo().nativeLibraryDir;
        log("nativeLibraryDir = " + nat);

        new Thread(() -> {
            ToolchainManager tm = new ToolchainManager(MainActivity.this);

            log("========== aapt2 ==========");
            File aapt2 = tm.getTool("aapt2");
            if (aapt2 == null) log("[FAIL] libaapt2.so not found");
            else {
                log("path=" + aapt2 + " canExec=" + aapt2.canExecute());
                log(tm.exec(java.util.Arrays.asList(aapt2.getAbsolutePath(), "version")));
            }

            log("========== zipalign ==========");
            File za = tm.getTool("zipalign");
            if (za == null) log("[FAIL] libzipalign.so not found");
            else {
                log("path=" + za + " canExec=" + za.canExecute());
                log(tm.exec(java.util.Arrays.asList(za.getAbsolutePath())));
            }
        }).start();
    }

    private void log(String line) {
        Log.d("APKBUILDER", line);
        runOnUiThread(() -> tvLog.append(line + "\n"));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}