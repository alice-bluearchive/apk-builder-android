package com.example.androidstudio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class ExtensionActivity extends AppCompatActivity {

    private static final int REQ_PICK_ZIP = 3001;

    private ImageButton btnBack;
    private MaterialButton btnImport;
    private RecyclerView listExt;
    private TextView tvEmpty, tvRootPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);

        btnBack = findViewById(R.id.btnBack);
        btnImport = findViewById(R.id.btnImport);
        listExt = findViewById(R.id.listExt);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvRootPath = findViewById(R.id.tvRootPath);

        btnBack.setOnClickListener(v -> finish());

        btnImport.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(i, getString(R.string.ext_pick_zip)),
                    REQ_PICK_ZIP);
        });

        listExt.setLayoutManager(new LinearLayoutManager(this));
        tvRootPath.setText(ExtensionManager.EXT_ROOT);

        refreshList();
    }

    private void refreshList() {
        ExtensionManager mgr = ExtensionManager.get(this);
        List<ExtensionInfo> infos = mgr.getInstalled();

        if (infos.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            listExt.setVisibility(View.GONE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        listExt.setVisibility(View.VISIBLE);

        listExt.setAdapter(new RecyclerView.Adapter<ExtVH>() {
            @Override
            public ExtVH onCreateViewHolder(
                    android.view.ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(
                        R.layout.item_extension, parent, false);
                return new ExtVH(v);
            }

            @Override
            public void onBindViewHolder(ExtVH h, int pos) {
                ExtensionInfo info = infos.get(pos);
                h.tvName.setText(info.name);
                h.tvMeta.setText("v" + info.version
                        + "  ·  " + info.id
                        + (info.author.isEmpty() ? "" : "  ·  by " + info.author));
                h.tvDesc.setText(info.description);
                h.btnUninstall.setOnClickListener(v -> {
                    deleteRec(info.dir);
                    ExtensionManager.get(ExtensionActivity.this).reload();
                    Toast.makeText(ExtensionActivity.this,
                            R.string.ext_uninstalled,
                            Toast.LENGTH_SHORT).show();
                    refreshList();
                });
            }

            @Override
            public int getItemCount() {
                return infos.size();
            }
        });
    }

    private static class ExtVH extends RecyclerView.ViewHolder {
        TextView tvName, tvMeta, tvDesc;
        MaterialButton btnUninstall;

        ExtVH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvExtName);
            tvMeta = v.findViewById(R.id.tvExtMeta);
            tvDesc = v.findViewById(R.id.tvExtDesc);
            btnUninstall = v.findViewById(R.id.btnExtUninstall);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_ZIP) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            File tmp = new File(getCacheDir(), "ext_import.zip");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }

            ExtensionManager.get(this).importZip(tmp, new ExtensionManager.ImportCallback() {
                @Override
                public void onSuccess(ExtensionInfo info) {
                    Toast.makeText(ExtensionActivity.this,
                            getString(R.string.ext_imported, info.id),
                            Toast.LENGTH_SHORT).show();
                    refreshList();
                }

                @Override
                public void onError(String msg) {
                    Toast.makeText(ExtensionActivity.this,
                            getString(R.string.ext_import_failed, msg),
                            Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "读取 zip 失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
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