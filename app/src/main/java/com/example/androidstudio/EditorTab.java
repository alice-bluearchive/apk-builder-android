package com.example.androidstudio;

public class EditorTab {
    public String path;
    public String name;
    public boolean dirty;

    public EditorTab(String path, String name) {
        this.path = path;
        this.name = name;
    }
}