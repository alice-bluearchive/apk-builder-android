package com.example.apkbuilder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

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

    private RadioButton modeHtml;
    private EditText etPackage, etAppName;
    private MaterialButton btnBuild, btnPickSource, btnEditFiles, btnPickIcon;
    private Button btnTestToolchain, btnBack;
    private Button btnLang, btnCopyLog, btnClearLog;
    private ImageView ivIcon;
    private CheckBox permInternet, permStorage, permCamera, permLocation;
    private EditText tvLog;
    private TextView tvSourceName, tvIconName, tvProjectName;

    private File selectedHtml = null;
    private File pickedIcon = null;
    private String projectName = null;
    private volatile boolean building = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        modeHtml = findViewById(R.id.modeHtml);
        etPackage = findViewById(R.id.etPackage);
        etAppName = findViewById(R.id.etAppName);
        btnPickIcon = findViewById(R.id.btnPickIcon);
        btnPickSource = findViewById(R.id.btnPickSource);
        btnEditFiles = findViewById(R.id.btnEditFiles);
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
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        tvLog.setKeyListener(null);

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

        btnLang.setOnClickListener(v -> LocaleHelper.toggle(this));

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
            updateSourceLabel();
        } else {
            tvProjectName.setText("(no project)");
        }

        btnPickIcon.setOnClickListener(v -> {
            if (building) return;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_icon_title)), REQ_PICK_ICON);
        });

        btnPickSource.setOnClickListener(v -> {
            if (building) return;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            if (modeHtml.isChecked()) {
                startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_html_title)), REQ_PICK_HTML);
            } else {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_java_title)), REQ_PICK_HTML);
            }
        });

        btnEditFiles.setOnClickListener(v -> {
            if (building) return;
            if (projectName == null) {
                toast("No project");
                return;
            }
            File src = ProjectStore.srcDir(projectName);
            if (!src.isDirectory()) {
                toast("No src dir");
                return;
            }
            File[] files = src.listFiles();
            if (files == null || files.length == 0) {
                toast("No files in src/");
                return;
            }
            String[] names = new String[files.length];
            for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.btn_edit_file)
                    .setItems(names, (d, idx) -> {
                        Intent i = new Intent(MainActivity.this, EditorActivity.class);
                        i.putExtra("file", files[idx].getAbsolutePath());
                        startActivity(i);
                    })
                    .show();
        });

        btnBuild.setOnClickListener(v -> {
            if (building) return;
            tvLog.setText("");

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

            File srcFile = null;
            if (projectName != null) {
                File projSrc = ProjectStore.srcDir(projectName);
                if ("HTML".equals(mode)) {
                    File idx = new File(projSrc, "index.html");
                    if (idx.exists()) srcFile = idx;
                } else {
                    File[] fs = projSrc.listFiles();
                    if (fs != null) {
                        for (File f : fs) {
                            if (f.getName().endsWith(".java")) {
                                srcFile = f;
                                break;
                            }
                        }
                    }
                }
            }
            if (srcFile == null) srcFile = selectedHtml;

            if (srcFile == null || !srcFile.exists()) {
                log("[ERR] 项目 src/ 里没有源文件");
                toast("请先在 src/ 里放源文件");
                return;
            }
            selectedHtml = srcFile;

            if ("Java".equals(mode) && projectName != null) {
                File srcDir = ProjectStore.srcDir(projectName);
                int changed = syncPackageToJava(srcDir, pkg);
                if (changed > 0) {
                    log("已同步 package 到 " + changed + " 个 .java 文件");
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

            setBuilding(true);

            new Thread(() -> {
                try {
                    // === 解压 android.jar（首次）===
                    File androidJar = new File(getFilesDir(), "android.jar");
                    if (!androidJar.exists() || androidJar.length() < 1_000_000) {
                        log("首次解压 android.jar.zip ...");
                        long t0 = System.currentTimeMillis();
                        try (InputStream is = getAssets().open("template/android.jar.zip");
                             java.util.zip.ZipInputStream zis =
                                     new java.util.zip.ZipInputStream(
                                             new java.io.BufferedInputStream(is));
                             OutputStream out = new FileOutputStream(androidJar)) {
                            byte[] buf = new byte[65536];
                            java.util.zip.ZipEntry entry;
                            while ((entry = zis.getNextEntry()) != null) {
                                if (entry.isDirectory()) continue;
                                int n;
                                while ((n = zis.read(buf)) > 0) out.write(buf, 0, n);
                                break;
                            }
                        }
                        long dt = System.currentTimeMillis() - t0;
                        log("android.jar 解压完成: " + androidJar.length() + " bytes, 耗时 " + (dt / 1000) + "s");
                    } else {
                        log("android.jar 已就绪: " + androidJar.length() + " bytes");
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

    private void setBuilding(boolean b) {
        building = b;
        btnBuild.setEnabled(!b);
        btnPickSource.setEnabled(!b);
        btnPickIcon.setEnabled(!b);
        btnBack.setEnabled(!b);
        btnEditFiles.setEnabled(!b);
        if (b) btnBuild.setText(R.string.build_running);
        else btnBuild.setText(R.string.btn_build);
    }

    private void updateSourceLabel() {
        if (projectName == null) {
            tvSourceName.setText(R.string.source_none);
            return;
        }
        File src = ProjectStore.srcDir(projectName);
        File[] files = src.listFiles();
        int n = 0;
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) n++;
            }
        }
        if (n == 0) {
            tvSourceName.setText(R.string.source_none);
        } else {
            tvSourceName.setText(getString(R.string.source_selected, n + " 个文件 (src/)"));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSourceLabel();
    }

    private int syncPackageToJava(File dir, String pkg) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        Pattern p = Pattern.compile("^\\s*package\\s+[A-Za-z_][A-Za-z0-9_.]*\\s*;", Pattern.MULTILINE);
        int count = 0;
        for (File f : files) {
            if (!f.getName().endsWith(".java")) continue;
            String content = readText(f);
            Matcher m = p.matcher(content);
            if (m.find()) {
                String oldLine = m.group().trim();
                String newLine = "package " + pkg + ";";
                if (!oldLine.equals(newLine)) {
                    content = m.replaceFirst(Matcher.quoteReplacement(newLine));
                    try (OutputStream os = new FileOutputStream(f)) {
                        os.write(content.getBytes("UTF-8"));
                    } catch (Exception ex) {
                        log("  写入失败 " + f.getName() + ": " + ex.getMessage());
                        continue;
                    }
                    log("  " + f.getName() + ": " + oldLine + " -> " + newLine);
                    count++;
                }
            }
        }
        return count;
    }

    private void showResultDialog(File apk) {
        String path = apk.getAbsolutePath();
        String size = humanSize(apk.length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.build_success_title)
                .setMessage(getString(R.string.build_success_msg, path, size))
                .setPositiveButton(R.string.btn_install, (d, w) -> {
                    installApk(apk);
                    promptCleanTemp();
                })
                .setNeutralButton(R.string.btn_open_dir, (d, w) -> {
                    openApkDir();
                    promptCleanTemp();
                })
                .setNegativeButton(R.string.btn_close, (d, w) -> promptCleanTemp())
                .show();
    }

    private void promptCleanTemp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clean_temp_title)
                .setMessage(R.string.clean_temp_msg)
                .setPositiveButton(R.string.btn_yes, (d, w) -> {
                    int n = 0;
                    File[] kids = getCacheDir().listFiles();
                    if (kids != null) {
                        for (File f : kids) {
                            String name = f.getName();
                            if (name.startsWith("build_") || name.equals("class_out")
                                    || name.equals("dex_out") || name.equals("ecj_out")
                                    || name.equals("java_src")) {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

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

                File destDir;
                if (projectName != null) {
                    destDir = ProjectStore.srcDir(projectName);
                } else {
                    destDir = new File(getCacheDir(), "java_src");
                }
                destDir.mkdirs();

                if (modeHtml.isChecked()) {
                    File old = new File(destDir, "index.html");
                    if (old.exists()) {
                        old.delete();
                        log("已删除旧 src/index.html");
                    }
                    File dst = new File(destDir, "index.html");
                    try (InputStream in = getContentResolver().openInputStream(firstUri);
                         OutputStream out = new FileOutputStream(dst)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    selectedHtml = dst;
                    log("已覆盖到 src/index.html: " + dst.length() + " bytes");
                    toast("已覆盖 src/index.html");
                    updateSourceLabel();
                } else {
                    File[] old = destDir.listFiles();
                    int removed = 0;
                    if (old != null) {
                        for (File f : old) {
                            if (f.getName().endsWith(".java")) {
                                f.delete();
                                removed++;
                            }
                        }
                    }
                    log("已删除旧 .java 文件: " + removed + " 个");

                    int copied = 0;
                    File firstDst = null;
                    for (Uri u : uris) {
                        String display = getDisplayName(u, "unknown.java");
                        if (!display.endsWith(".java")) display = display + ".java";
                        File dst = new File(destDir, display);
                        try (InputStream in = getContentResolver().openInputStream(u);
                             OutputStream out = new FileOutputStream(dst)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                        log("  src/" + dst.getName() + " (" + dst.length() + " bytes)");
                        if (firstDst == null) firstDst = dst;
                        copied++;
                    }
                    selectedHtml = firstDst;
                    toast("已覆盖 " + copied + " 个 .java 到 src/");
                    updateSourceLabel();
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