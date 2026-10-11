package com.example.androidstudio;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class ExtensionInfo {
    public String id;
    public String name;
    public String version;
    public int minAppVersion = 1;
    public String entryClass;
    public String description = "";
    public String author = "";
    public File dir;      // 扩展解压后的目录

    public static ExtensionInfo fromManifest(File extDir) throws Exception {
        File mf = new File(extDir, "manifest.json");
        if (!mf.exists()) throw new Exception("manifest.json 不存在");

        String text;
        try (InputStream in = new FileInputStream(mf)) {
            byte[] buf = new byte[(int) mf.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            text = new String(buf, 0, off, "UTF-8");
        }

        JSONObject o = new JSONObject(text);
        ExtensionInfo e = new ExtensionInfo();
        e.id = o.optString("id", "");
        e.name = o.optString("name", e.id);
        e.version = o.optString("version", "0");
        e.minAppVersion = o.optInt("minAppVersion", 1);
        e.entryClass = o.optString("entryClass", "");
        e.description = o.optString("description", "");
        e.author = o.optString("author", "");
        e.dir = extDir;
        if (e.id.isEmpty()) throw new Exception("manifest 缺 id");
        if (e.entryClass.isEmpty()) throw new Exception("manifest 缺 entryClass");
        return e;
    }
}