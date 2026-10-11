package com.example.androidstudio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ProjectBuilder {

    public interface Callback {
        void log(String msg);
    }

    public interface Done {
        void onSuccess(File apk);
        void onError(String message, Exception e);
    }

    private final Context ctx;
    private final Callback cb;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ProjectBuilder(Context ctx, Callback cb) {
        this.ctx = ctx.getApplicationContext();
        this.cb = cb == null ? s -> {} : cb;
    }

    public void build(final String projectName, final Done done) {
        new Thread(() -> {
            try {
                File apk = doBuild(projectName);
                mainHandler.post(() -> done.onSuccess(apk));
            } catch (Exception e) {
                e.printStackTrace();
                final String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                mainHandler.post(() -> done.onError(msg, e));
            }
        }, "ProjectBuilder").start();
    }

    private File doBuild(String projectName) throws Exception {
        ProjectConfig cfg = ProjectStore.loadConfig(projectName);
        File projDir = ProjectStore.dir(projectName);
        SigningConfig signingConfig = SigningConfig.from(projDir, cfg);

        log("==== 开始构建: " + projectName + " ====");
        log("模式  : " + cfg.mode);
        log("包名  : " + cfg.pkg);
        log("应用名: " + cfg.appName);
        if (signingConfig.signingEnabled) {
            log("签名  : 自定义 keystore (" + cfg.keystoreType + ")");
        } else {
            log("签名  : debug 默认");
        }

        List<String> perms = new ArrayList<>();
        if (cfg.permInternet) perms.add("INTERNET");
        if (cfg.permStorage) perms.add("READ_EXTERNAL_STORAGE");
        if (cfg.permCamera) perms.add("CAMERA");
        if (cfg.permLocation) perms.add("ACCESS_FINE_LOCATION");
        if (!perms.isEmpty()) log("权限  : " + perms);

        File icon = null;
        if (cfg.iconFile != null && !cfg.iconFile.isEmpty()) {
            File f = new File(projDir, cfg.iconFile);
            if (f.exists()) icon = f;
        }

        File androidJar = ensureAndroidJar();

        ToolchainManager tm = new ToolchainManager(ctx);
        ApkBuilder builder = new ApkBuilder(ctx, tm, this::log);

        if (TemplateManager.TYPE_HTML.equals(cfg.mode)) {
            File idx = new File(projDir, "src/index.html");
            if (!idx.exists()) throw new IOException("缺少 src/index.html");
            log("--- HTML 模式 ---");
            return builder.build(idx, cfg.pkg, cfg.appName, icon, perms, signingConfig);
        } else {
            return buildJavaProject(builder, projDir, androidJar, cfg, icon, signingConfig);
        }
    }

    private File buildJavaProject(ApkBuilder builder, File projDir,
                                  File androidJar, ProjectConfig cfg,
                                  File icon, SigningConfig signingConfig) throws Exception {

        log("--- Step 0: 生成 R.java ---");
        File genDir = new File(ctx.getCacheDir(), "gen");
        deleteRec(genDir);
        genDir.mkdirs();
        builder.generateRJava(projDir, genDir);

        List<File> sources = new ArrayList<>();
        collectJava(new File(projDir, "src"), sources);
        collectJava(genDir, sources);
        log("Java 源文件: " + sources.size() + " 个");

        log("--- Step 1: ECJ 编译 ---");
        EcjCompiler ecj = new EcjCompiler(ctx, this::log);
        File classOut = new File(ctx.getCacheDir(), "class_out");
        deleteRec(classOut);
        classOut.mkdirs();
        boolean ok = ecj.compileFiles(sources, classOut, androidJar);
        if (!ok) throw new Exception("ECJ 编译失败");

        log("--- Step 2: D8 转 dex ---");
        D8Wrapper d8 = new D8Wrapper(this::log);
        File dexOut = new File(ctx.getCacheDir(), "dex_out");
        deleteRec(dexOut);
        dexOut.mkdirs();
        d8.classToDex(classOut, androidJar, dexOut);

        File[] dexes = dexOut.listFiles();
        if (dexes == null) throw new Exception("D8 输出为空");
        List<File> dexList = new ArrayList<>();
        for (File d : dexes) {
            if (d.getName().endsWith(".dex")) dexList.add(d);
        }
        if (dexList.isEmpty()) throw new Exception("D8 没生成 dex");
        log("生成 " + dexList.size() + " 个 dex");

        log("--- Step 3: 打包 APK ---");
        return builder.buildJavaProject(
                projDir, dexList.toArray(new File[0]), icon, cfg.appName,
                signingConfig);
    }

    private File ensureAndroidJar() throws IOException {
        File cached = new File(ctx.getFilesDir(), "android.jar");
        if (cached.exists() && cached.length() > 1_000_000) {
            log("android.jar 已就绪: " + cached.length() + " 字节");
            return cached;
        }
        log("首次解压 android.jar.zip ...");
        long t0 = System.currentTimeMillis();
        try (InputStream is = ctx.getAssets().open("template/android.jar.zip");
             java.util.zip.ZipInputStream zis =
                     new java.util.zip.ZipInputStream(
                             new java.io.BufferedInputStream(is));
             OutputStream os = new FileOutputStream(cached)) {
            byte[] buf = new byte[65536];
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                int n;
                while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                break;
            }
        }
        long dt = System.currentTimeMillis() - t0;
        log("android.jar 解压完成: " + cached.length()
                + " 字节, 耗时 " + (dt / 1000) + "s");
        return cached;
    }

    private static void collectJava(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectJava(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }

    private void log(String s) {
        android.util.Log.d("APKBUILDER", s);
        cb.log(s);
    }
}