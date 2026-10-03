package com.example.apkbuilder;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.android.apksig.ApkSigner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ApkBuilder {

    private static final String TEMPLATE_ACTIVITY = "com.example.myapplication.MainActivity";

    private static final String KS_FILE = "keystore/debug.p12";
    private static final String KS_TYPE = "PKCS12";
    private static final String KS_PASSWORD = "android";
    private static final String KS_ALIAS = "androiddebugkey";

    public interface Logger { void log(String msg); }

    private final Context ctx;
    private final ToolchainManager tm;
    private final Logger logger;

    public ApkBuilder(Context ctx, ToolchainManager tm, Logger logger) {
        this.ctx = ctx.getApplicationContext();
        this.tm = tm;
        this.logger = (logger == null) ? msg -> {} : logger;
    }

    // ============ HTML 模式 ============
    public File build(File htmlFile, String pkg, String appName,
                      File iconFile, List<String> permissions) throws Exception {
        if (htmlFile == null || !htmlFile.exists()) throw new IOException("源文件不存在");
        if (appName == null || appName.isEmpty()) appName = "My App";
        if (permissions == null) permissions = new ArrayList<>();
        if (pkg == null || pkg.isEmpty()) throw new IOException("包名不能为空");

        if (tm.getTool("aapt2") == null) throw new IOException("aapt2 不可用");
        if (tm.getTool("zipalign") == null) throw new IOException("zipalign 不可用");

        File workDir = new File(ctx.getCacheDir(), "build_" + System.currentTimeMillis());
        deleteRec(workDir);
        if (!workDir.mkdirs()) throw new IOException("无法创建工作目录");
        log("工作目录: " + workDir.getAbsolutePath());

        // 生成 res + manifest
        File manifestFile = prepareResAndManifest(workDir, appName, pkg, iconFile, permissions, TEMPLATE_ACTIVITY);

        // assets/index.html
        File assetsDir = new File(workDir, "assets");
        assetsDir.mkdirs();
        copyFile(htmlFile, new File(assetsDir, "index.html"));

        // 从模板拷 classes.dex / classes2.dex
        File dex1 = new File(workDir, "classes.dex");
        File dex2 = new File(workDir, "classes2.dex");
        copyAsset("template/classes.dex", dex1);
        copyAsset("template/classes2.dex", dex2);

        return assembleApk(workDir, manifestFile, assetsDir, new File[]{dex1, dex2}, appName);
    }

    // ============ Java 模式 ============
    public File buildJava(File userDex, String pkg, String appName,
                          File iconFile, List<String> permissions) throws Exception {
        if (userDex == null || !userDex.exists()) throw new IOException("dex 文件不存在");
        if (appName == null || appName.isEmpty()) appName = "My App";
        if (permissions == null) permissions = new ArrayList<>();
        if (pkg == null || pkg.isEmpty()) throw new IOException("包名不能为空");

        if (tm.getTool("aapt2") == null) throw new IOException("aapt2 不可用");
        if (tm.getTool("zipalign") == null) throw new IOException("zipalign 不可用");

        File workDir = new File(ctx.getCacheDir(), "build_java_" + System.currentTimeMillis());
        deleteRec(workDir);
        if (!workDir.mkdirs()) throw new IOException("无法创建工作目录");
        log("工作目录: " + workDir.getAbsolutePath());

        // Java 模式：manifest 里 activity 名 = 用户包名 + .MainActivity
        String activityClass = pkg + ".MainActivity";
        File manifestFile = prepareResAndManifest(workDir, appName, pkg, iconFile, permissions, activityClass);

        // assets 空目录（aapt2 需要一个）
        File assetsDir = new File(workDir, "assets");
        assetsDir.mkdirs();

        // 用户的 classes.dex
        File dex1 = new File(workDir, "classes.dex");
        copyFile(userDex, dex1);

        return assembleApk(workDir, manifestFile, assetsDir, new File[]{dex1}, appName);
    }

    // ============ 公共流程 ============
    private File prepareResAndManifest(File workDir, String appName, String pkg,
                                       File iconFile, List<String> permissions,
                                       String activityClass) throws Exception {
        File resDir = new File(workDir, "res");
        File valuesDir = new File(resDir, "values");
        File mipmapDir = new File(resDir, "mipmap");
        valuesDir.mkdirs();
        mipmapDir.mkdirs();

        writeFile(new File(valuesDir, "strings.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<resources><string name=\"app_name\">" + escapeXml(appName) + "</string></resources>\n");

        File mipmapPng = new File(mipmapDir, "ic_launcher.png");
        if (iconFile != null && iconFile.exists()) {
            boolean handled = false;
            try {
                Bitmap bmp = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                if (bmp != null) {
                    int w = bmp.getWidth();
                    int h = bmp.getHeight();
                    float scale = Math.min(192f / w, 192f / h);
                    if (scale > 1f) scale = 1f;
                    int nw = Math.max(1, Math.round(w * scale));
                    int nh = Math.max(1, Math.round(h * scale));
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true);
                    try (FileOutputStream fos = new FileOutputStream(mipmapPng)) {
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                    if (scaled != bmp) scaled.recycle();
                    bmp.recycle();
                    log("图标已处理: " + nw + "x" + nh);
                    handled = true;
                }
            } catch (Exception e) {
                log("图标重编码失败，改为直接拷贝: " + e.getMessage());
            }
            if (!handled) {
                copyFile(iconFile, mipmapPng);
                log("图标直接拷贝");
            }
        } else {
            writePlaceholderIcon(mipmapPng);
            log("使用默认图标（蓝色方块）");
        }

        File manifestFile = new File(workDir, "AndroidManifest.xml");
        StringBuilder m = new StringBuilder();
        m.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        m.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"")
                .append(pkg).append("\">\n");
        m.append("<uses-sdk android:minSdkVersion=\"24\" android:targetSdkVersion=\"34\"/>\n");
        for (String p : permissions) {
            m.append("<uses-permission android:name=\"android.permission.")
                    .append(p).append("\"/>\n");
        }
        m.append("<application android:label=\"@string/app_name\" android:icon=\"@mipmap/ic_launcher\" ")
                .append("android:theme=\"@android:style/Theme.Material.Light\">\n");
        m.append("<activity android:name=\"").append(activityClass).append("\" ")
                .append("android:exported=\"true\" ")
                .append("android:configChanges=\"orientation|screenSize|keyboardHidden\">\n");
        m.append("<intent-filter>\n");
        m.append("<action android:name=\"android.intent.action.MAIN\"/>\n");
        m.append("<category android:name=\"android.intent.category.LAUNCHER\"/>\n");
        m.append("</intent-filter>\n</activity>\n</application>\n</manifest>\n");
        writeFile(manifestFile, m.toString());
        return manifestFile;
    }

    private File assembleApk(File workDir, File manifestFile, File assetsDir,
                             File[] dexFiles, String appName) throws Exception {
        File androidJar = prepareAndroidJar();
        File resDir = new File(workDir, "res");

        log("=== aapt2 compile ===");
        File compiledRes = new File(workDir, "compiled_res.zip");
        runTool("aapt2", "compile", "--dir", resDir.getAbsolutePath(),
                "-o", compiledRes.getAbsolutePath());

        log("=== aapt2 link ===");
        File baseApk = new File(workDir, "base.apk");
        runTool("aapt2", "link",
                "-o", baseApk.getAbsolutePath(),
                "-I", androidJar.getAbsolutePath(),
                "--manifest", manifestFile.getAbsolutePath(),
                "-A", assetsDir.getAbsolutePath(),
                "--min-sdk-version", "24",
                "--target-sdk-version", "34",
                compiledRes.getAbsolutePath());

        log("=== 添加 classes.dex ===");
        File withDex = new File(workDir, "with_dex.apk");
        addDexToApk(baseApk, withDex, dexFiles);

        log("=== zipalign ===");
        File alignedApk = new File(workDir, "aligned.apk");
        runTool("zipalign", "-p", "-f", "4",
                withDex.getAbsolutePath(), alignedApk.getAbsolutePath());

        log("=== 签名 ===");
        File outDir = new File("/sdcard/Download");
        if (!outDir.exists() || !outDir.canWrite()) {
            outDir = ctx.getExternalFilesDir(null);
            if (outDir == null) outDir = ctx.getFilesDir();
        }
        String safeName = appName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.isEmpty()) safeName = "app";
        File outputApk = new File(outDir, safeName + ".apk");
        if (outputApk.exists()) outputApk.delete();

        signApk(alignedApk, outputApk);
        log("签名完成: " + outputApk.getAbsolutePath());

        return outputApk;
    }

    private File prepareAndroidJar() throws IOException {
        // 从 zip 解压到 filesDir（持久化，避免每次重解压）
        File cached = new File(ctx.getFilesDir(), "android.jar");
        if (cached.exists() && cached.length() > 1_000_000) {
            log("android.jar 已就绪: " + cached.length() + " bytes");
            return cached;
        }

        log("首次解压 android.jar.zip ...");
        long t0 = System.currentTimeMillis();
        File tmp = new File(ctx.getFilesDir(), "android.jar.tmp");

        byte[] buf = new byte[65536];
        try (InputStream is = ctx.getAssets().open("template/android.jar.zip");
             java.util.zip.ZipInputStream zis =
                     new java.util.zip.ZipInputStream(
                             new java.io.BufferedInputStream(is));
             OutputStream os = new FileOutputStream(tmp)) {

            java.util.zip.ZipEntry entry;
            boolean extracted = false;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                int n;
                while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                extracted = true;
                break; // 只有一个文件 android.jar
            }
            if (!extracted) throw new IOException("zip 里没有文件");
        } catch (IOException e) {
            throw new IOException("解压 android.jar.zip 失败: " + e.getMessage(), e);
        }

        if (!tmp.renameTo(cached)) {
            // rename 失败就用 copy
            try (InputStream in = new FileInputStream(tmp);
                 OutputStream out = new FileOutputStream(cached)) {
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            tmp.delete();
        }

        long dt = System.currentTimeMillis() - t0;
        log("android.jar 解压完成: " + cached.length() + " bytes, 耗时 " + (dt / 1000) + "s");
        return cached;
    }

    private void addDexToApk(File srcApk, File dstApk, File[] dexFiles) throws IOException {
        try (ZipFile zf = new ZipFile(srcApk);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dstApk))) {

            byte[] buf = new byte[8192];
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                ZipEntry newEntry = new ZipEntry(entry.getName());
                newEntry.setMethod(entry.getMethod());
                if (entry.getMethod() == ZipEntry.STORED) {
                    newEntry.setSize(entry.getSize());
                    newEntry.setCompressedSize(entry.getCompressedSize());
                    newEntry.setCrc(entry.getCrc());
                }
                zos.putNextEntry(newEntry);
                try (InputStream is = zf.getInputStream(entry)) {
                    int n;
                    while ((n = is.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
            }

            // 添加 dex 文件（classes.dex, classes2.dex, ...）
            for (int i = 0; i < dexFiles.length; i++) {
                File dex = dexFiles[i];
                if (!dex.exists()) continue;
                String name = (i == 0) ? "classes.dex" : ("classes" + (i + 1) + ".dex");
                ZipEntry dexEntry = new ZipEntry(name);
                dexEntry.setMethod(ZipEntry.DEFLATED);
                zos.putNextEntry(dexEntry);
                try (InputStream is = new FileInputStream(dex)) {
                    int n;
                    while ((n = is.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
            }
        }
    }

    private void signApk(File input, File output) throws Exception {
        File ksFile = new File(ctx.getCacheDir(), "build_ks.p12");
        try (InputStream is = ctx.getAssets().open(KS_FILE);
             OutputStream os = new FileOutputStream(ksFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        } catch (IOException e) {
            throw new IOException("assets/" + KS_FILE + " 缺失", e);
        }

        KeyStore ks;
        try {
            ks = KeyStore.getInstance(KS_TYPE);
        } catch (Exception e) {
            throw new IOException("Android 不支持 " + KS_TYPE + ": " + e.getMessage(), e);
        }

        try (InputStream is = new FileInputStream(ksFile)) {
            ks.load(is, KS_PASSWORD.toCharArray());
        } catch (Exception e) {
            throw new IOException("加载 keystore 失败: " + e.getMessage(), e);
        }

        PrivateKey key = (PrivateKey) ks.getKey(KS_ALIAS, KS_PASSWORD.toCharArray());
        java.security.cert.Certificate[] rawCerts = ks.getCertificateChain(KS_ALIAS);
        if (key == null || rawCerts == null || rawCerts.length == 0) {
            throw new IOException("keystore 缺 alias: " + KS_ALIAS);
        }

        X509Certificate[] certs = new X509Certificate[rawCerts.length];
        for (int i = 0; i < rawCerts.length; i++) {
            certs[i] = (X509Certificate) rawCerts[i];
        }

        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                KS_ALIAS, key, Arrays.asList(certs)).build();

        new ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(input)
                .setOutputApk(output)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .build()
                .sign();
    }

    private void runTool(String name, String... args) throws IOException, InterruptedException {
        File tool = tm.getTool(name);
        if (tool == null) throw new IOException(name + " 未找到");
        if (!tool.canExecute()) throw new IOException(name + " 不可执行");

        List<String> cmd = new ArrayList<>();
        cmd.add(tool.getAbsolutePath());
        for (String a : args) cmd.add(a);

        StringBuilder sb = new StringBuilder("$ ");
        for (String c : cmd) sb.append(c).append(" ");
        log(sb.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().put("LD_LIBRARY_PATH", tm.getNativeDir().getAbsolutePath());
        pb.environment().put("TMPDIR", ctx.getCacheDir().getAbsolutePath());

        Process p = pb.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) log("  " + line);
        int code = p.waitFor();
        if (code != 0) throw new IOException(name + " 失败, exit=" + code);
    }

    private void copyAsset(String assetPath, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream is = ctx.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    private void log(String s) { logger.log(s); }

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
            w.write(content);
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void deleteRec(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void writePlaceholderIcon(File f) throws IOException {
        Bitmap bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFF3B82F6);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        bmp.recycle();
    }
}