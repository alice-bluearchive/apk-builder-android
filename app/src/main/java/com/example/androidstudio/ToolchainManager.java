package com.example.androidstudio;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

public class ToolchainManager {
    private final Context ctx;
    private final File nativeDir;
    private final String currentAbi;

    public ToolchainManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.nativeDir = new File(this.ctx.getApplicationInfo().nativeLibraryDir);
        // 获取当前设备的首选架构
        this.currentAbi = Build.SUPPORTED_ABIS[0];
    }

    public String getCurrentAbi() {
        return currentAbi;
    }

    public File getNativeDir() { return nativeDir; }

    public File getTool(String name) {
        File f = new File(nativeDir, "lib" + name + ".so");
        if (f.exists()) {
            return f;
        }
        return null;
    }

    /** 检查工具是否存在且可执行，给出明确的错误提示 */
    public boolean checkTool(String name, StringBuilder outLog) {
        File tool = getTool(name);
        if (tool == null) {
            outLog.append("❌ [").append(name).append("] 找不到二进制文件\n");
            outLog.append("   设备架构: ").append(currentAbi).append("\n");
            outLog.append("   请检查 app/src/main/jniLibs/").append(currentAbi)
                    .append("/lib").append(name).append(".so 是否存在\n");
            return false;
        }
        if (!tool.canExecute()) {
            outLog.append("❌ [").append(name).append("] 文件不可执行\n");
            outLog.append("   路径: ").append(tool.getAbsolutePath()).append("\n");
            return false;
        }
        outLog.append("✅ [").append(name).append("] 准备就绪\n");
        outLog.append("   路径: ").append(tool.getAbsolutePath()).append("\n");
        return true;
    }

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

            if (code == 126) {
                sb.append("提示: 权限被拒绝。可能是因为 useLegacyPackaging 未开启。\n");
            } else if (code == 127) {
                sb.append("提示: 找不到命令。如果使用了动态链接库，请检查依赖是否齐全。\n");
            } else if (code == 134) {
                sb.append("提示: 程序崩溃 (SIGABRT)。可能是 CPU 架构不匹配 (例如在 x86 上跑了 ARM 的二进制)。\n");
            }
        } catch (Exception e) {
            sb.append("ERROR: ").append(e).append("\n");
        }
        return sb.toString();
    }
}