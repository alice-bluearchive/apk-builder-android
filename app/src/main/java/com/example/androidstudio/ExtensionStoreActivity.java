package com.example.androidstudio;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ExtensionStoreActivity extends AppCompatActivity {

    public static final String STORE_DIR = "/sdcard/apk-builder/store";

    private RecyclerView listStore;
    private TextView tvEmptyStore, tvStorePath;
    private ImageButton btnBack, btnRefreshStore;

    private final List<StoreItem> storeItems = new ArrayList<>();
    private StoreAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static class StoreItem {
        File zipFile;
        String id = "";
        String name = "";
        String version = "";
        String author = "";
        String description = "";
        boolean isInstalled = false;
        String errorMsg = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extension_store);

        listStore = findViewById(R.id.listStore);
        tvEmptyStore = findViewById(R.id.tvEmptyStore);
        tvStorePath = findViewById(R.id.tvStorePath);
        btnBack = findViewById(R.id.btnBack);
        btnRefreshStore = findViewById(R.id.btnRefreshStore);

        tvStorePath.setText(getString(R.string.store_source));
        btnBack.setOnClickListener(v -> finish());
        btnRefreshStore.setOnClickListener(v -> scanStore());

        listStore.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StoreAdapter();
        listStore.setAdapter(adapter);

        scanStore();
    }

    private void scanStore() {
        new Thread(() -> {
            storeItems.clear();
            File root = new File(STORE_DIR);
            if (!root.exists()) root.mkdirs();

            File[] zips = root.listFiles((dir, name) -> name.endsWith(".zip"));
            if (zips != null) {
                for (File zip : zips) {
                    StoreItem item = new StoreItem();
                    item.zipFile = zip;
                    try {
                        try (ZipFile zipFile = new ZipFile(zip)) {
                            ZipEntry entry = zipFile.getEntry("manifest.json");
                            if (entry == null) {
                                item.errorMsg = "Missing manifest.json";
                            } else {
                                try (InputStream is = zipFile.getInputStream(entry)) {
                                    byte[] buf = new byte[(int) entry.getSize()];
                                    int off = 0;
                                    while (off < buf.length) {
                                        int n = is.read(buf, off, buf.length - off);
                                        if (n < 0) break;
                                        off += n;
                                    }
                                    String text = new String(buf, 0, off, "UTF-8");
                                    JSONObject o = new JSONObject(text);
                                    item.id = o.optString("id", "");
                                    item.name = o.optString("name", item.id);
                                    item.version = o.optString("version", "0");
                                    item.author = o.optString("author", "");
                                    item.description = o.optString("description", "");

                                    if (item.id.isEmpty()) {
                                        item.errorMsg = "Missing id in manifest";
                                    } else {
                                        // 修复这里：使用 getExtRoot()
                                        File extDir = new File(ExtensionManager.get(this).getExtRoot(), item.id);
                                        item.isInstalled = extDir.exists();
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        item.errorMsg = e.getMessage();
                    }
                    storeItems.add(item);
                }
            }

            mainHandler.post(() -> {
                adapter.notifyDataSetChanged();
                tvEmptyStore.setVisibility(storeItems.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_extension_store, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            StoreItem item = storeItems.get(position);

            if (item.errorMsg != null) {
                h.tvName.setText(item.zipFile.getName());
                h.tvMeta.setText(getString(R.string.store_invalid_pkg));
                h.tvDesc.setText(item.errorMsg);
                h.btnInstall.setVisibility(View.GONE);
                h.btnUninstall.setVisibility(View.GONE);
                return;
            }

            h.tvName.setText(item.name);
            String meta = "v" + item.version;
            if (!item.author.isEmpty()) meta += " · " + getString(R.string.store_by) + " " + item.author;
            h.tvMeta.setText(meta);
            h.tvDesc.setText(item.description);

            h.btnInstall.setVisibility(View.VISIBLE);
            if (item.isInstalled) {
                h.btnInstall.setText(getString(R.string.store_reinstall));
                h.btnInstall.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF444444));
                h.btnInstall.setTextColor(0xFFFFFFFF);
                h.btnUninstall.setVisibility(View.VISIBLE);
            } else {
                h.btnInstall.setText(getString(R.string.store_install));
                h.btnInstall.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF3DDC84));
                h.btnInstall.setTextColor(0xFF000000);
                h.btnUninstall.setVisibility(View.GONE);
            }

            h.btnInstall.setOnClickListener(v -> {
                h.btnInstall.setEnabled(false);
                h.btnInstall.setText(getString(R.string.store_wait));
                installZip(item, h.btnInstall);
            });

            h.btnUninstall.setOnClickListener(v -> {
                new AlertDialog.Builder(ExtensionStoreActivity.this)
                        .setTitle(getString(R.string.ext_uninstall) + " " + item.name)
                        .setMessage(getString(R.string.store_uninstall_confirm))
                        .setPositiveButton(getString(R.string.ext_uninstall), (d, w) -> uninstallZip(item))
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return storeItems.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvMeta, tvDesc;
            com.google.android.material.button.MaterialButton btnInstall, btnUninstall;

            VH(@NonNull View v) {
                super(v);
                tvName = v.findViewById(R.id.tvName);
                tvMeta = v.findViewById(R.id.tvMeta);
                tvDesc = v.findViewById(R.id.tvDesc);
                btnInstall = v.findViewById(R.id.btnInstall);
                btnUninstall = v.findViewById(R.id.btnUninstall);
            }
        }
    }

    private void installZip(StoreItem item, com.google.android.material.button.MaterialButton btn) {
        ExtensionManager.get(this).importZip(item.zipFile, new ExtensionManager.ImportCallback() {
            @Override
            public void onSuccess(ExtensionInfo info) {
                runOnUiThread(() -> {
                    Toast.makeText(ExtensionStoreActivity.this, getString(R.string.store_installed_success, info.id), Toast.LENGTH_SHORT).show();
                    item.isInstalled = true;
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    Toast.makeText(ExtensionStoreActivity.this, getString(R.string.ext_import_failed, msg), Toast.LENGTH_LONG).show();
                    btn.setEnabled(true);
                    btn.setText(getString(R.string.store_install));
                });
            }
        });
    }

    private void uninstallZip(StoreItem item) {
        try {
            // 修复这里：使用 getExtRoot()
            File extDir = new File(ExtensionManager.get(this).getExtRoot(), item.id);
            if (extDir.exists()) {
                deleteRec(extDir);
            }
            ExtensionManager.get(this).reload();

            Toast.makeText(this, getString(R.string.store_uninstalled_success, item.name), Toast.LENGTH_SHORT).show();
            item.isInstalled = false;
            adapter.notifyDataSetChanged();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
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
}