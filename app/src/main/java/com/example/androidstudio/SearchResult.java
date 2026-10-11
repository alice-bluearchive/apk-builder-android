package com.example.androidstudio;

import java.io.File;

public class SearchResult {
    public File file;
    public int line;
    public String lineText;
    public int matchStart;   // 匹配起始位置（用于高亮）
    public int matchEnd;
}