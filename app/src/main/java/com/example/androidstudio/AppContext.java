package com.example.androidstudio;

import android.content.Context;

public class AppContext {
    private static Context sCtx;
    public static void init(Context ctx) { sCtx = ctx.getApplicationContext(); }
    public static Context get() { return sCtx; }
}