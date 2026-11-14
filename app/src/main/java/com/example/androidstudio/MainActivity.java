package com.example.androidstudio;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PICK_ICON = 1002;

    private EditText etPackage, etAppName;
    private MaterialButton btnPickIcon;
    private ImageButton btnBack;
    private TextView btnLang;
    private ImageView ivIcon;

    // 适配新的 MaterialSwitch
    private MaterialSwitch permInternet, permStorage, permCamera, permLocation;
    private TextView tvIconName;

    private File pickedIcon = null;
    private String projectName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etPackage = findViewById(R.id.etPackage);
        etAppName = findViewById(R.id.etAppName);
        btnPickIcon = findViewById(R.id.btnPickIcon);
        btnLang = findViewById(R.id.btnLang);
        btnBack = findViewById(R.id.btnBack);
        ivIcon = findViewById(R.id.ivIcon);

        // 绑定 MaterialSwitch
        permInternet = findViewById(R.id.permInternet);
        permStorage = findViewById(R.id.permStorage);
        permCamera = findViewById(R.id.permCamera);
        permLocation = findViewById(R.id.permLocation);
        tvIconName = findViewById(R.id.tvIconName);

        projectName = getIntent().getStringExtra("project");
        if (projectName != null) {
            // tvProjectName 已被移除，由顶部标题栏代替
            loadProjectConfig();
        }

        btnBack.setOnClickListener(v -> {
            saveConfigQuietly();
            finish();
        });

        btnLang.setOnClickListener(v -> {
            LocaleHelper.toggle(this);
            recreate(); // 切换语言后重建界面
        });

        btnPickIcon.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.pick_icon_title)),
                    REQ_PICK_ICON);
        });
    }

    private void loadProjectConfig() {
        try {
            ProjectConfig cfg = ProjectStore.loadConfig(projectName);
            if (cfg.pkg != null && !cfg.pkg.isEmpty()) etPackage.setText(cfg.pkg);
            if (cfg.appName != null && !cfg.appName.isEmpty()) etAppName.setText(cfg.appName);

            permInternet.setChecked(cfg.permInternet);
            permStorage.setChecked(cfg.permStorage);
            permCamera.setChecked(cfg.permCamera);
            permLocation.setChecked(cfg.permLocation);

            // 如果之前配过图标，显示出来
            if (cfg.iconFile != null && !cfg.iconFile.isEmpty()) {
                File f = new File(ProjectStore.dir(projectName), cfg.iconFile);
                if (f.exists()) {
                    pickedIcon = f;
                    ivIcon.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
                    tvIconName.setText(f.getName());
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "加载配置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveConfigQuietly() {
        if (projectName == null) return;
        try {
            ProjectConfig cfg = ProjectStore.loadConfig(projectName);
            cfg.pkg = etPackage.getText().toString().trim();
            cfg.appName = etAppName.getText().toString().trim();

            // 获取开关状态
            cfg.permInternet = permInternet.isChecked();
            cfg.permStorage = permStorage.isChecked();
            cfg.permCamera = permCamera.isChecked();
            cfg.permLocation = permLocation.isChecked();

            // 如果重新选了图标，要拷到项目目录里
            if (pickedIcon != null && !pickedIcon.getAbsolutePath().contains(ProjectStore.dir(projectName).getAbsolutePath())) {
                File dst = new File(ProjectStore.dir(projectName), "icon.png");
                try (InputStream in = new java.io.FileInputStream(pickedIcon);
                     OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                cfg.iconFile = "icon.png";
            }

            ProjectStore.saveConfig(cfg);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_ICON && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                Bitmap src = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                if (src == null) return;

                int w = src.getWidth();
                int h = src.getHeight();
                float scale = Math.min(192f / w, 192f / h);
                int nw = Math.max(1, Math.round(w * scale));
                int nh = Math.max(1, Math.round(h * scale));
                Bitmap scaled = Bitmap.createScaledBitmap(src, nw, nh, true);

                File dst = new File(getCacheDir(), "picked_icon.png");
                try (FileOutputStream fos = new FileOutputStream(dst)) {
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                if (scaled != src) scaled.recycle();
                src.recycle();

                pickedIcon = dst;
                ivIcon.setImageBitmap(BitmapFactory.decodeFile(dst.getAbsolutePath()));
                tvIconName.setText("picked_icon.png");
                Toast.makeText(this, "图标已选择", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "图标加载失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
}