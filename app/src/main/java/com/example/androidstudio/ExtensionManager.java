package com.example.androidstudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.DexClassLoader;

public class ExtensionManager {

    public static final String EXT_ROOT = "/sdcard/apk-builder/extensions";
    private static final String TAG = "APKBUILDER-EXT";

    private static ExtensionManager sInst;

    public static synchronized ExtensionManager get(Context ctx) {
        if (sInst == null) sInst = new ExtensionManager(ctx.getApplicationContext());
        return sInst;
    }

    public static class MenuItem {
        public String title;
        public Runnable action;
        public String extensionId;
    }

    public interface ImportCallback {
        void onSuccess(ExtensionInfo info);
        void onError(String msg);
    }

    private final Context ctx;
    private final List<ExtensionInfo> installed = new ArrayList<>();
    private final List<IExtension> loaded = new ArrayList<>();
    private final List<MenuItem> menuItems = new ArrayList<>();
    private final List<SidebarPanelSpec> sidebarPanels = new ArrayList<>();

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private WeakReference<Activity> currentActivity = new WeakReference<>(null);

    private boolean initialized = false;
    private Logger logger = null;
    private volatile String currentProject = null;
    private volatile int generation = 0;

    public interface Logger {
        void log(String s);
    }

    private ExtensionManager(Context ctx) {
        this.ctx = ctx;
    }

    public void setLogger(Logger l) {
        this.logger = l;
    }

    public void setActivity(Activity act) {
        currentActivity = new WeakReference<>(act);
    }

    private Activity act() {
        return currentActivity.get();
    }

    public synchronized void ensureInit() {
        if (initialized) return;
        initialized = true;
        installed.clear();
        installed.addAll(scanInstalled());
        loadAll();
    }

    public List<ExtensionInfo> getInstalled() {
        return new ArrayList<>(installed);
    }

    public List<IExtension> getLoaded() {
        return new ArrayList<>(loaded);
    }

    public List<MenuItem> getMenuItems() {
        return new ArrayList<>(menuItems);
    }

    public List<SidebarPanelSpec> getSidebarPanels() {
        return new ArrayList<>(sidebarPanels);
    }

    public synchronized void reload() {
        unloadAll();
        initialized = false;
        generation++;
        ensureInit();
    }

    public void setCurrentProject(String name) {
        this.currentProject = name;
    }

    public int getGeneration() {
        return generation;
    }

    // ================ 扫描 ================

    private List<ExtensionInfo> scanInstalled() {
        List<ExtensionInfo> out = new ArrayList<>();
        File root = new File(EXT_ROOT);
        if (!root.exists()) {
            root.mkdirs();
            return out;
        }
        File[] kids = root.listFiles();
        if (kids == null) return out;

        for (File k : kids) {
            if (!k.isDirectory()) continue;
            try {
                ExtensionInfo info = ExtensionInfo.fromManifest(k);
                if (!new File(k, "classes.dex").exists()) {
                    log("扩展 " + info.id + " 缺 classes.dex，跳过");
                    continue;
                }
                out.add(info);
                log("发现扩展: " + info.id + " v" + info.version);
            } catch (Exception e) {
                log("跳过 " + k.getName() + ": " + e.getMessage());
            }
        }
        return out;
    }

    // ================ 加载 ================

    private void loadAll() {
        for (ExtensionInfo info : installed) {
            try {
                loadOne(info);
            } catch (Throwable t) {
                log("加载 " + info.id + " 失败: " + t.getMessage());
            }
        }
    }

    private void unloadAll() {
        for (IExtension e : loaded) {
            try {
                e.onUnload();
            } catch (Throwable ignored) {}
        }
        loaded.clear();
        menuItems.clear();
        sidebarPanels.clear();
    }

    private void loadOne(ExtensionInfo info) throws Exception {
        File dexFile = new File(info.dir, "classes.dex");
        File optDir = new File(ctx.getCacheDir(), "ext_opt/" + info.id);
        if (!optDir.exists()) optDir.mkdirs();

        // 收集 classes.dex + lib/*.dex
        StringBuilder dexPath = new StringBuilder(dexFile.getAbsolutePath());
        File libDir = new File(info.dir, "lib");
        if (libDir.isDirectory()) {
            File[] dexs = libDir.listFiles(
                    (dir, name) -> name.endsWith(".dex"));
            if (dexs != null) {
                for (File d : dexs) {
                    dexPath.append(File.pathSeparator).append(d.getAbsolutePath());
                    log("   + lib: " + d.getName());
                }
            }
        }

        DexClassLoader loader = new DexClassLoader(
                dexPath.toString(),
                optDir.getAbsolutePath(),
                null,
                ctx.getClassLoader()
        );

        Class<?> cls = loader.loadClass(info.entryClass);
        Object obj = cls.newInstance();
        if (!(obj instanceof IExtension)) {
            throw new Exception("entryClass 不是 IExtension: " + info.entryClass);
        }

        IExtension ext = (IExtension) obj;
        HostImpl host = new HostImpl(info.id);
        ext.onLoad(host);
        loaded.add(ext);
        log("已加载扩展: " + ext.getId() + " v" + ext.getVersion());
    }

    private class HostImpl implements ExtensionHost {
        final String extId;

        HostImpl(String extId) {
            this.extId = extId;
        }

        @Override
        public Context getAppContext() {
            return ctx;
        }

        @Override
        public void registerMenuItem(String title, Runnable action) {
            MenuItem m = new MenuItem();
            m.title = title;
            m.action = action;
            m.extensionId = extId;
            menuItems.add(m);
        }

        @Override
        public void registerSidebarPanel(SidebarPanelSpec spec) {
            if (spec == null || spec.id == null || spec.id.isEmpty()) return;
            sidebarPanels.add(spec);
            log("注册侧边栏面板: " + spec.id
                    + " (" + (spec.items == null ? 0 : spec.items.size()) + " items)");
        }

        @Override
        public void log(String msg) {
            ExtensionManager.this.log("[" + extId + "] " + msg);
        }

        @Override
        public String getCurrentProject() {
            return currentProject;
        }

        @Override
        public File getProjectRoot() {
            String p = currentProject;
            if (p == null) return null;
            return ProjectStore.dir(p);
        }

        @Override
        public void showMessage(String title, String message) {
            post(() -> {
                Activity a = act();
                if (a == null || a.isFinishing()) return;
                new AlertDialog.Builder(a)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }

        @Override
        public void showConfirm(String title, String message,
                                final Runnable onConfirm) {
            post(() -> {
                Activity a = act();
                if (a == null || a.isFinishing()) return;
                new AlertDialog.Builder(a)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok,
                                (d, w) -> {
                                    if (onConfirm != null) onConfirm.run();
                                })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }

        @Override
        public void showInput(String title, String hint, String defaultValue,
                              final OnInput onOk) {
            post(() -> {
                Activity a = act();
                if (a == null || a.isFinishing()) return;
                EditText et = new EditText(a);
                et.setInputType(InputType.TYPE_CLASS_TEXT);
                et.setHint(hint);
                if (defaultValue != null) {
                    et.setText(defaultValue);
                    et.setSelection(defaultValue.length());
                }
                new AlertDialog.Builder(a)
                        .setTitle(title)
                        .setView(et)
                        .setPositiveButton(android.R.string.ok,
                                (d, w) -> {
                                    if (onOk != null)
                                        onOk.onInput(et.getText().toString());
                                })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }

        @Override
        public void showList(String title, final List<String> items,
                             final OnListItem onItem) {
            post(() -> {
                Activity a = act();
                if (a == null || a.isFinishing()) return;
                final String[] arr = items.toArray(new String[0]);
                new AlertDialog.Builder(a)
                        .setTitle(title)
                        .setItems(arr, (d, which) -> {
                            if (onItem != null)
                                onItem.onItem(which, arr[which]);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }

        private void post(Runnable r) {
            if (Looper.myLooper() == Looper.getMainLooper()) r.run();
            else uiHandler.post(r);
        }
    }

    // ================ 导入 zip ================

    public void importZip(final File zipFile, final ImportCallback cb) {
        new Thread(() -> {
            try {
                File tmpDir = new File(ctx.getCacheDir(),
                        "ext_import_" + System.currentTimeMillis());
                deleteRec(tmpDir);
                tmpDir.mkdirs();

                unzip(zipFile, tmpDir);

                ExtensionInfo info = ExtensionInfo.fromManifest(tmpDir);
                if (!new File(tmpDir, "classes.dex").exists()) {
                    throw new IOException("zip 里没有 classes.dex");
                }

                File root = new File(EXT_ROOT);
                if (!root.exists()) root.mkdirs();
                File dst = new File(root, info.id);
                if (dst.exists()) deleteRec(dst);
                if (!tmpDir.renameTo(dst)) {
                    copyRec(tmpDir, dst);
                    deleteRec(tmpDir);
                }

                log("导入扩展成功: " + info.id + " v" + info.version);
                new Handler(Looper.getMainLooper()).post(() -> {
                    reload();
                    cb.onSuccess(info);
                });
            } catch (Exception e) {
                e.printStackTrace();
                final String msg = e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage();
                new Handler(Looper.getMainLooper()).post(
                        () -> cb.onError(msg));
            }
        }, "ext-import").start();
    }

    private static void unzip(File zip, File outDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File target = new File(outDir, entry.getName());
                if (!target.getCanonicalPath().startsWith(
                        outDir.getCanonicalPath())) {
                    throw new IOException("非法路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    File parent = target.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new FileOutputStream(target)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void copyRec(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("mkdirs: " + dst);
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) {
                copyRec(k, new File(dst, k.getName()));
            }
        } else {
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

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }

    private void log(String s) {
        Log.d(TAG, s);
        if (logger != null) logger.log(s);
    }
}