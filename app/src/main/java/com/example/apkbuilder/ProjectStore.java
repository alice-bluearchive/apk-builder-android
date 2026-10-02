package com.example.apkbuilder;

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
    public static final String SRC_DIR = "src";
    public static final String OUT_DIR = "output";

    public static File base() {
        return new File(Environment.getExternalStorageDirectory(), "apk-project");
    }

    public static File dir(String name) {
        return new File(base(), name);
    }

    public static File srcDir(String name) {
        return new File(dir(name), SRC_DIR);
    }

    public static File outDir(String name) {
        return new File(dir(name), OUT_DIR);
    }

    public static boolean exists(String name) {
        return dir(name).isDirectory();
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        File b = base();
        if (!b.exists()) return out;
        File[] kids = b.listFiles();
        if (kids == null) return out;
        for (File f : kids) {
            if (f.isDirectory()) out.add(f.getName());
        }
        Collections.sort(out);
        return out;
    }

    public static void create(String name) throws IOException {
        File d = dir(name);
        if (d.exists()) throw new IOException("Project exists");
        if (!d.mkdirs()) throw new IOException("Cannot create dir: " + d);
        new File(d, SRC_DIR).mkdirs();
        new File(d, OUT_DIR).mkdirs();
        ProjectConfig c = new ProjectConfig();
        c.name = name;
        saveConfig(c);
    }

    public static void delete(String name) {
        deleteRec(dir(name));
    }

    public static ProjectConfig loadConfig(String name) {
        File f = new File(dir(name), CONFIG_FILE);
        if (!f.exists()) {
            ProjectConfig c = new ProjectConfig();
            c.name = name;
            return c;
        }
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            return ProjectConfig.fromJson(new String(buf, "UTF-8"));
        } catch (Exception e) {
            ProjectConfig c = new ProjectConfig();
            c.name = name;
            return c;
        }
    }

    public static void saveConfig(ProjectConfig c) throws IOException {
        File f = new File(dir(c.name), CONFIG_FILE);
        try {
            String s = c.toJson().toString(2);
            try (OutputStream os = new FileOutputStream(f)) {
                os.write(s.getBytes("UTF-8"));
            }
        } catch (Exception e) {
            throw new IOException("Save config failed: " + e.getMessage());
        }
    }

    public static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }

    public static void copy(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}