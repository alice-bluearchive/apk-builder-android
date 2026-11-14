package com.example.androidstudio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;

import com.android.apksig.ApkSigner;
import com.example.androidstudio.build.BuildStage;
import com.example.androidstudio.build.ProgressReporter;
import com.example.androidstudio.build.ProjectLayout;

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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ApkBuilder {

    private static final String KS_FILE = "keystore/debug.p12";
    private static final String KS_TYPE = "PKCS12";
    private static final String KS_PASSWORD = "android";
    private static final String KS_ALIAS = "androiddebugkey";

    public interface Logger {
        void log(String msg);
    }

    private final Context ctx;
    private final ToolchainManager tm;
    private final Logger logger;

    public ApkBuilder(Context ctx, ToolchainManager tm, Logger logger) {
        this.ctx = ctx.getApplicationContext();
        this.tm = tm;
        this.logger = (logger == null) ? msg -> {} : logger;
    }

    // ============ 预生成 R.java ============
    public void generateRJava(File projDir, File rJavaOutDir, String pkg) throws Exception {
        ProjectLayout layout = ProjectLayout.detect(projDir);
        File projectRes = layout.isValid() ? layout.resDir : new File(projDir, "src/main/res");
        if (!projectRes.exists()) projectRes = new File(projDir, "res");

        if (projectRes == null || !projectRes.isDirectory()) {
            log("项目无 res/，跳过 R.java 生成");
            return;
        }

        File manifest = layout.isValid() ? layout.manifest : new File(projDir, "src/main/AndroidManifest.xml");
        if (!manifest.exists()) manifest = new File(projDir, "AndroidManifest.xml");
        if (manifest == null || !manifest.exists()) throw new IOException("项目缺 AndroidManifest.xml");

        if (tm.getTool("aapt2") == null) throw new IOException("aapt2 不可用");

        File workDir = new File(ctx.getCacheDir(), "genr_" + System.currentTimeMillis());
        deleteRec(workDir);
        if (!workDir.mkdirs()) throw new IOException("无法创建临时目录");

        File resCopy = new File(workDir, "res");
        copyRec(projectRes, resCopy);
        log("已拷贝项目 res/ 到临时目录");

        File mipmapDir = new File(resCopy, "mipmap");
        mipmapDir.mkdirs();
        File mipmapPng = new File(mipmapDir, "ic_launcher.png");
        if (!mipmapPng.exists()) {
            writePlaceholderIcon(mipmapPng);
            log("补默认 mipmap/ic_launcher.png");
        }

        log("=== aapt2 compile（生成 R.java 前） ===");
        File compiledRes = new File(workDir, "compiled_res.zip");
        runTool("aapt2", "compile", "--dir", resCopy.getAbsolutePath(), "-o", compiledRes.getAbsolutePath());

        log("=== aapt2 link --java ===");
        if (!rJavaOutDir.exists()) rJavaOutDir.mkdirs();

        File androidJar = new File(ctx.getCacheDir(), "android_build.jar");
        if (!androidJar.exists()) throw new IOException("android.jar 缺失");

        File dummyApk = new File(workDir, "dummy.apk");

        // 强制指定包名生成 R.java
        runTool("aapt2", "link",
                "-o", dummyApk.getAbsolutePath(),
                "-I", androidJar.getAbsolutePath(),
                "--manifest", manifest.getAbsolutePath(),
                "--java", rJavaOutDir.getAbsolutePath(),
                "--custom-package", pkg,
                "--min-sdk-version", "24",
                "--target-sdk-version", "34",
                compiledRes.getAbsolutePath());

        log("R.java 输出目录: " + rJavaOutDir.getAbsolutePath());
    }

    // ============ HTML 模式 ============
    public File build(File htmlFile, String pkg, String appName,
                      File iconFile, List<String> permissions,
                      SigningConfig signingConfig) throws Exception {
        return build(htmlFile, pkg, appName, iconFile, permissions, signingConfig, null);
    }

    public File build(File htmlFile, String pkg, String appName,
                      File iconFile, List<String> permissions,
                      SigningConfig signingConfig, ProgressReporter reporter) throws Exception {
        if (htmlFile == null || !htmlFile.exists()) throw new IOException("源文件不存在");
        if (appName == null || appName.isEmpty()) appName = "My App";
        if (permissions == null) permissions = new ArrayList<>();
        if (pkg == null || pkg.isEmpty()) throw new IOException("包名不能为空");

        if (tm.getTool("aapt2") == null) throw new IOException("aapt2 不可用");
        if (tm.getTool("zipalign") == null) throw new IOException("zipalign 不可用");

        File workDir = new File(ctx.getCacheDir(), "build_html_" + System.currentTimeMillis());
        deleteRec(workDir);
        if (!workDir.mkdirs()) throw new IOException("无法创建工作目录");

        File manifestFile = prepareResAndManifest(workDir, appName, pkg, iconFile,
                permissions, "com.example.MainActivity");

        File assetsDir = new File(workDir, "assets");
        assetsDir.mkdirs();
        copyFile(htmlFile, new File(assetsDir, "index.html"));

        File dex1 = new File(workDir, "classes.dex");
        File dex2 = new File(workDir, "classes2.dex");
        copyAsset("template/classes.dex", dex1);
        copyAsset("template/classes2.dex", dex2);

        return assembleApk(workDir, manifestFile, assetsDir,
                new File[]{dex1, dex2}, appName, signingConfig, reporter);
    }

    // ============ Java 项目模式 ============
    public File buildJavaProject(File projDir, File[] dexFiles, File iconFile,
                                 String appNameFallback,
                                 SigningConfig signingConfig) throws Exception {
        return buildJavaProject(projDir, dexFiles, iconFile, appNameFallback, signingConfig, null);
    }

    public File buildJavaProject(File projDir, File[] dexFiles, File iconFile,
                                 String appNameFallback,
                                 SigningConfig signingConfig,
                                 ProgressReporter reporter) throws Exception {
        File manifest = new File(projDir, "src/main/AndroidManifest.xml");
        if (!manifest.exists()) manifest = new File(projDir, "AndroidManifest.xml");
        if (!manifest.exists()) throw new IOException("项目缺 AndroidManifest.xml");

        if (dexFiles == null || dexFiles.length == 0) throw new IOException("dex 文件为空");

        if (tm.getTool("aapt2") == null) throw new IOException("aapt2 不可用");
        if (tm.getTool("zipalign") == null) throw new IOException("zipalign 不可用");

        File workDir = new File(ctx.getCacheDir(), "build_java_" + System.currentTimeMillis());
        deleteRec(workDir);
        if (!workDir.mkdirs()) throw new IOException("无法创建工作目录");

        File projectRes = new File(projDir, "src/main/res");
        if (!projectRes.exists()) projectRes = new File(projDir, "res");
        File resCopy = new File(workDir, "res");
        if (projectRes.isDirectory()) {
            copyRec(projectRes, resCopy);
        } else {
            resCopy.mkdirs();
        }

        File mipmapDir = new File(resCopy, "mipmap");
        mipmapDir.mkdirs();
        File mipmapPng = new File(mipmapDir, "ic_launcher.png");
        if (iconFile != null && iconFile.exists()) {
            processIcon(iconFile, mipmapPng);
        } else if (!mipmapPng.exists()) {
            writePlaceholderIcon(mipmapPng);
        }

        File assetsDir = new File(workDir, "assets");
        assetsDir.mkdirs();

        File[] dexCopies = new File[dexFiles.length];
        for (int i = 0; i < dexFiles.length; i++) {
            File dex = new File(workDir,
                    "classes" + (i == 0 ? "" : String.valueOf(i + 1)) + ".dex");
            copyFile(dexFiles[i], dex);
            dexCopies[i] = dex;
        }

        return assembleApk(workDir, manifest, assetsDir, dexCopies, appNameFallback, signingConfig, reporter);
    }

    // ============ 公共打包流程 ============
    private File assembleApk(File workDir, File manifestFile, File assetsDir,
                             File[] dexFiles, String appName,
                             SigningConfig signingConfig,
                             ProgressReporter reporter) throws Exception {

        if (reporter != null) reporter.onStageStart(BuildStage.AAPT2);

        File androidJar = new File(ctx.getCacheDir(), "android_build.jar");
        if (!androidJar.exists()) throw new IOException("android.jar 缺失，请重新构建");

        File resDir = new File(workDir, "res");
        log("=== aapt2 compile ===");
        File compiledRes = new File(workDir, "compiled_res.zip");
        runTool("aapt2", "compile", "--dir", resDir.getAbsolutePath(), "-o", compiledRes.getAbsolutePath());

        log("=== aapt2 link ===");
        File baseApk = new File(workDir, "base.apk");
        runTool("aapt2", "link", "-o", baseApk.getAbsolutePath(), "-I", androidJar.getAbsolutePath(),
                "--manifest", manifestFile.getAbsolutePath(), "-A", assetsDir.getAbsolutePath(),
                "--min-sdk-version", "24", "--target-sdk-version", "34",
                compiledRes.getAbsolutePath());

        if (reporter != null) reporter.onStageEnd(BuildStage.AAPT2);
        if (reporter != null) reporter.onStageStart(BuildStage.ZIPALIGN);

        log("=== 添加 classes.dex ===");
        File withDex = new File(workDir, "with_dex.apk");
        addDexToApk(baseApk, withDex, dexFiles);

        log("=== zipalign ===");
        File alignedApk = new File(workDir, "aligned.apk");
        runTool("zipalign", "-p", "-f", "4", withDex.getAbsolutePath(), alignedApk.getAbsolutePath());

        if (reporter != null) reporter.onStageEnd(BuildStage.ZIPALIGN);
        if (reporter != null) reporter.onStageStart(BuildStage.SIGN);

        File outDir = new File("/sdcard/Download");
        if (!outDir.exists() || !outDir.canWrite()) outDir = ctx.getExternalFilesDir(null);
        if (outDir == null) outDir = ctx.getFilesDir();

        String safeName = appName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.isEmpty()) safeName = "app";
        File outputApk = new File(outDir, safeName + ".apk");
        if (outputApk.exists()) outputApk.delete();

        if (signingConfig != null && signingConfig.signingEnabled) {
            File ksFile = new File(signingConfig.projectRoot, signingConfig.keystorePath);
            signApkCustom(alignedApk, outputApk, ksFile, signingConfig.keystoreType,
                    signingConfig.keystoreAlias, signingConfig.keystorePassword, signingConfig.keyPassword);
        } else {
            signApk(alignedApk, outputApk);
        }
        log("签名完成: " + outputApk.getAbsolutePath());
        if (reporter != null) reporter.onStageEnd(BuildStage.SIGN);

        return outputApk;
    }

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
                        "<resources><string name=\"app_name\">"
                        + escapeXml(appName) + "</string></resources>\n");

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
                    handled = true;
                }
            } catch (Exception e) {
                log("图标重编码失败，改为直接拷贝: " + e.getMessage());
            }
            if (!handled) {
                copyFile(iconFile, mipmapPng);
            }
        } else {
            writePlaceholderIcon(mipmapPng);
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

        List<X509Certificate> certs = new ArrayList<>();
        for (java.security.cert.Certificate c : rawCerts) {
            certs.add((X509Certificate) c);
        }

        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                KS_ALIAS, key, certs).build();

        new ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(input)
                .setOutputApk(output)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .build()
                .sign();
    }

    private void signApkCustom(File input, File output,
                               File ksFile, String ksType,
                               String alias, String storePass, String keyPass)
            throws Exception {
        if (keyPass == null || keyPass.isEmpty()) keyPass = storePass;

        KeyStore ks = KeyStore.getInstance(ksType);
        try (InputStream is = new FileInputStream(ksFile)) {
            ks.load(is, storePass.toCharArray());
        } catch (Exception e) {
            throw new IOException("加载 keystore 失败: " + e.getMessage(), e);
        }

        PrivateKey key = (PrivateKey) ks.getKey(alias, keyPass.toCharArray());
        java.security.cert.Certificate[] rawCerts = ks.getCertificateChain(alias);
        if (key == null) throw new IOException("keystore 缺 alias: " + alias);
        if (rawCerts == null || rawCerts.length == 0) throw new IOException("alias 缺证书链");

        List<X509Certificate> certs = new ArrayList<>();
        for (java.security.cert.Certificate c : rawCerts) {
            certs.add((X509Certificate) c);
        }

        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                alias, key, certs).build();

        new ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(input)
                .setOutputApk(output)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .build()
                .sign();

        log("已用自定义 keystore 签名");
    }

    private void runTool(String name, String... args) throws IOException, InterruptedException {
        File tool = tm.getTool(name);
        if (tool == null) throw new IOException(name + " 未找到");

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
        StringBuilder errOut = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            log("  " + line);
            errOut.append(line).append("\n");
        }
        int code = p.waitFor();
        if (code != 0) throw new IOException(name + " 失败 (exit=" + code + ")\n真实错误:\n" + errOut.toString());
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

    private void log(String s) {
        logger.log(s);
    }

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
            w.write(content);
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void copyRec(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new IOException("mkdirs failed: " + dst);
            }
            File[] kids = src.listFiles();
            if (kids != null) {
                for (File k : kids) copyRec(k, new File(dst, k.getName()));
            }
        } else {
            copyFile(src, dst);
        }
    }

    private void processIcon(File iconFile, File outPng) throws IOException {
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
                try (FileOutputStream fos = new FileOutputStream(outPng)) {
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                if (scaled != bmp) scaled.recycle();
                bmp.recycle();
                return;
            }
        } catch (Exception e) {
            log("图标重编码失败，改为直接拷贝: " + e.getMessage());
        }
        copyFile(iconFile, outPng);
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

    private void writePlaceholderIcon(File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            android.graphics.drawable.Drawable d =
                    androidx.core.content.ContextCompat.getDrawable(
                            ctx, android.R.drawable.sym_def_app_icon);
            if (d != null) {
                Bitmap bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(bmp);
                d.setBounds(0, 0, 192, 192);
                d.draw(cv);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                bmp.recycle();
                return;
            }
        } catch (Throwable e) {
            log("加载系统图标失败: " + e);
        }

        Bitmap bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFF3DDC84);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        bmp.recycle();
    }
}