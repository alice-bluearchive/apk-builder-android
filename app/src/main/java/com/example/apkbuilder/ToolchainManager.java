package com.example.apkbuilder;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

public class ToolchainManager {
    private final Context ctx;
    private final File nativeDir;

    public ToolchainManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.nativeDir = new File(this.ctx.getApplicationInfo().nativeLibraryDir);
    }

    public File getTool(String name) {
        File f = new File(nativeDir, "lib" + name + ".so");
        return f.exists() ? f : null;
    }

    public File getNativeDir() { return nativeDir; }

    public String exec(List<String> cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append("$ ").append(String.join(" ", cmd)).append("\n");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.environment().put("LD_LIBRARY_PATH", nativeDir.getAbsolutePath());
            pb.environment().put("TMPDIR", ctx.getCacheDir().getAbsolutePath());

            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            int code = p.waitFor();
            sb.append("exit code: ").append(code).append("\n");
        } catch (Exception e) {
            sb.append("ERROR: ").append(e).append("\n");
        }
        return sb.toString();
    }
}