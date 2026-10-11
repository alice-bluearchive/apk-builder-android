package com.example.androidstudio;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREF = "settings";
    private static final String KEY_LANG = "app_lang";

    /** 应用指定语言。lang = "zh" 或 "en" */
    public static void apply(Context ctx, String lang) {
        // 存偏好
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANG, lang).apply();

        // 关键：用完整 tag 保证资源匹配 values-zh-rCN
        String tag = "zh".equals(lang) ? "zh-CN" : "en-US";
        LocaleListCompat locales = LocaleListCompat.forLanguageTags(tag);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    /** 当前语言（优先读偏好，未设置则读系统） */
    public static String current(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_LANG, null);
        if (saved != null) return saved;
        // 首次：跟随系统
        Locale locale = ctx.getResources().getConfiguration().getLocales().get(0);
        return "zh".equals(locale.getLanguage()) ? "zh" : "en";
    }

    /** 一次点击：中 ↔ 英 */
    public static void toggle(Context ctx) {
        String cur = current(ctx);
        apply(ctx, "zh".equals(cur) ? "en" : "zh");
    }
}