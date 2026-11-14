package com.example.androidstudio;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProjectStore {

    public static final String BASE_DIR = "/storage/emulated/0/apk-project";
    public static final String CONFIG_FILE = "config.json";
    public static final String OUT_DIR = "output";

    public static File base() { return new File(Environment.getExternalStorageDirectory(), "apk-project"); }
    public static File dir(String name) { return new File(base(), name); }
    public static File srcDir(String name) { return new File(dir(name), "src/main/java"); }

    public static boolean exists(String name) { return dir(name).isDirectory(); }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        File b = base();
        if (b.exists()) {
            File[] kids = b.listFiles();
            if (kids != null) for (File f : kids) if (f.isDirectory()) out.add(f.getName());
        }
        Collections.sort(out);
        return out;
    }

    public static void create(String name, String mode) throws IOException {
        // 这个方法废弃不用了，改用下面这个新的
    }

    // 新增支持中英文的创建方法
    public static void create(String projectName, String mode, String appName, String pkg) throws IOException {
        if (mode == null || mode.isEmpty()) mode = TemplateManager.TYPE_HTML;
        File d = dir(projectName);
        if (d.exists()) throw new IOException("Project exists");
        if (!d.mkdirs()) throw new IOException("Cannot create dir: " + d);

        // 生成标准目录
        File mainDir = new File(d, "src/main");
        File javaDir = new File(mainDir, "java/" + pkg.replace('.', '/'));
        File resDir = new File(mainDir, "res");
        File assetsDir = new File(mainDir, "assets");

        javaDir.mkdirs();
        new File(resDir, "layout").mkdirs();
        new File(resDir, "values").mkdirs();
        assetsDir.mkdirs();
        new File(d, OUT_DIR).mkdirs();

        ProjectConfig c = new ProjectConfig();
        c.name = projectName;
        c.mode = mode;
        c.appName = appName; // 中文应用名
        c.pkg = pkg;         // 英文包名
        saveConfig(c);

        // 生成 build.gradle
        String gradle = "plugins { id 'com.android.application' }\n" +
                "android {\n namespace '" + pkg + "'\n compileSdk 34\n" +
                "defaultConfig { applicationId \"" + pkg + "\"\n minSdk 24\n targetSdk 34\n versionCode 1\n versionName \"1.0\" }\n}\n";
        try (OutputStream os = new FileOutputStream(new File(d, "build.gradle"))) {
            os.write(gradle.getBytes("UTF-8"));
        }

        if (TemplateManager.TYPE_HTML.equals(mode)) {
            String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + appName + "</title></head><body><h1>Hello " + appName + "</h1></body></html>";
            try (OutputStream os = new FileOutputStream(new File(assetsDir, "index.html"))) { os.write(html.getBytes("UTF-8")); }
            String mf = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"" + pkg + "\">\n<application android:label=\"" + appName + "\"/>\n</manifest>\n";
            try (OutputStream os = new FileOutputStream(new File(mainDir, "AndroidManifest.xml"))) { os.write(mf.getBytes("UTF-8")); }
        } else {
            try {
                TemplateManager tm = new TemplateManager(AppContext.get());
                tm.apply(mode, d, pkg, appName);
            } catch (Exception e) { throw new IOException(e.getMessage(), e); }
        }
    }

    public static ProjectConfig loadConfig(String name) {
        File f = new File(dir(name), CONFIG_FILE);
        if (!f.exists()) { ProjectConfig c = new ProjectConfig(); c.name = name; return c; }
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) { int n = in.read(buf, off, buf.length - off); if (n < 0) break; off += n; }
            return ProjectConfig.fromJson(new String(buf, 0, off, "UTF-8"));
        } catch (Exception e) { return new ProjectConfig(); }
    }

    public static void saveConfig(ProjectConfig c) throws IOException {
        File f = new File(dir(c.name), CONFIG_FILE);
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(c.toJson().toString(2).getBytes("UTF-8"));
        } catch (Exception e) { throw new IOException(e); }
    }

    public static void delete(String name) { deleteRec(dir(name)); }

    public static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }
}