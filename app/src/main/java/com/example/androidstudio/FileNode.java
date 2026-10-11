package com.example.androidstudio;

import java.io.File;

public class FileNode {
    public String name;
    public File file;
    public boolean isDir;
    public int depth;
    public boolean expanded;
    public FileNode parent;
}