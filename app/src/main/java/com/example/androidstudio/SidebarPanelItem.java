package com.example.androidstudio;

public class SidebarPanelItem {
    public String label;
    public Runnable onClick;

    public SidebarPanelItem() {}

    public SidebarPanelItem(String label, Runnable onClick) {
        this.label = label;
        this.onClick = onClick;
    }
}