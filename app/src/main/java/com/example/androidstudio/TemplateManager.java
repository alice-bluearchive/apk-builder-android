package com.example.androidstudio;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TemplateManager {

    public static final String TYPE_HTML = "HTML";
    public static final String TYPE_JAVA_EMPTY = "JAVA_EMPTY";
    public static final String TYPE_JAVA_EMPTY_VIEW = "JAVA_EMPTY_VIEW";

    private final Context ctx;

    public TemplateManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void apply(String type, File projectDir, String pkg, String appName) throws IOException {
        String assetBase;
        String[][] files;

        // 包名转路径 (com.example.app -> com/example/app)
        String pkgPath = pkg.replace('.', '/');

        if (TYPE_JAVA_EMPTY_VIEW.equals(type)) {
            assetBase = "templates/empty_view/";
            files = new String[][]{
                    {"AndroidManifest.xml", "src/main/AndroidManifest.xml"},
                    {"MainActivity.java",   "src/main/java/" + pkgPath + "/MainActivity.java"},
                    {"activity_main.xml",   "src/main/res/layout/activity_main.xml"},
                    {"strings.xml",         "src/main/res/values/strings.xml"},
                    {"themes.xml",          "src/main/res/values/themes.xml"}
            };
        } else if (TYPE_JAVA_EMPTY.equals(type)) {
            assetBase = "templates/empty/";
            files = new String[][]{
                    {"AndroidManifest.xml", "src/main/AndroidManifest.xml"},
                    {"MainActivity.java",   "src/main/java/" + pkgPath + "/MainActivity.java"}
            };
        } else {
            return; // HTML 模式走 ProjectStore 里的逻辑
        }

        for (String[] pair : files) {
            String src = assetBase + pair[0];
            File dst = new File(projectDir, pair[1]);
            copyWithSubstitution(src, dst, pkg, appName);
        }
    }

    private void copyWithSubstitution(String assetPath, File out,
                                      String pkg, String appName) throws IOException {
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();

        String text;
        try (InputStream is = ctx.getAssets().open(assetPath)) {
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = is.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
            text = sb.toString();
        } catch (IOException e) {
            throw new IOException("模板文件缺失: " + assetPath, e);
        }

        // 替换变量
        text = text.replace("${PACKAGE}", pkg)
                .replace("${APP_NAME}", appName)
                .replace("${APP_NAME_RES}", escapeXml(appName));

        try (OutputStream os = new FileOutputStream(out)) {
            os.write(text.getBytes("UTF-8"));
        }
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}