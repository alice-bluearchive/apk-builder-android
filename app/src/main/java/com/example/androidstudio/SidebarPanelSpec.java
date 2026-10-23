package com.example.androidstudio;

import android.content.Context;
import android.view.View;

import java.io.File;
import java.util.List;

public class SidebarPanelSpec {
    /** 唯一 id */
    public String id;
    /** 面板标题 */
    public String title;
    /** 内置图标名：files/search/git/extensions/terminal/robot/database/cloud/settings */
    public String iconName;
    /** 扩展自带的图标（相对扩展目录，如 "icon.png"）。非空时优先于 iconName */
    public String iconFile;
    /** 按钮列表（viewFactory 为 null 时使用） */
    public List<SidebarPanelItem> items;
    /** 面板打开时回调 */
    public Runnable onOpen;
    /** 可选：完全自定义面板 View。非 null 时忽略 items */
    public PanelViewFactory viewFactory;

    /** 由主 App 在注册时填充，扩展不要设置 */
    public File extDir;

    public interface PanelViewFactory {
        View create(Context ctx);
    }
}