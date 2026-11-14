package com.example.androidstudio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.androidstudio.build.BuildConfig;
import com.example.androidstudio.build.BuildErrorExporter;
import com.example.androidstudio.build.BuildStage;
import com.example.androidstudio.build.ErrorCollector;
import com.example.androidstudio.build.GradleConfigParser;
import com.example.androidstudio.build.ProgressReporter;
import com.example.androidstudio.build.ProjectLayout;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ProjectBuilder {
    public interface Callback { void log(String msg); }
    public interface Done { void onSuccess(File apk); void onError(String message, Exception e); }

    private final Context ctx;
    private final Callback cb;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ProgressReporter reporter;
    private ErrorCollector errorCollector;
    private ProjectLayout layout;
    private BuildConfig parsedConfig;
    private Throwable lastFatal;

    public ProjectBuilder(Context ctx, Callback cb) {
        this.ctx = ctx.getApplicationContext();
        this.cb = cb == null ? s -> {} : cb;
    }

    public void setProgressReporter(ProgressReporter reporter) { this.reporter = reporter; }

    public void build(final String projectName, final Done done) {
        new Thread(() -> {
            try {
                File apk = doBuild(projectName);
                mainHandler.post(() -> done.onSuccess(apk));
            } catch (Throwable e) {
                e.printStackTrace();
                lastFatal = e;
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                final Exception err = (e instanceof Exception) ? (Exception) e : new Exception(e);
                mainHandler.post(() -> done.onError(msg, err));
            }
        }, "ProjectBuilder").start();
    }

    public File exportErrorReport() throws Exception {
        if (errorCollector == null) return null;
        return BuildErrorExporter.export(layout, parsedConfig, errorCollector, lastFatal);
    }

    public List<ErrorCollector.Line> getCollectedErrors() {
        return errorCollector == null ? new ArrayList<>() : errorCollector.all();
    }

    private File doBuild(String projectName) throws Exception {
        ProjectConfig cfg = ProjectStore.loadConfig(projectName);
        File projDir = ProjectStore.dir(projectName);
        SigningConfig signingConfig = SigningConfig.from(projDir, cfg);

        errorCollector = new ErrorCollector();
        layout = ProjectLayout.detect(projDir);

        try {
            parsedConfig = GradleConfigParser.parse(layout);
            if (parsedConfig.applicationId != null && !parsedConfig.applicationId.isEmpty()) cfg.pkg = parsedConfig.applicationId;
        } catch (Exception e) {
            parsedConfig = new BuildConfig();
            parsedConfig.applicationId = cfg.pkg;
        }

        log("==== 开始构建: " + projectName + " ====");
        log("模式  : " + cfg.mode);
        log("包名  : " + cfg.pkg);

        List<String> perms = new ArrayList<>();
        if (cfg.permInternet) perms.add("INTERNET");
        if (cfg.permStorage) perms.add("READ_EXTERNAL_STORAGE");
        if (cfg.permCamera) perms.add("CAMERA");
        if (cfg.permLocation) perms.add("ACCESS_FINE_LOCATION");

        File icon = null;
        if (cfg.iconFile != null && !cfg.iconFile.isEmpty()) {
            File f = new File(projDir, cfg.iconFile);
            if (f.exists()) icon = f;
        }

        File androidJar = ensureAndroidJar();
        ToolchainManager tm = new ToolchainManager(ctx);
        ApkBuilder builder = new ApkBuilder(ctx, tm, this::log);

        if (TemplateManager.TYPE_HTML.equals(cfg.mode)) {
            File idx = new File(projDir, "src/main/assets/index.html");
            if (!idx.exists()) idx = new File(projDir, "src/index.html"); // 兼容老位置
            if (!idx.exists()) throw new IOException("缺少 index.html");
            return builder.build(idx, cfg.pkg, cfg.appName, icon, perms, signingConfig, reporter);
        } else {
            return buildJavaProject(builder, projDir, androidJar, cfg, icon, signingConfig);
        }
    }

    private File buildJavaProject(ApkBuilder builder, File projDir, File androidJar, ProjectConfig cfg, File icon, SigningConfig signingConfig) throws Exception {
        log("--- Step 0: 生成 R.java ---");
        File genDir = new File(ctx.getCacheDir(), "gen");
        ProjectStore.deleteRec(genDir);
        genDir.mkdirs();

        builder.generateRJava(projDir, genDir, cfg.pkg);

        File sourceRoot = layout.isGradle() ? layout.javaSrc : new File(projDir, "src");
        List<File> sources = new ArrayList<>();
        collectJava(sourceRoot, sources);
        collectJava(genDir, sources);

        boolean hasR = false;
        for (File f : sources) {
            if (f.getName().equals("R.java")) { hasR = true; log("✅ 找到 R.java"); break; }
        }
        if (!hasR) throw new Exception("R.java 生成失败！请检查 AndroidManifest.xml 或 res 目录。");

        if (reporter != null) reporter.onStageStart(BuildStage.ECJ);
        EcjCompiler ecj = new EcjCompiler(ctx, this::log);
        File classOut = new File(ctx.getCacheDir(), "class_out");
        ProjectStore.deleteRec(classOut);
        classOut.mkdirs();
        boolean ok = ecj.compileFiles(sources, classOut, androidJar);
        if (reporter != null) reporter.onStageEnd(BuildStage.ECJ);
        if (!ok) throw new Exception("ECJ 编译失败");

        if (reporter != null) reporter.onStageStart(BuildStage.D8);
        D8Wrapper d8 = new D8Wrapper(this::log);
        File dexOut = new File(ctx.getCacheDir(), "dex_out");
        ProjectStore.deleteRec(dexOut);
        dexOut.mkdirs();
        d8.classToDex(classOut, androidJar, dexOut);
        if (reporter != null) reporter.onStageEnd(BuildStage.D8);

        File[] dexes = dexOut.listFiles();
        List<File> dexList = new ArrayList<>();
        if (dexes != null) for (File d : dexes) if (d.getName().endsWith(".dex")) dexList.add(d);
        if (dexList.isEmpty()) throw new Exception("D8 没生成 dex");

        return builder.buildJavaProject(projDir, dexList.toArray(new File[0]), icon, cfg.appName, signingConfig, reporter);
    }

    private File ensureAndroidJar() throws IOException {
        File jar = new File(ctx.getCacheDir(), "android_build.jar");
        if (jar.exists() && jar.length() > 1_000_000) return jar;

        try (InputStream is = ctx.getAssets().open("template/android.jar.zip");
             java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new BufferedInputStream(is));
             OutputStream os = new FileOutputStream(jar)) {
            byte[] buf = new byte[65536];
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                int n;
                while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                break;
            }
        }
        return jar;
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

    private void log(String s) {
        cb.log(s);
        if (errorCollector != null) errorCollector.append(getStageFromText(s), s);
    }

    private BuildStage getStageFromText(String s) {
        if (s.contains("ECJ")) return BuildStage.ECJ;
        if (s.contains("D8")) return BuildStage.D8;
        if (s.contains("aapt2")) return BuildStage.AAPT2;
        if (s.contains("zipalign")) return BuildStage.ZIPALIGN;
        if (s.contains("签名")) return BuildStage.SIGN;
        return null;
    }
}