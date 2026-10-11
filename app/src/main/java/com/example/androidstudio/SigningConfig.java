package com.example.androidstudio;

import java.io.File;

public class SigningConfig {
    public File projectRoot;
    public boolean signingEnabled;
    public String keystorePath;
    public String keystoreType;
    public String keystoreAlias;
    public String keystorePassword;
    public String keyPassword;

    public static SigningConfig from(File projectRoot, ProjectConfig cfg) {
        SigningConfig s = new SigningConfig();
        s.projectRoot = projectRoot;
        s.signingEnabled = cfg.signingEnabled;
        s.keystorePath = cfg.keystorePath;
        s.keystoreType = cfg.keystoreType;
        s.keystoreAlias = cfg.keystoreAlias;
        s.keystorePassword = cfg.keystorePassword;
        s.keyPassword = cfg.keyPassword;
        return s;
    }
}