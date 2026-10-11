package com.example.androidstudio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

public class SigningActivity extends AppCompatActivity {

    private static final int REQ_PICK_KEYSTORE = 2001;

    private String projectName;
    private ProjectConfig cfg;

    private ImageButton btnBack;
    private SwitchCompat swEnable;
    private TextView tvKeystorePath, tvStatus;
    private MaterialButton btnPickKeystore, btnTest, btnClear, btnSave;
    private RadioButton rbJks, rbPkcs12;
    private EditText etAlias, etStorePassword, etKeyPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signing);

        projectName = getIntent().getStringExtra("project");
        if (projectName == null) {
            Toast.makeText(this, "No project", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        cfg = ProjectStore.loadConfig(projectName);

        bindViews();
        loadConfigToUi();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        swEnable = findViewById(R.id.swEnable);
        tvKeystorePath = findViewById(R.id.tvKeystorePath);
        tvStatus = findViewById(R.id.tvStatus);
        btnPickKeystore = findViewById(R.id.btnPickKeystore);
        btnTest = findViewById(R.id.btnTest);
        btnClear = findViewById(R.id.btnClear);
        btnSave = findViewById(R.id.btnSave);
        rbJks = findViewById(R.id.rbJks);
        rbPkcs12 = findViewById(R.id.rbPkcs12);
        etAlias = findViewById(R.id.etAlias);
        etStorePassword = findViewById(R.id.etStorePassword);
        etKeyPassword = findViewById(R.id.etKeyPassword);

        btnBack.setOnClickListener(v -> finish());

        btnPickKeystore.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(i, getString(R.string.sign_pick_keystore)),
                    REQ_PICK_KEYSTORE);
        });

        btnTest.setOnClickListener(v -> testSigning());
        btnClear.setOnClickListener(v -> clearConfig());
        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void loadConfigToUi() {
        swEnable.setChecked(cfg.signingEnabled);
        etAlias.setText(cfg.keystoreAlias);
        etStorePassword.setText(cfg.keystorePassword);
        etKeyPassword.setText(cfg.keyPassword);

        if ("PKCS12".equals(cfg.keystoreType)) rbPkcs12.setChecked(true);
        else rbJks.setChecked(true);

        if (cfg.keystorePath != null && !cfg.keystorePath.isEmpty()) {
            File f = new File(ProjectStore.dir(projectName), cfg.keystorePath);
            if (f.exists()) {
                tvKeystorePath.setText(f.getName() + "  (" + f.length() + " B)");
            } else {
                tvKeystorePath.setText(getString(R.string.sign_no_keystore));
            }
        } else {
            tvKeystorePath.setText(getString(R.string.sign_no_keystore));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_KEYSTORE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            String display = getDisplayName(uri);
            if (!display.endsWith(".jks") && !display.endsWith(".p12")
                    && !display.endsWith(".keystore")
                    && !display.endsWith(".bks")) {
                display = "release.jks";
            }

            File signingDir = new File(ProjectStore.dir(projectName), "signing");
            if (!signingDir.exists()) signingDir.mkdirs();
            File dst = new File(signingDir, display);

            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }

            cfg.keystorePath = "signing/" + display;
            autoDetectType(dst);

            tvKeystorePath.setText(dst.getName() + "  (" + dst.length() + " B)");
            showStatus(getString(R.string.sign_imported), true);
        } catch (Exception e) {
            showStatus(getString(R.string.sign_import_failed) + ": " + e.getMessage(), false);
        }
    }

    /** 读文件头自动判断 JKS / PKCS12 */
    private void autoDetectType(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] head = new byte[4];
            int n = in.read(head);
            if (n >= 2) {
                int b0 = head[0] & 0xFF;
                int b1 = head[1] & 0xFF;
                // JKS: FE ED FE ED
                if (b0 == 0xFE && b1 == 0xED) {
                    rbJks.setChecked(true);
                    cfg.keystoreType = "JKS";
                    return;
                }
                // PKCS12: 30 82
                if (b0 == 0x30 && b1 == 0x82) {
                    rbPkcs12.setChecked(true);
                    cfg.keystoreType = "PKCS12";
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private void testSigning() {
        String alias = etAlias.getText().toString().trim();
        String storePass = etStorePassword.getText().toString();
        String keyPass = etKeyPassword.getText().toString();
        if (keyPass.isEmpty()) keyPass = storePass;

        if (cfg.keystorePath == null || cfg.keystorePath.isEmpty()) {
            showStatus(getString(R.string.sign_no_keystore), false);
            return;
        }
        File ksFile = new File(ProjectStore.dir(projectName), cfg.keystorePath);
        if (!ksFile.exists()) {
            showStatus(getString(R.string.sign_file_missing), false);
            return;
        }
        if (alias.isEmpty()) {
            showStatus(getString(R.string.sign_alias_empty), false);
            return;
        }

        try {
            String type = rbPkcs12.isChecked() ? "PKCS12" : "JKS";
            KeyStore ks = KeyStore.getInstance(type);
            try (InputStream in = new FileInputStream(ksFile)) {
                ks.load(in, storePass.toCharArray());
            }
            PrivateKey key = (PrivateKey) ks.getKey(alias, keyPass.toCharArray());
            if (key == null) {
                showStatus(getString(R.string.sign_alias_not_found), false);
                return;
            }
            Certificate[] chain = ks.getCertificateChain(alias);
            if (chain == null || chain.length == 0) {
                showStatus(getString(R.string.sign_no_cert_chain), false);
                return;
            }
            showStatus(getString(R.string.sign_ok, chain.length), true);
        } catch (Exception e) {
            showStatus(getString(R.string.sign_test_failed) + ": " + e.getMessage(), false);
        }
    }

    private void clearConfig() {
        cfg.signingEnabled = false;
        cfg.keystorePath = "";
        cfg.keystoreType = "JKS";
        cfg.keystoreAlias = "";
        cfg.keystorePassword = "";
        cfg.keyPassword = "";

        File signingDir = new File(ProjectStore.dir(projectName), "signing");
        deleteRec(signingDir);

        swEnable.setChecked(false);
        rbJks.setChecked(true);
        etAlias.setText("");
        etStorePassword.setText("");
        etKeyPassword.setText("");
        tvKeystorePath.setText(getString(R.string.sign_no_keystore));
        tvStatus.setVisibility(View.GONE);

        try {
            ProjectStore.saveConfig(cfg);
        } catch (Exception ignored) {}
        Toast.makeText(this, R.string.sign_cleared, Toast.LENGTH_SHORT).show();
    }

    private void saveConfig() {
        String alias = etAlias.getText().toString().trim();
        String storePass = etStorePassword.getText().toString();
        String keyPass = etKeyPassword.getText().toString();

        if (swEnable.isChecked()) {
            if (cfg.keystorePath == null || cfg.keystorePath.isEmpty()) {
                showStatus(getString(R.string.sign_no_keystore), false);
                return;
            }
            if (alias.isEmpty()) {
                showStatus(getString(R.string.sign_alias_empty), false);
                return;
            }
            if (storePass.isEmpty()) {
                showStatus(getString(R.string.sign_pass_empty), false);
                return;
            }
        }

        cfg.signingEnabled = swEnable.isChecked();
        cfg.keystoreType = rbPkcs12.isChecked() ? "PKCS12" : "JKS";
        cfg.keystoreAlias = alias;
        cfg.keystorePassword = storePass;
        cfg.keyPassword = keyPass;

        try {
            ProjectStore.saveConfig(cfg);
            Toast.makeText(this, R.string.sign_saved, Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            showStatus(getString(R.string.sign_save_failed) + ": " + e.getMessage(), false);
        }
    }

    private void showStatus(String msg, boolean ok) {
        tvStatus.setText(msg);
        tvStatus.setTextColor(ok ? 0xFF81C784 : 0xFFE57373);
        tvStatus.setVisibility(View.VISIBLE);
    }

    private String getDisplayName(Uri uri) {
        try {
            android.database.Cursor c = getContentResolver()
                    .query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (c.moveToFirst() && idx >= 0) {
                    String name = c.getString(idx);
                    c.close();
                    return name == null ? "release.jks" : name;
                }
                c.close();
            }
        } catch (Exception ignored) {}
        return "release.jks";
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