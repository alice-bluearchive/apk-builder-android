package com.example.apkbuilder;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.DexClassLoader;

public class EcjCompiler {

    public interface Logger { void log(String msg); }

    private final Context ctx;
    private final Logger logger;
    private final File ecjDex;
    private final File ecjJar;
    private final File ecjOut;
    private ClassLoader ecjLoader;

    public EcjCompiler(Context ctx, Logger logger) {
        this.ctx = ctx.getApplicationContext();
        this.logger = logger == null ? s -> {} : logger;
        this.ecjDex = new File(ctx.getFilesDir(), "ecj.dex");
        this.ecjJar = new File(ctx.getFilesDir(), "ecj.jar");
        this.ecjOut = new File(ctx.getFilesDir(), "ecj_out");
    }

    private void copyAssetIfNeeded(File out, String assetPath, long minSize) throws Exception {
        if (out.exists() && out.length() >= minSize) return;
        log("首次拷贝 " + out.getName() + " ...");
        try (InputStream is = ctx.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        }
        log(out.getName() + " 已拷贝: " + out.length() + " bytes");
    }

    private void ensureFiles() throws Exception {
        copyAssetIfNeeded(ecjDex, "template/ecj.dex", 100_000);
        copyAssetIfNeeded(ecjJar, "template/ecj.jar", 100_000);
    }

    private void ensureLoader() throws Exception {
        if (ecjLoader != null) return;
        ensureFiles();
        if (!ecjOut.exists()) ecjOut.mkdirs();

        String dexPath = ecjDex.getAbsolutePath()
                + File.pathSeparator
                + ecjJar.getAbsolutePath();

        log("DexClassLoader dexPath = " + dexPath);
        ecjLoader = new DexClassLoader(
                dexPath,
                ecjOut.getAbsolutePath(),
                null,
                ctx.getClassLoader()
        );
        log("EcjClassLoader 已创建");
    }

    /** 准备 lambda-patch.jar，返回文件路径 */
    private File prepareLambdaPatch() throws Exception {
        File f = new File(ctx.getFilesDir(), "lambda-patch.jar");
        if (f.exists() && f.length() > 10_000) return f;
        log("首次拷贝 lambda-patch.jar ...");
        try (InputStream is = ctx.getAssets().open("template/lambda-patch.jar");
             OutputStream os = new FileOutputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        }
        log("lambda-patch.jar 已拷贝: " + f.length() + " bytes");
        return f;
    }

    private String buildClasspath(File androidJar) throws Exception {
        File lambdaPatch = prepareLambdaPatch();
        return androidJar.getAbsolutePath()
                + File.pathSeparator
                + lambdaPatch.getAbsolutePath();
    }

    public boolean compile(File javaFile, File outputDir, File androidJar) throws Exception {
        ensureLoader();
        if (!outputDir.exists()) outputDir.mkdirs();

        String cp = buildClasspath(androidJar);

        List<String> argList = new ArrayList<>();
        argList.add("-d");              argList.add(outputDir.getAbsolutePath());
        argList.add("-cp");             argList.add(cp);
        argList.add("-source");         argList.add("1.8");
        argList.add("-target");         argList.add("1.8");
        argList.add("-proc:none");
        argList.add("-nowarn");
        argList.add(javaFile.getAbsolutePath());

        return runEcj(argList);
    }

    public boolean compileDir(File javaDir, File outputDir, File androidJar) throws Exception {
        if (javaDir == null || !javaDir.isDirectory()) throw new Exception("源目录不存在");
        ensureLoader();
        if (!outputDir.exists()) outputDir.mkdirs();

        String cp = buildClasspath(androidJar);

        List<String> argList = new ArrayList<>();
        argList.add("-d");              argList.add(outputDir.getAbsolutePath());
        argList.add("-cp");             argList.add(cp);
        argList.add("-source");         argList.add("1.8");
        argList.add("-target");         argList.add("1.8");
        argList.add("-proc:none");
        argList.add("-nowarn");

        File[] files = javaDir.listFiles();
        if (files == null) throw new Exception("源目录为空");
        int n = 0;
        for (File f : files) {
            if (f.getName().endsWith(".java")) {
                argList.add(f.getAbsolutePath());
                n++;
            }
        }
        if (n == 0) throw new Exception("没有 .java 文件");

        log("ECJ 编译 " + n + " 个文件");
        log("  -cp: " + cp);

        boolean ok = runEcj(argList);
        log("ECJ compileDir 结果: " + ok + " (" + n + " 文件)");
        return ok;
    }

    private boolean runEcj(List<String> argList) throws Exception {
        for (String a : argList) log("  arg: " + a);

        Class<?> mainClz = ecjLoader.loadClass("org.eclipse.jdt.internal.compiler.batch.Main");
        StringWriter outSw = new StringWriter();
        StringWriter errSw = new StringWriter();

        Constructor<?> ctor = mainClz.getConstructor(
                PrintWriter.class, PrintWriter.class, boolean.class);
        Object mainObj = ctor.newInstance(
                new PrintWriter(outSw), new PrintWriter(errSw), Boolean.FALSE);

        Method compileMethod = mainClz.getMethod("compile", String[].class);
        Object result;
        try {
            result = compileMethod.invoke(mainObj, (Object) argList.toArray(new String[0]));
        } catch (Throwable t) {
            log("[ERR] 调用 compile 失败:");
            dumpThrowable(t);
            printCaptured(outSw, errSw);
            return false;
        }
        printCaptured(outSw, errSw);
        return Boolean.TRUE.equals(result);
    }

    private void printCaptured(StringWriter outSw, StringWriter errSw) {
        String outStr = outSw.toString();
        String errStr = errSw.toString();
        if (!outStr.isEmpty()) {
            log("--- ECJ stdout ---");
            for (String line : outStr.split("\n")) log("  " + line);
        }
        if (!errStr.isEmpty()) {
            log("--- ECJ stderr ---");
            for (String line : errStr.split("\n")) log("  " + line);
        }
    }

    private void dumpThrowable(Throwable t) {
        log("  class = " + t.getClass().getName());
        log("  message = " + t.getMessage());
        Throwable cause = t.getCause();
        int depth = 0;
        while (cause != null && depth < 5) {
            depth++;
            log("  [cause " + depth + "] " + cause.getClass().getName()
                    + ": " + cause.getMessage());
            cause = cause.getCause();
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        for (String line : sw.toString().split("\n")) log("  " + line);
    }

    private void log(String s) {
        Log.d("APKBUILDER", "[ECJ] " + s);
        logger.log("[ECJ] " + s);
    }
}