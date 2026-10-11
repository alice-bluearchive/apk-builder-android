package com.example.androidstudio;

import android.content.Context;

import java.io.File;
import java.util.List;

public interface ExtensionHost {
    Context getAppContext();

    void registerMenuItem(String title, Runnable action);

    /** 注册侧边栏面板。iconName 从内置图标集里选。 */
    void registerSidebarPanel(SidebarPanelSpec spec);

    void log(String msg);

    String getCurrentProject();

    File getProjectRoot();

    void showMessage(String title, String message);

    void showConfirm(String title, String message, Runnable onConfirm);

    void showInput(String title, String hint, String defaultValue, OnInput onOk);

    void showList(String title, List<String> items, OnListItem onItem);

    interface OnInput {
        void onInput(String text);
    }

    interface OnListItem {
        void onItem(int index, String item);
    }
}