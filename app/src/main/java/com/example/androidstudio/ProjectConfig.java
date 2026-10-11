package com.example.androidstudio;

import org.json.JSONObject;

public class ProjectConfig {
    public String name = "";
    public String pkg = "com.example.myapplication";
    public String appName = "My App";
    public String mode = "HTML";
    public boolean permInternet = true;
    public boolean permStorage = false;
    public boolean permCamera = false;
    public boolean permLocation = false;
    public String iconFile = "";
    public String htmlFile = "";
    public String mainClass = "";

    // ===== 签名配置 =====
    public boolean signingEnabled = false;
    public String keystorePath = "";
    public String keystoreType = "JKS";
    public String keystoreAlias = "";
    public String keystorePassword = "";
    public String keyPassword = "";

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("pkg", pkg);
        o.put("appName", appName);
        o.put("mode", mode);
        o.put("permInternet", permInternet);
        o.put("permStorage", permStorage);
        o.put("permCamera", permCamera);
        o.put("permLocation", permLocation);
        o.put("iconFile", iconFile);
        o.put("htmlFile", htmlFile);
        o.put("mainClass", mainClass);
        o.put("signingEnabled", signingEnabled);
        o.put("keystorePath", keystorePath);
        o.put("keystoreType", keystoreType);
        o.put("keystoreAlias", keystoreAlias);
        o.put("keystorePassword", keystorePassword);
        o.put("keyPassword", keyPassword);
        return o;
    }

    public static ProjectConfig fromJson(String s) throws Exception {
        ProjectConfig c = new ProjectConfig();
        JSONObject o = new JSONObject(s);
        c.name = o.optString("name", c.name);
        c.pkg = o.optString("pkg", c.pkg);
        c.appName = o.optString("appName", c.appName);
        c.mode = o.optString("mode", c.mode);
        c.permInternet = o.optBoolean("permInternet", true);
        c.permStorage = o.optBoolean("permStorage", false);
        c.permCamera = o.optBoolean("permCamera", false);
        c.permLocation = o.optBoolean("permLocation", false);
        c.iconFile = o.optString("iconFile", "");
        c.htmlFile = o.optString("htmlFile", "");
        c.mainClass = o.optString("mainClass", "");
        c.signingEnabled = o.optBoolean("signingEnabled", false);
        c.keystorePath = o.optString("keystorePath", "");
        c.keystoreType = o.optString("keystoreType", "JKS");
        c.keystoreAlias = o.optString("keystoreAlias", "");
        c.keystorePassword = o.optString("keystorePassword", "");
        c.keyPassword = o.optString("keyPassword", "");
        return c;
    }
}