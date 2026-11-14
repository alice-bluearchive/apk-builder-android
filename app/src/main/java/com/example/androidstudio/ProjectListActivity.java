package com.example.androidstudio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ProjectListActivity extends AppCompatActivity {

    private RecyclerView listProjects;
    private LinearLayout emptyState;
    private TextView tvBasePath;
    private ImageButton btnNewProject;
    private TextView btnLang;
    private ProjectAdapter adapter;
    private List<String> projects;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocaleHelper.apply(this, LocaleHelper.current(this));
        setContentView(R.layout.activity_project_list);

        AppContext.init(getApplicationContext());

        listProjects = findViewById(R.id.listProjects);
        emptyState = findViewById(R.id.emptyState);
        tvBasePath = findViewById(R.id.tvBasePath);
        btnNewProject = findViewById(R.id.btnNewProject);
        btnLang = findViewById(R.id.btnLang);

        listProjects.setLayoutManager(new LinearLayoutManager(this));
        tvBasePath.setText(getString(R.string.workspace_hint, ProjectStore.base().getAbsolutePath()));

        btnLang.setOnClickListener(v -> {
            LocaleHelper.toggle(this);
            recreate(); // 切换语言后重建界面刷新
        });

        btnNewProject.setOnClickListener(v -> promptMode());

        checkStoragePermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        if (!hasStoragePermission()) {
            emptyState.setVisibility(View.GONE);
            return;
        }
        projects = ProjectStore.list();
        if (projects.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            listProjects.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            listProjects.setVisibility(View.VISIBLE);
            adapter = new ProjectAdapter(projects, new ProjectAdapter.OnClick() {
                @Override
                public void onClick(String name) {
                    openProject(name);
                }
                @Override
                public void onDelete(String name) {
                    confirmDelete(name);
                }
            });
            listProjects.setAdapter(adapter);
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void checkStoragePermission() {
        if (hasStoragePermission()) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.btn_grant, (d, w) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Exception e) {
                            Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(i);
                        }
                    } else {
                        requestPermissions(new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        }, 100);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, (d, w) -> finish())
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshList();
            } else {
                finish();
            }
        }
    }

    private void promptMode() {
        final String[] labels = new String[]{
                getString(R.string.mode_html),
                "Empty View (Java)",
                "Empty (Java)"
        };
        final String[] types = new String[]{
                TemplateManager.TYPE_HTML,
                TemplateManager.TYPE_JAVA_EMPTY_VIEW,
                TemplateManager.TYPE_JAVA_EMPTY
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_mode_title)
                .setItems(labels, (d, which) -> promptProjectName(types[which]))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void promptProjectName(final String mode) {
        // 第一次：询问应用名称（支持中文）
        EditText etAppName = new EditText(this);
        etAppName.setHint("例如：我的牛逼应用");
        LinearLayout c1 = new LinearLayout(this);
        c1.setPadding(48, 24, 48, 0);
        c1.addView(etAppName);

        new AlertDialog.Builder(this)
                .setTitle("第一步：输入应用名称")
                .setView(c1)
                .setCancelable(false)
                .setPositiveButton("下一步", (d, w) -> {
                    String appName = etAppName.getText().toString().trim();
                    if (appName.isEmpty()) {
                        toast("应用名称不能为空");
                        return;
                    }
                    // 进入第二次弹窗
                    promptPackageName(mode, appName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void promptPackageName(final String mode, final String appName) {
        // 第二次：询问包名（必须是英文）
        EditText etPkg = new EditText(this);
        // 默认生成一个随机包名，防止重复
        String defaultPkg = "com.example.app" + (System.currentTimeMillis() % 10000);
        etPkg.setText(defaultPkg);
        LinearLayout c2 = new LinearLayout(this);
        c2.setPadding(48, 24, 48, 0);
        c2.addView(etPkg);

        new AlertDialog.Builder(this)
                .setTitle("第二步：输入包名 (仅英文和点)")
                .setView(c2)
                .setCancelable(false)
                .setPositiveButton("创建项目", (d, w) -> {
                    String pkg = etPkg.getText().toString().trim();
                    if (pkg.isEmpty() || !pkg.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")) {
                        toast("包名不合法，例如：com.example.myapp");
                        return;
                    }

                    // 用包名的最后一段作为文件夹名
                    String folderName = pkg.substring(pkg.lastIndexOf(".") + 1);
                    if (ProjectStore.exists(folderName)) {
                        toast("项目文件夹已存在，请换一个包名");
                        return;
                    }

                    try {
                        ProjectStore.create(folderName, mode, appName, pkg);
                        toast("已创建项目: " + appName);
                        openProject(folderName);
                    } catch (Exception e) {
                        toast("失败: " + e.getMessage());
                    }
                })
                .setNegativeButton("上一步", null)
                .show();
    }

    private void confirmDelete(String name) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_confirm_msg, name))
                .setPositiveButton(R.string.btn_yes, (d, w) -> {
                    ProjectStore.delete(name);
                    refreshList();
                })
                .setNegativeButton(R.string.btn_no, null)
                .show();
    }

    private void openProject(String name) {
        Intent i = new Intent(this, IdeActivity.class);
        i.putExtra("project", name);
        startActivity(i);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}