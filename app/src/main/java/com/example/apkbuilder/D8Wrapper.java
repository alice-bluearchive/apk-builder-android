package com.example.apkbuilder;

import android.util.Log;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class D8Wrapper {

    public interface Logger { void log(String msg); }

    private final Logger logger;

    public D8Wrapper(Logger logger) {
        this.logger = logger == null ? s -> {} : logger;
    }

    public void classToDex(File classDir, File androidJar, File outputDir) throws Exception {
        if (!outputDir.exists()) outputDir.mkdirs();

        List<File> classFiles = new ArrayList<>();
        collectClasses(classDir, classFiles);
        log("找到 " + classFiles.size() + " 个 .class 文件");
        if (classFiles.isEmpty()) throw new Exception("没有找到 .class 文件");

        D8Command.Builder builder = D8Command.builder(new DiagnosticsHandler() {
            @Override
            public void error(Diagnostic diagnostic) {
                log("[D8 error] " + diagnostic.getDiagnosticMessage());
            }
            @Override
            public void warning(Diagnostic diagnostic) {
                log("[D8 warn] " + diagnostic.getDiagnosticMessage());
            }
            @Override
            public void info(Diagnostic diagnostic) {
                log("[D8 info] " + diagnostic.getDiagnosticMessage());
            }
        });

        List<Path> classPaths = new ArrayList<>();
        for (File f : classFiles) classPaths.add(f.toPath());
        builder.addProgramFiles(classPaths);

        builder.addLibraryFiles(Collections.singletonList(androidJar.toPath()));

        builder.setMinApiLevel(24);
        builder.setMode(CompilationMode.DEBUG);
        builder.setOutput(outputDir.toPath(), OutputMode.DexIndexed);

        D8Command command = builder.build();
        D8.run(command);
        log("D8 完成，输出到 " + outputDir.getAbsolutePath());

        File[] dexes = outputDir.listFiles();
        if (dexes != null) {
            for (File d : dexes) log("  " + d.getName() + " (" + d.length() + " bytes)");
        }
    }

    private void collectClasses(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectClasses(f, out);
            else if (f.getName().endsWith(".class")) out.add(f);
        }
    }

    private void log(String s) {
        Log.d("APKBUILDER", "[D8] " + s);
        logger.log("[D8] " + s);
    }
}