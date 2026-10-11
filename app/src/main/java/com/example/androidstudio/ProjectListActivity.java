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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ProjectListActivity extends AppCompatActivity {

    private RecyclerView listProjects;
    private LinearLayout emptyState;
    private TextView tvBasePath;
    private MaterialButton btnNewProject;
    private Button btnLang;
    private ProjectAdapter adapter;
    private List<String> projects;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_list);

        AppContext.init(getApplicationContext());

        listProjects = findViewById(R.id.listProjects);
        emptyState = findViewById(R.id.emptyState);
        tvBasePath = findViewById(R.id.tvBasePath);
        btnNewProject = findViewById(R.id.btnNewProject);
        btnLang = findViewById(R.id.btnLang);

        listProjects.setLayoutManager(new LinearLayoutManager(this));
        tvBasePath.setText(ProjectStore.base().getAbsolutePath());

        btnLang.setOnClickListener(v -> LocaleHelper.toggle(this));

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
        EditText et = new EditText(this);
        et.setHint(R.string.new_project_hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setAutofillHints("");
        et.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_new_project) + " · " + mode)
                .setView(et)
                .setPositiveButton(R.string.btn_open_project, (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty() || !name.matches("[A-Za-z0-9_\\-]+")) {
                        toast(getString(R.string.invalid_name));
                        return;
                    }
                    if (ProjectStore.exists(name)) {
                        toast(getString(R.string.project_exists));
                        return;
                    }
                    try {
                        ProjectStore.create(name, mode);
                        toast(getString(R.string.project_created, name));
                        openProject(name);
                    } catch (Exception e) {
                        toast("Failed: " + e.getMessage());
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
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