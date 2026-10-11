package com.example.androidstudio;

import java.util.List;

public class SidebarPanelSpec {
    public String id;
    public String title;
    /**
     * 只能从主 App 内置图标集选：
     * "files" / "search" / "git" / "extensions" / "terminal"
     * 匹配已有图标 → 内容填进那个面板
     * 未匹配 → 在图标栏末尾新增一个图标
     */
    public String iconName;
    public List<SidebarPanelItem> items;
    public Runnable onOpen;
}