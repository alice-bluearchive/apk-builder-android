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

    /** 兼容老接口，默认 HTML 模式 */
    public static void create(String name) throws IOException {
        create(name, "HTML");
    }

    /** 按模式创建项目：只生成该模式所需的最小文件 */
    public static void create(String name, String mode) throws IOException {
        if (mode == null || mode.isEmpty()) mode = "HTML";

        File d = dir(name);
        if (d.exists()) throw new IOException("Project exists");
        if (!d.mkdirs()) throw new IOException("Cannot create dir: " + d);

        File src = new File(d, SRC_DIR);
        src.mkdirs();
        new File(d, OUT_DIR).mkdirs();

        ProjectConfig c = new ProjectConfig();
        c.name = name;
        c.mode = mode;
        saveConfig(c);

        if ("Java".equals(mode)) {
            writeMinimalJava(src, c.pkg);
        } else {
            writeMinimalHtml(src);
        }
    }

    private static void writeMinimalJava(File srcDir, String pkg) throws IOException {
        File f = new File(srcDir, "MainActivity.java");
        String content =
                "package " + pkg + ";\n" +
                        "\n" +
                        "import android.app.Activity;\n" +
                        "import android.os.Bundle;\n" +
                        "import android.widget.TextView;\n" +
                        "\n" +
                        "public class MainActivity extends Activity {\n" +
                        "    @Override\n" +
                        "    protected void onCreate(Bundle savedInstanceState) {\n" +
                        "        super.onCreate(savedInstanceState);\n" +
                        "        TextView tv = new TextView(this);\n" +
                        "        tv.setText(\"Hello from APK Builder\");\n" +
                        "        setContentView(tv);\n" +
                        "    }\n" +
                        "}\n";
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(content.getBytes("UTF-8"));
        }
    }

    private static void writeMinimalHtml(File srcDir) throws IOException {
        File f = new File(srcDir, "index.html");
        String content =
                "<!DOCTYPE html>\n" +
                        "<html lang=\"zh\">\n" +
                        "<head>\n" +
                        "<meta charset=\"utf-8\">\n" +
                        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
                        "<title>My App</title>\n" +
                        "<style>\n" +
                        "  body{margin:0;font-family:sans-serif;display:flex;align-items:center;\n" +
                        "       justify-content:center;min-height:100vh;background:#f5f5f5;color:#333}\n" +
                        "  .card{text-align:center;padding:32px}\n" +
                        "  h1{margin:0 0 12px;font-size:24px}\n" +
                        "  button{padding:10px 24px;font-size:16px;border:none;border-radius:8px;\n" +
                        "         background:#FFB300;color:#1A1A1A;font-weight:600}\n" +
                        "</style>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "  <div class=\"card\">\n" +
                        "    <h1>Hello, APK Builder!</h1>\n" +
                        "    <button onclick=\"alert('Working!')\">Tap me</button>\n" +
                        "  </div>\n" +
                        "</body>\n" +
                        "</html>\n";
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(content.getBytes("UTF-8"));
        }
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