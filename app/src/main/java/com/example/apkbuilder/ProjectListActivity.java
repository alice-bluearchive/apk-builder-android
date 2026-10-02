package com.example.apkbuilder;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class ProjectListActivity extends AppCompatActivity {

    private static final String PREF = "settings";
    private static final String KEY_LANG = "lang";

    private ListView listProjects;
    private TextView tvEmpty, tvBasePath;
    private Button btnNewProject, btnLang;
    private List<String> projects;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREF, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "system");
        if ("system".equals(lang)) {
            super.attachBaseContext(newBase);
            return;
        }
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_list);

        listProjects = findViewById(R.id.listProjects);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvBasePath = findViewById(R.id.tvBasePath);
        btnNewProject = findViewById(R.id.btnNewProject);
        btnLang = findViewById(R.id.btnLang);

        tvBasePath.setText(ProjectStore.base().getAbsolutePath());

        btnLang.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
            String cur = sp.getString(KEY_LANG, "system");
            String next;
            if ("zh".equals(cur)) next = "en";
            else if ("en".equals(cur)) next = "system";
            else next = "zh";
            sp.edit().putString(KEY_LANG, next).apply();
            recreate();
        });

        btnNewProject.setOnClickListener(v -> promptNewProject());

        listProjects.setOnItemClickListener((parent, view, position, id) -> {
            String name = projects.get(position);
            openProject(name);
        });

        listProjects.setOnItemLongClickListener((parent, view, position, id) -> {
            String name = projects.get(position);
            confirmDelete(name);
            return true;
        });

        checkStoragePermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        if (!hasStoragePermission()) {
            tvEmpty.setVisibility(View.GONE);
            return;
        }
        projects = ProjectStore.list();
        if (projects.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            listProjects.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            listProjects.setVisibility(View.VISIBLE);
            listProjects.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, projects));
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

    private void promptNewProject() {
        EditText et = new EditText(this);
        et.setHint(R.string.new_project_hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setAutofillHints("");
        et.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);

        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_new_project)
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
                        ProjectStore.create(name);
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
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("project", name);
        startActivity(i);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
