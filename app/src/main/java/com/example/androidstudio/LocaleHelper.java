package com.example.androidstudio;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.util.Locale;

public class LocaleHelper {

    private static final String PREF = "settings";
    private static final String KEY_LANG = "app_lang";

    public static void apply(Context ctx, String lang) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANG, lang).apply();

        // 使用完整的 tag "zh-CN" 匹配 values-zh-rCN 文件夹
        String tag = "zh".equals(lang) ? "zh-CN" : "en-US";
        LocaleListCompat locales = LocaleListCompat.forLanguageTags(tag);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    public static String current(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_LANG, null);
        if (saved != null) return saved;

        Locale locale = ctx.getResources().getConfiguration().getLocales().get(0);
        return "zh".equals(locale.getLanguage()) ? "zh" : "en";
    }

    public static void toggle(Context ctx) {
        String cur = current(ctx);
        apply(ctx, "zh".equals(cur) ? "en" : "zh");
    }
}