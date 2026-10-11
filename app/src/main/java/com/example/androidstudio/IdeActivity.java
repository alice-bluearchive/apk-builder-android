package com.example.androidstudio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class IdeActivity extends AppCompatActivity {

    private static final int TERMINAL_COLLAPSED_DP = 40;
    private static final int TERMINAL_EXPANDED_DP = 220;
    private static final int TAB_ACTIVE_BG = 0xFF1E1E1E;
    private static final int TAB_INACTIVE_BG = 0xFF2D2D2D;
    private static final int ICON_ON = 0xFFFFC107;
    private static final int ICON_OFF = 0xFF5A5A5A;

    private static final int MIN_PANEL_DP = 140;
    private static final int MAX_PANEL_DP = 520;
    private static final int DEFAULT_PANEL_DP = 240;
    private static final String PREF_IDE = "ide_prefs";
    private static final String PREF_PANEL_WIDTH = "panel_width";

    private String projectName;
    private File projectRoot;
    private RecyclerView fileTree;
    private FileTreeAdapter adapter;

    private TextView tvTitle, tvEditorPlaceholder, tvTerminal, tvTerminalArrow;
    private ImageButton btnBack, btnMenu, btnBuild, btnSave, btnToggleSidebar,
            btnTerminalCopy, btnTerminalClear,
            btnNewFile, btnNewFolder, btnRefreshTree;

    private ImageButton iconFiles, iconSearch, iconGit, iconExtensions;
    private LinearLayout iconBar, iconBarExt;
    private FrameLayout panelContainer;
    private FrameLayout panelExtContainer;
    private LinearLayout panelFiles, panelSearch, panelGit, panelExtensions;
    private LinearLayout gitPanelContent;
    private TextView tvGitPlaceholder;
    private TextView tvExtCount;
    private MaterialButton btnExtManage;

    private EditText etSearch;
    private ImageButton btnSearchClear;
    private TextView tvSearchStatus;
    private RecyclerView listSearchResult;
    private final List<SearchResult> searchResults = new ArrayList<>();
    private SearchAdapter searchAdapter;
    private volatile boolean searching = false;

    private LinearLayout terminalPanel, terminalHeader, tabBar;
    private ScrollView terminalScroll;
    private HorizontalScrollView tabScroll;
    private WebView webEditor;

    private final List<FileNode> allNodes = new ArrayList<>();
    private final List<FileNode> visibleNodes = new ArrayList<>();

    private final List<EditorTab> tabs = new ArrayList<>();
    private EditorTab currentTab = null;

    private final List<ExtensionManager.MenuItem> extMenuItems = new ArrayList<>();
    private final List<SidebarPanelSpec> extSidebarPanels = new ArrayList<>();
    private int lastExtGeneration = -1;

    private boolean terminalExpanded = false;
    private volatile boolean building = false;

    private String activePanel = "files";
    private String lastActivePanel = "files";

    private View panelDivider;
    private float panelDragStartX;
    private int panelDragStartWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ide);

        projectName = getIntent().getStringExtra("project");
        if (projectName == null) {
            Toast.makeText(this, "No project", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        projectRoot = ProjectStore.dir(projectName);

        bindViews();
        setupToolbar();
        setupSideBar();
        setupTerminal();
        setupWebEditor();
        setupFileTree();
        hideEditor();
        refreshExtPanel();

        ExtensionManager mgr = ExtensionManager.get(this);
        mgr.setLogger(this::terminalLog);
        mgr.ensureInit();
        mgr.setCurrentProject(projectName);
        extMenuItems.clear();
        extMenuItems.addAll(mgr.getMenuItems());
        extSidebarPanels.clear();
        extSidebarPanels.addAll(mgr.getSidebarPanels());
        lastExtGeneration = mgr.getGeneration();
        renderExtensionPanels();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(projectName);
        btnBack = findViewById(R.id.btnBack);
        btnToggleSidebar = findViewById(R.id.btnToggleSidebar);
        btnMenu = findViewById(R.id.btnMenu);
        btnBuild = findViewById(R.id.btnBuild);
        btnSave = findViewById(R.id.btnSave);
        fileTree = findViewById(R.id.fileTree);
        tvEditorPlaceholder = findViewById(R.id.tvEditorPlaceholder);
        terminalPanel = findViewById(R.id.terminalPanel);
        terminalHeader = findViewById(R.id.terminalHeader);
        terminalScroll = findViewById(R.id.terminalScroll);
        tvTerminal = findViewById(R.id.tvTerminal);
        tvTerminalArrow = findViewById(R.id.tvTerminalArrow);
        btnTerminalCopy = findViewById(R.id.btnTerminalCopy);
        btnTerminalClear = findViewById(R.id.btnTerminalClear);
        tabScroll = findViewById(R.id.tabScroll);
        tabBar = findViewById(R.id.tabBar);
        webEditor = findViewById(R.id.webEditor);
        btnNewFile = findViewById(R.id.btnNewFile);
        btnNewFolder = findViewById(R.id.btnNewFolder);
        btnRefreshTree = findViewById(R.id.btnRefreshTree);

        iconBar = findViewById(R.id.iconBar);
        iconBarExt = findViewById(R.id.iconBarExt);
        iconFiles = findViewById(R.id.iconFiles);
        iconSearch = findViewById(R.id.iconSearch);
        iconGit = findViewById(R.id.iconGit);
        iconExtensions = findViewById(R.id.iconExtensions);
        panelContainer = findViewById(R.id.panelContainer);
        panelExtContainer = findViewById(R.id.panelExtContainer);
        panelFiles = findViewById(R.id.panelFiles);
        panelSearch = findViewById(R.id.panelSearch);
        panelGit = findViewById(R.id.panelGit);
        panelExtensions = findViewById(R.id.panelExtensions);
        gitPanelContent = findViewById(R.id.gitPanelContent);
        tvGitPlaceholder = findViewById(R.id.tvGitPlaceholder);
        tvExtCount = findViewById(R.id.tvExtCount);
        btnExtManage = findViewById(R.id.btnExtManage);
        etSearch = findViewById(R.id.etSearch);
        btnSearchClear = findViewById(R.id.btnSearchClear);
        tvSearchStatus = findViewById(R.id.tvSearchStatus);
        listSearchResult = findViewById(R.id.listSearchResult);
        panelDivider = findViewById(R.id.panelDivider);
    }

    private void setupToolbar() {
        btnBack.setOnClickListener(v -> finish());
        btnToggleSidebar.setOnClickListener(v -> toggleSidebar());
        btnMenu.setOnClickListener(v -> showMenu());
        btnSave.setOnClickListener(v -> saveCurrentFile());
        btnBuild.setOnClickListener(v -> onBuildClicked());

        btnNewFile.setOnClickListener(v -> promptCreate(projectRoot, false));
        btnNewFolder.setOnClickListener(v -> promptCreate(projectRoot, true));
        btnRefreshTree.setOnClickListener(v -> rebuildTree());
    }

    // ============== 侧边栏 ==============

    private void setupSideBar() {
        iconFiles.setOnClickListener(v -> togglePanel("files"));
        iconSearch.setOnClickListener(v -> togglePanel("search"));
        iconGit.setOnClickListener(v -> togglePanel("git"));
        iconExtensions.setOnClickListener(v -> togglePanel("extensions"));

        btnExtManage.setOnClickListener(v ->
                startActivity(new Intent(this, ExtensionActivity.class)));

        setupSearch();
        setupPanelResize();

        int saved = getSharedPreferences(PREF_IDE, MODE_PRIVATE)
                .getInt(PREF_PANEL_WIDTH, dp(DEFAULT_PANEL_DP));
        ViewGroup.LayoutParams lp = panelContainer.getLayoutParams();
        lp.width = saved;
        panelContainer.setLayoutParams(lp);

        selectPanel("files");
    }

    private void setupPanelResize() {
        panelDivider.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    panelDragStartX = event.getRawX();
                    panelDragStartWidth = panelContainer.getWidth();
                    return true;
                case android.view.MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - panelDragStartX;
                    int newWidth = (int) (panelDragStartWidth + dx);
                    int minW = dp(MIN_PANEL_DP);
                    int maxW = dp(MAX_PANEL_DP);
                    if (newWidth < minW) newWidth = minW;
                    if (newWidth > maxW) newWidth = maxW;
                    ViewGroup.LayoutParams lp = panelContainer.getLayoutParams();
                    lp.width = newWidth;
                    panelContainer.setLayoutParams(lp);
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: {
                    int w = panelContainer.getWidth();
                    getSharedPreferences(PREF_IDE, MODE_PRIVATE)
                            .edit().putInt(PREF_PANEL_WIDTH, w).apply();
                    return true;
                }
            }
            return false;
        });

        panelDivider.setOnClickListener(new View.OnClickListener() {
            private long lastClick = 0;
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastClick < 300) {
                    int def = dp(DEFAULT_PANEL_DP);
                    ViewGroup.LayoutParams lp = panelContainer.getLayoutParams();
                    lp.width = def;
                    panelContainer.setLayoutParams(lp);
                    getSharedPreferences(PREF_IDE, MODE_PRIVATE)
                            .edit().putInt(PREF_PANEL_WIDTH, def).apply();
                }
                lastClick = now;
            }
        });
    }

    private void togglePanel(String name) {
        if (iconBar.getVisibility() != View.VISIBLE) {
            showSidebar();
            selectPanel(name);
            return;
        }
        if (name.equals(activePanel)) {
            hideSidebar();
            return;
        }
        selectPanel(name);
    }

    private void hideSidebar() {
        if (activePanel != null) lastActivePanel = activePanel;
        activePanel = null;
        iconBar.setVisibility(View.GONE);
        panelContainer.setVisibility(View.GONE);
        panelDivider.setVisibility(View.GONE);
        updateIconTint();
    }

    private void showSidebar() {
        iconBar.setVisibility(View.VISIBLE);
        panelContainer.setVisibility(View.VISIBLE);
        panelDivider.setVisibility(View.VISIBLE);
    }

    private void toggleSidebar() {
        if (iconBar.getVisibility() == View.VISIBLE) {
            hideSidebar();
        } else {
            showSidebar();
            String target = (lastActivePanel == null) ? "files" : lastActivePanel;
            selectPanel(target);
        }
    }

    private void selectPanel(String name) {
        activePanel = name;
        lastActivePanel = name;
        iconBar.setVisibility(View.VISIBLE);
        panelContainer.setVisibility(View.VISIBLE);
        panelDivider.setVisibility(View.VISIBLE);
        panelExtContainer.setVisibility(View.GONE);

        panelFiles.setVisibility("files".equals(name) ? View.VISIBLE : View.GONE);
        panelSearch.setVisibility("search".equals(name) ? View.VISIBLE : View.GONE);
        panelGit.setVisibility("git".equals(name) ? View.VISIBLE : View.GONE);
        panelExtensions.setVisibility("extensions".equals(name) ? View.VISIBLE : View.GONE);

        updateIconTint();
    }

    private void updateIconTint() {
        boolean sidebarVisible = iconBar.getVisibility() == View.VISIBLE;
        iconFiles.setImageTintList(ColorStateList.valueOf(
                sidebarVisible && "files".equals(activePanel) ? ICON_ON : ICON_OFF));
        iconSearch.setImageTintList(ColorStateList.valueOf(
                sidebarVisible && "search".equals(activePanel) ? ICON_ON : ICON_OFF));
        iconGit.setImageTintList(ColorStateList.valueOf(
                sidebarVisible && "git".equals(activePanel) ? ICON_ON : ICON_OFF));
        iconExtensions.setImageTintList(ColorStateList.valueOf(
                sidebarVisible && "extensions".equals(activePanel) ? ICON_ON : ICON_OFF));

        if (iconBarExt != null) {
            for (int i = 0; i < iconBarExt.getChildCount(); i++) {
                View c = iconBarExt.getChildAt(i);
                if (c instanceof ImageButton) {
                    boolean on = sidebarVisible && activePanel != null
                            && activePanel.startsWith("ext:");
                    ((ImageButton) c).setImageTintList(ColorStateList.valueOf(
                            on ? ICON_ON : ICON_OFF));
                }
            }
        }
    }

    private void refreshExtPanel() {
        int n = ExtensionManager.get(this).getInstalled().size();
        tvExtCount.setText(getString(R.string.ide_ext_panel_hint, n));
    }

    // ============== 渲染扩展的侧边栏面板 ==============

    private void renderExtensionPanels() {
        iconBarExt.removeAllViews();
        panelExtContainer.removeAllViews();

        // git 面板先复位
        gitPanelContent.removeAllViews();
        if (tvGitPlaceholder != null) {
            if (tvGitPlaceholder.getParent() != null) {
                ((ViewGroup) tvGitPlaceholder.getParent()).removeView(tvGitPlaceholder);
            }
            gitPanelContent.addView(tvGitPlaceholder);
            tvGitPlaceholder.setVisibility(View.VISIBLE);
        }

        for (SidebarPanelSpec spec : extSidebarPanels) {
            if ("git".equals(spec.iconName)) {
                renderIntoGitPanel(spec);
            } else {
                renderCustomPanel(spec);
            }
        }
    }

    private void renderIntoGitPanel(SidebarPanelSpec spec) {
        if (tvGitPlaceholder != null) {
            tvGitPlaceholder.setVisibility(View.GONE);
        }
        if (spec.items == null) return;
        for (SidebarPanelItem item : spec.items) {
            MaterialButton btn = makePanelButton(item.label);
            btn.setOnClickListener(v -> safeRun(item.onClick));
            gitPanelContent.addView(btn);
        }
    }

    private void renderCustomPanel(final SidebarPanelSpec spec) {
        final ImageButton icon = new ImageButton(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        icon.setBackgroundResource(android.R.attr.selectableItemBackgroundBorderless);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageResource(iconResFor(spec.iconName));
        icon.setImageTintList(ColorStateList.valueOf(ICON_OFF));
        iconBarExt.addView(icon);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF252526);
        panel.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        panel.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(0xFF2D2D2D);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        header.setPadding(dp(12), 0, dp(4), 0);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(spec.title);
        title.setTextColor(0xFFCCCCCC);
        title.setTextSize(12);
        header.addView(title);
        panel.addView(header);

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(6), 0, dp(6));
        if (spec.items != null) {
            for (SidebarPanelItem item : spec.items) {
                MaterialButton btn = makePanelButton(item.label);
                btn.setOnClickListener(v -> safeRun(item.onClick));
                content.addView(btn);
            }
        }
        sv.addView(content);
        panel.addView(sv);

        panelExtContainer.addView(panel);

        final String panelKey = "ext:" + spec.id;
        icon.setOnClickListener(v -> {
            if (panelKey.equals(activePanel)) {
                activePanel = null;
                panelContainer.setVisibility(View.GONE);
                panelDivider.setVisibility(View.GONE);
            } else {
                activePanel = panelKey;
                panelContainer.setVisibility(View.VISIBLE);
                panelDivider.setVisibility(View.VISIBLE);
                panelFiles.setVisibility(View.GONE);
                panelSearch.setVisibility(View.GONE);
                panelGit.setVisibility(View.GONE);
                panelExtensions.setVisibility(View.GONE);
                panelExtContainer.setVisibility(View.VISIBLE);
                for (int i = 0; i < panelExtContainer.getChildCount(); i++) {
                    View c = panelExtContainer.getChildAt(i);
                    c.setVisibility(c == panel ? View.VISIBLE : View.GONE);
                }
                if (spec.onOpen != null) safeRun(spec.onOpen);
            }
            updateIconTint();
        });
    }

    private MaterialButton makePanelButton(String label) {
        MaterialButton btn = new MaterialButton(this, null,
                com.google.android.material.R.attr.borderlessButtonStyle);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        btn.setTextSize(12);
        btn.setTextColor(0xFFDDDDDD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(8));
        lp.setMarginEnd(dp(8));
        lp.topMargin = dp(4);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void safeRun(Runnable r) {
        if (r == null) return;
        try { r.run(); }
        catch (Throwable t) { toast("扩展出错: " + t.getMessage()); }
    }

    private int iconResFor(String name) {
        if ("files".equals(name)) return R.drawable.ic_side_files;
        if ("search".equals(name)) return R.drawable.ic_side_search;
        if ("git".equals(name)) return R.drawable.ic_side_git;
        if ("extensions".equals(name)) return R.drawable.ic_side_extensions;
        if ("terminal".equals(name)) return R.drawable.ic_side_terminal;
        return R.drawable.ic_side_extensions;
    }

    // ============== 搜索 ==============

    private void setupSearch() {
        listSearchResult.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new SearchAdapter(searchResults,
                projectRoot.getAbsolutePath(),
                r -> onSearchResultClick(r));
        listSearchResult.setAdapter(searchAdapter);

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            startSearch(etSearch.getText().toString());
            return true;
        });

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            private long last = 0;
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                long now = System.currentTimeMillis();
                last = now;
                tvSearchStatus.postDelayed(() -> {
                    if (last != now) return;
                    startSearch(etSearch.getText().toString());
                }, 500);
            }
        });

        btnSearchClear.setOnClickListener(v -> {
            etSearch.setText("");
            searchResults.clear();
            searchAdapter.notifyDataSetChanged();
            tvSearchStatus.setText("");
        });
    }

    private void startSearch(String query) {
        if (searching) return;
        if (query == null || query.trim().isEmpty()) {
            searchResults.clear();
            searchAdapter.notifyDataSetChanged();
            tvSearchStatus.setText("");
            return;
        }
        final String q = query.trim();

        if ("*".equals(q)) {
            searching = true;
            tvSearchStatus.setText("诊断：列出所有文件…");
            new Thread(() -> {
                List<SearchResult> results = new ArrayList<>();
                listAllFiles(projectRoot, results);
                final int count = results.size();
                runOnUiThread(() -> {
                    searching = false;
                    searchResults.clear();
                    searchResults.addAll(results);
                    searchAdapter.notifyDataSetChanged();
                    tvSearchStatus.setText("共 " + count + " 个文件（根: "
                            + projectRoot.getAbsolutePath() + "）");
                });
            }, "diag").start();
            return;
        }

        searching = true;
        tvSearchStatus.setText(getString(R.string.ide_search_searching, q));

        new Thread(() -> {
            List<SearchResult> results = new ArrayList<>();
            scanDir(projectRoot, q, results);
            final int count = results.size();
            runOnUiThread(() -> {
                searching = false;
                searchResults.clear();
                searchResults.addAll(results);
                searchAdapter.notifyDataSetChanged();
                if (count == 0) {
                    tvSearchStatus.setText(getString(R.string.ide_search_none, q));
                } else if (count >= 500) {
                    tvSearchStatus.setText(getString(R.string.ide_search_capped, 500));
                } else {
                    tvSearchStatus.setText(getString(R.string.ide_search_count, count));
                }
            });
        }, "search").start();
    }

    private void listAllFiles(File dir, List<SearchResult> out) {
        if (dir == null || !dir.exists()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                String n = f.getName();
                if (n.startsWith(".") || n.equals("build")
                        || n.equals("output") || n.equals("node_modules")) {
                    continue;
                }
                listAllFiles(f, out);
            } else {
                SearchResult r = new SearchResult();
                r.file = f;
                r.line = 0;
                r.lineText = f.getName();
                r.matchStart = -1;
                r.matchEnd = -1;
                out.add(r);
                if (out.size() >= 2000) return;
            }
        }
    }

    private void scanDir(File dir, String query, List<SearchResult> out) {
        if (out.size() >= 500) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (out.size() >= 500) return;
            if (f.isDirectory()) {
                String n = f.getName();
                if (n.startsWith(".") || n.equals("build")
                        || n.equals("output") || n.equals("node_modules")) {
                    continue;
                }
                scanDir(f, query, out);
            } else {
                String name = f.getName().toLowerCase();
                String q = query.toLowerCase();

                int nameIdx = name.indexOf(q);
                if (nameIdx >= 0) {
                    SearchResult r = new SearchResult();
                    r.file = f;
                    r.line = 0;
                    r.lineText = f.getName();
                    r.matchStart = nameIdx;
                    r.matchEnd = nameIdx + query.length();
                    out.add(r);
                    if (out.size() >= 500) return;
                }

                if (!isTextFile(name)) continue;
                if (f.length() > 500 * 1024) continue;
                scanFile(f, query, out);
            }
        }
    }

    private void scanFile(File f, String query, List<SearchResult> out) {
        String q = query.toLowerCase();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String line;
            int num = 0;
            while ((line = br.readLine()) != null) {
                num++;
                String lower = line.toLowerCase();
                int idx = lower.indexOf(q);
                if (idx >= 0) {
                    SearchResult r = new SearchResult();
                    r.file = f;
                    r.line = num;
                    r.matchStart = idx;
                    r.matchEnd = idx + query.length();

                    int ctxBefore = 20;
                    int ctxAfter = 120;
                    int start = Math.max(0, idx - ctxBefore);
                    int end = Math.min(line.length(), idx + query.length() + ctxAfter);
                    String snippet = line.substring(start, end);
                    r.matchStart = idx - start;
                    r.matchEnd = r.matchStart + query.length();
                    if (start > 0) {
                        snippet = "…" + snippet;
                        r.matchStart += 1;
                        r.matchEnd += 1;
                    }
                    if (end < line.length()) snippet = snippet + "…";
                    r.lineText = snippet;

                    out.add(r);
                    if (out.size() >= 500) return;
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean isTextFile(String name) {
        String[] exts = {".java", ".kt", ".xml", ".html", ".htm", ".js", ".css",
                ".json", ".txt", ".md", ".gradle", ".kts", ".properties",
                ".c", ".h", ".cpp", ".hpp", ".py", ".sh", ".yml", ".yaml",
                ".toml", ".ini", ".cfg"};
        for (String e : exts) if (name.endsWith(e)) return true;
        return false;
    }

    private void onSearchResultClick(SearchResult r) {
        openFileAtLine(r.file.getAbsolutePath(), r.line <= 0 ? 1 : r.line);
    }

    private void openFileAtLine(String path, int line) {
        File f = new File(path);
        if (!f.exists()) {
            toast("文件不存在: " + path);
            return;
        }

        for (EditorTab t : tabs) {
            if (t.path.equals(path)) {
                selectTab(t);
                postGotoLine(line);
                return;
            }
        }

        EditorTab tab = new EditorTab(path, f.getName());
        tabs.add(tab);
        currentTab = tab;
        refreshTabBar();
        showEditor();

        try {
            String content = readText(f);
            String mode = modeForFile(f.getName());
            String js = "window.openFile("
                    + escapeJsString(path) + ","
                    + escapeJsString(content) + ","
                    + escapeJsString(mode) + ");";
            webEditor.evaluateJavascript(js, null);
            postGotoLine(line);
        } catch (Exception e) {
            toast("打开失败: " + e.getMessage());
        }
    }

    private void postGotoLine(final int line) {
        webEditor.postDelayed(() -> {
            String js = "window.gotoLine(" + line + ");";
            webEditor.evaluateJavascript(js, null);
        }, 250);
    }

    private void setupTerminal() {
        terminalHeader.setOnClickListener(v -> toggleTerminal());
        btnTerminalClear.setOnClickListener(v -> tvTerminal.setText(""));
        btnTerminalCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(
                        "terminal", tvTerminal.getText().toString()));
                Toast.makeText(this, getString(R.string.ide_copied),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupWebEditor() {
        WebSettings ws = webEditor.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        webEditor.setFocusable(true);
        webEditor.setFocusableInTouchMode(true);
        webEditor.setWebViewClient(new WebViewClient());
        webEditor.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webEditor.loadUrl("file:///android_asset/editor/editor_multi.html");
        webEditor.requestFocus();
    }

    private void setupFileTree() {
        fileTree.setLayoutManager(new LinearLayoutManager(this));
        DefaultItemAnimator anim = new DefaultItemAnimator();
        anim.setAddDuration(180);
        anim.setRemoveDuration(180);
        fileTree.setItemAnimator(anim);

        buildTree(projectRoot);
        refreshVisible();
        adapter = new FileTreeAdapter(visibleNodes, new FileTreeAdapter.OnNodeClick() {
            @Override
            public void onClick(FileNode node) {
                if (node.isDir) {
                    int pos = visibleNodes.indexOf(node);
                    node.expanded = !node.expanded;
                    refreshVisible();
                    if (pos >= 0 && pos < visibleNodes.size()
                            && visibleNodes.get(pos) == node) {
                        adapter.notifyItemChanged(pos);
                    }
                } else {
                    onFileClick(node);
                }
            }

            @Override
            public void onLongClick(FileNode node) {
                showNodeMenu(node);
            }
        });
        fileTree.setAdapter(adapter);

        terminalLog("APK Builder IDE ready.");
        terminalLog("Project: " + projectName);
    }

    // ============== 文件树 ==============

    private void buildTree(File root) {
        allNodes.clear();
        File[] kids = root.listFiles();
        if (kids == null) return;
        sortEntries(kids);
        for (File k : kids) addNode(k, 0, null);
    }

    private void addNode(File f, int depth, FileNode parent) {
        FileNode n = new FileNode();
        n.name = f.getName();
        n.file = f;
        n.isDir = f.isDirectory();
        n.depth = depth;
        n.expanded = false;
        n.parent = parent;
        allNodes.add(n);

        if (n.isDir) {
            File[] kids = f.listFiles();
            if (kids != null) {
                sortEntries(kids);
                for (File k : kids) addNode(k, depth + 1, n);
            }
        }
    }

    private static void sortEntries(File[] arr) {
        Arrays.sort(arr, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
    }

    private void rebuildTree() {
        Set<String> expandedPaths = new HashSet<>();
        for (FileNode n : allNodes) {
            if (n.isDir && n.expanded) expandedPaths.add(n.file.getAbsolutePath());
        }

        allNodes.clear();
        File[] kids = projectRoot.listFiles();
        if (kids != null) {
            sortEntries(kids);
            for (File k : kids) addNode(k, 0, null);
        }

        for (FileNode n : allNodes) {
            if (n.isDir && expandedPaths.contains(n.file.getAbsolutePath())) {
                n.expanded = true;
            }
        }

        visibleNodes.clear();
        for (FileNode n : allNodes) {
            boolean visible = true;
            FileNode p = n.parent;
            while (p != null) {
                if (!p.expanded) { visible = false; break; }
                p = p.parent;
            }
            if (visible) visibleNodes.add(n);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void refreshVisible() {
        List<FileNode> newVisible = new ArrayList<>();
        for (FileNode n : allNodes) {
            boolean visible = true;
            FileNode p = n.parent;
            while (p != null) {
                if (!p.expanded) { visible = false; break; }
                p = p.parent;
            }
            if (visible) newVisible.add(n);
        }

        if (adapter == null) {
            visibleNodes.clear();
            visibleNodes.addAll(newVisible);
            return;
        }

        int oldSize = visibleNodes.size();
        int newSize = newVisible.size();

        int firstDiff = 0;
        while (firstDiff < oldSize && firstDiff < newSize
                && visibleNodes.get(firstDiff) == newVisible.get(firstDiff)) {
            firstDiff++;
        }
        if (firstDiff == oldSize && firstDiff == newSize) return;

        int tailOld = oldSize - 1;
        int tailNew = newSize - 1;
        while (tailOld >= firstDiff && tailNew >= firstDiff
                && visibleNodes.get(tailOld) == newVisible.get(tailNew)) {
            tailOld--;
            tailNew--;
        }

        int removeCount = tailOld - firstDiff + 1;
        int insertCount = tailNew - firstDiff + 1;

        visibleNodes.clear();
        visibleNodes.addAll(newVisible);

        if (removeCount > 0) adapter.notifyItemRangeRemoved(firstDiff, removeCount);
        if (insertCount > 0) adapter.notifyItemRangeInserted(firstDiff, insertCount);
    }

    // ============== 长按菜单 ==============

    private void showNodeMenu(final FileNode node) {
        final List<String> items = new ArrayList<>();
        if (node.isDir) {
            items.add(getString(R.string.ide_new_file_here));
            items.add(getString(R.string.ide_new_folder_here));
        }
        items.add(getString(R.string.ide_rename));
        items.add(getString(R.string.ide_delete));
        items.add(getString(R.string.ide_copy_path));

        new AlertDialog.Builder(this)
                .setTitle(node.name)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String action = items.get(which);
                    if (action.equals(getString(R.string.ide_new_file_here))) {
                        promptCreate(node.file, false);
                    } else if (action.equals(getString(R.string.ide_new_folder_here))) {
                        promptCreate(node.file, true);
                    } else if (action.equals(getString(R.string.ide_rename))) {
                        promptRename(node);
                    } else if (action.equals(getString(R.string.ide_delete))) {
                        confirmDelete(node);
                    } else if (action.equals(getString(R.string.ide_copy_path))) {
                        ClipboardManager cm = (ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText(
                                    "path", node.file.getAbsolutePath()));
                            toast(getString(R.string.ide_path_copied));
                        }
                    }
                })
                .show();
    }

    private void promptCreate(final File parentDir, final boolean isFolder) {
        final EditText et = new EditText(this);
        et.setHint(isFolder ? getString(R.string.ide_foldername_hint)
                : getString(R.string.ide_filename_hint));
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setSingleLine(true);

        String title = (isFolder ? getString(R.string.ide_new_folder)
                : getString(R.string.ide_new_file))
                + " · " + parentDir.getName();

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton(R.string.ide_btn_create, (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast(getString(R.string.ide_name_empty));
                        return;
                    }
                    if (name.contains("/") || name.contains("\\")
                            || name.contains(":") || name.contains("*")
                            || name.contains("?") || name.contains("\"")
                            || name.contains("<") || name.contains(">")
                            || name.contains("|")) {
                        toast(getString(R.string.ide_name_invalid));
                        return;
                    }
                    File target = new File(parentDir, name);
                    if (target.exists()) {
                        toast(getString(R.string.ide_exists, name));
                        return;
                    }
                    try {
                        if (isFolder) {
                            if (!target.mkdirs())
                                throw new Exception(getString(R.string.ide_create_failed));
                        } else {
                            File p = target.getParentFile();
                            if (p != null) p.mkdirs();
                            if (!target.createNewFile())
                                throw new Exception(getString(R.string.ide_create_failed));
                        }
                        terminalLog("New: " + target.getAbsolutePath());
                        for (FileNode n : allNodes) {
                            if (n.isDir && n.file.equals(parentDir)) {
                                n.expanded = true;
                            }
                        }
                        rebuildTree();
                    } catch (Exception e) {
                        toast(e.getMessage());
                    }
                })
                .setNegativeButton(R.string.ide_btn_cancel, null)
                .show();
    }

    private void promptRename(final FileNode node) {
        final EditText et = new EditText(this);
        et.setText(node.name);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setSingleLine(true);
        et.setSelection(node.name.length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.ide_rename)
                .setView(et)
                .setPositiveButton(R.string.ide_btn_confirm, (d, w) -> {
                    String newName = et.getText().toString().trim();
                    if (newName.isEmpty() || newName.equals(node.name)) return;
                    if (newName.contains("/") || newName.contains("\\")) {
                        toast(getString(R.string.ide_name_invalid));
                        return;
                    }
                    File parent = node.file.getParentFile();
                    if (parent == null) return;
                    File target = new File(parent, newName);
                    if (target.exists()) {
                        toast(getString(R.string.ide_exists, newName));
                        return;
                    }
                    if (node.file.renameTo(target)) {
                        terminalLog("Rename: " + node.file.getName() + " -> " + newName);
                        closeTabByPath(node.file.getAbsolutePath());
                        rebuildTree();
                    } else {
                        toast(getString(R.string.ide_rename_failed));
                    }
                })
                .setNegativeButton(R.string.ide_btn_cancel, null)
                .show();
    }

    private void confirmDelete(final FileNode node) {
        String msg = node.isDir
                ? getString(R.string.ide_delete_folder_msg, node.name)
                : getString(R.string.ide_delete_file_msg, node.name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.ide_delete_title)
                .setMessage(msg)
                .setPositiveButton(R.string.ide_btn_delete, (d, w) -> {
                    String path = node.file.getAbsolutePath();
                    deleteRec(node.file);
                    terminalLog("Delete: " + path);
                    closeTabByPath(path);
                    rebuildTree();
                })
                .setNegativeButton(R.string.ide_btn_cancel, null)
                .show();
    }

    private void closeTabByPath(String path) {
        for (int i = tabs.size() - 1; i >= 0; i--) {
            EditorTab t = tabs.get(i);
            if (t.path.equals(path) || t.path.startsWith(path + "/")) {
                closeTab(t);
            }
        }
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }

    // ============== 编辑器 tab ==============

    private void onFileClick(FileNode node) {
        String path = node.file.getAbsolutePath();
        for (EditorTab t : tabs) {
            if (t.path.equals(path)) {
                selectTab(t);
                return;
            }
        }
        EditorTab tab = new EditorTab(path, node.name);
        tabs.add(tab);
        currentTab = tab;
        refreshTabBar();
        showEditor();

        try {
            String content = readText(node.file);
            String mode = modeForFile(node.name);
            String js = "window.openFile("
                    + escapeJsString(path) + ","
                    + escapeJsString(content) + ","
                    + escapeJsString(mode) + ");";
            webEditor.evaluateJavascript(js, null);
            terminalLog("Open: " + path);
        } catch (Exception e) {
            toast(getString(R.string.ide_open_failed, e.getMessage()));
        }
    }

    private void selectTab(EditorTab tab) {
        currentTab = tab;
        refreshTabBar();
        webEditor.evaluateJavascript(
                "window.switchTo(" + escapeJsString(tab.path) + ");", null);
    }

    private void closeTab(EditorTab tab) {
        webEditor.evaluateJavascript(
                "window.closeFile(" + escapeJsString(tab.path) + ");", null);

        int idx = tabs.indexOf(tab);
        tabs.remove(tab);

        if (currentTab == tab) {
            if (tabs.isEmpty()) {
                currentTab = null;
                hideEditor();
            } else {
                int next = Math.min(idx, tabs.size() - 1);
                currentTab = tabs.get(next);
                webEditor.evaluateJavascript(
                        "window.switchTo(" + escapeJsString(currentTab.path) + ");", null);
            }
        }
        refreshTabBar();
    }

    private void refreshTabBar() {
        tabBar.removeAllViews();
        for (EditorTab tab : tabs) {
            View v = getLayoutInflater().inflate(
                    R.layout.item_editor_tab, tabBar, false);
            ImageView ivIcon = v.findViewById(R.id.ivTabIcon);
            TextView tvName = v.findViewById(R.id.tvTabName);
            ImageButton btnClose = v.findViewById(R.id.btnTabClose);

            ivIcon.setImageResource(iconForName(tab.name));
            tvName.setText(tab.dirty ? (tab.name + " •") : tab.name);

            if (tab == currentTab) {
                v.setBackgroundColor(TAB_ACTIVE_BG);
                tvName.setTextColor(0xFFFFFFFF);
            } else {
                v.setBackgroundColor(TAB_INACTIVE_BG);
                tvName.setTextColor(0xFFAAAAAA);
            }

            final EditorTab t = tab;
            v.setOnClickListener(vv -> selectTab(t));
            btnClose.setOnClickListener(vv -> closeTab(t));

            tabBar.addView(v);
        }
    }

    private void showEditor() {
        tvEditorPlaceholder.setVisibility(View.GONE);
        webEditor.setVisibility(View.VISIBLE);
    }

    private void hideEditor() {
        tvEditorPlaceholder.setVisibility(View.VISIBLE);
        webEditor.setVisibility(View.INVISIBLE);
    }

    private void saveCurrentFile() {
        if (currentTab == null) {
            toast(getString(R.string.ide_no_open_file));
            return;
        }
        final EditorTab tab = currentTab;
        webEditor.evaluateJavascript(
                "window.getContent(" + escapeJsString(tab.path) + ");",
                value -> {
                    String content = unescapeJs(value);
                    try {
                        writeText(new File(tab.path), content);
                        tab.dirty = false;
                        refreshTabBar();
                        toast(getString(R.string.ide_saved, tab.name));
                        terminalLog("Save: " + tab.path);
                    } catch (Exception e) {
                        toast(getString(R.string.ide_save_failed, e.getMessage()));
                    }
                });
    }

    private void saveAllTabs(final Runnable onDone) {
        final List<EditorTab> dirty = new ArrayList<>();
        for (EditorTab t : tabs) if (t.dirty) dirty.add(t);
        if (dirty.isEmpty()) {
            onDone.run();
            return;
        }
        final int[] remaining = {dirty.size()};
        for (EditorTab t : dirty) {
            final String path = t.path;
            final String name = t.name;
            final EditorTab tabRef = t;
            webEditor.evaluateJavascript(
                    "window.getContent(" + escapeJsString(path) + ");",
                    value -> {
                        String content = unescapeJs(value);
                        try {
                            writeText(new File(path), content);
                            tabRef.dirty = false;
                            terminalLog("Auto-save: " + path);
                        } catch (Exception e) {
                            toast(getString(R.string.ide_save_failed, name));
                        }
                        if (--remaining[0] == 0) {
                            runOnUiThread(() -> {
                                refreshTabBar();
                                onDone.run();
                            });
                        }
                    });
        }
    }

    // ============== 构建 ==============

    private void onBuildClicked() {
        if (building) {
            toast(getString(R.string.ide_building));
            return;
        }
        expandTerminal();
        saveAllTabs(this::startBuild);
    }

    private void startBuild() {
        if (building) return;
        building = true;
        setBuildButtonEnabled(false);
        tvTerminal.setText("");
        terminalLog("==== Build " + projectName + " ====");

        ProjectBuilder pb = new ProjectBuilder(this,
                msg -> runOnUiThread(() -> terminalLog(msg)));
        pb.build(projectName, new ProjectBuilder.Done() {
            @Override
            public void onSuccess(File apk) {
                building = false;
                setBuildButtonEnabled(true);
                terminalLog("OK " + apk.getAbsolutePath());
                showBuildResultDialog(apk);
            }

            @Override
            public void onError(String message, Exception e) {
                building = false;
                setBuildButtonEnabled(true);
                terminalLog("FAIL " + message);
                toast(getString(R.string.ide_build_failed));
            }
        });
    }

    private void setBuildButtonEnabled(boolean enabled) {
        btnBuild.setEnabled(enabled);
        btnBuild.setAlpha(enabled ? 1f : 0.4f);
    }

    private void showBuildResultDialog(File apk) {
        String path = apk.getAbsolutePath();
        String size = humanSize(apk.length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.ide_build_success_title)
                .setMessage(getString(R.string.ide_build_success_msg, path, size))
                .setPositiveButton(R.string.ide_btn_install,
                        (d, w) -> installApk(apk))
                .setNeutralButton(R.string.ide_btn_close, null)
                .show();
    }

    private void installApk(File apk) {
        try {
            File priv = new File(getExternalFilesDir(null), "apk");
            if (!priv.exists()) priv.mkdirs();
            File privApk = new File(priv, apk.getName());
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = new FileOutputStream(privApk)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }

            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", privApk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            toast(getString(R.string.ide_install_failed, e.getMessage()));
            terminalLog("[ERR] install: " + e.getMessage());
        }
    }

    private static String humanSize(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.US, "%.1f KB", n / 1024.0);
        return String.format(Locale.US, "%.2f MB", n / 1024.0 / 1024.0);
    }

    // ============== 工具 ==============

    private static String modeForFile(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".java")) return "text/x-java";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".xml")) return "xml";
        if (n.endsWith(".html") || n.endsWith(".htm")) return "htmlmixed";
        if (n.endsWith(".js")) return "javascript";
        if (n.endsWith(".css")) return "css";
        return "text/plain";
    }

    private static int iconForName(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".java")) return R.drawable.ic_file_java;
        if (n.endsWith(".json")) return R.drawable.ic_file_json;
        if (n.endsWith(".xml")) return R.drawable.ic_file_xml;
        if (n.endsWith(".html") || n.endsWith(".htm")) return R.drawable.ic_file_html;
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".webp")
                || n.endsWith(".bmp")) return R.drawable.ic_file_image;
        return R.drawable.ic_file_generic;
    }

    private static String readText(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            return new String(buf, 0, off, "UTF-8");
        }
    }

    private static void writeText(File f, String content) throws Exception {
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(content.getBytes("UTF-8"));
        }
    }

    private static String escapeJsString(String s) {
        if (s == null) return "''";
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("'");
        return sb.toString();
    }

    private static String unescapeJs(String s) {
        if (s == null || s.equals("null")) return "";
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case 'n': sb.append('\n'); i += 2; break;
                    case 'r': sb.append('\r'); i += 2; break;
                    case 't': sb.append('\t'); i += 2; break;
                    case 'b': sb.append('\b'); i += 2; break;
                    case 'f': sb.append('\f'); i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    case '"': sb.append('"'); i += 2; break;
                    case '\'': sb.append('\''); i += 2; break;
                    case '/': sb.append('/'); i += 2; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                int code = Integer.parseInt(
                                        s.substring(i + 2, i + 6), 16);
                                sb.append((char) code);
                                i += 6;
                            } catch (NumberFormatException e) {
                                sb.append(n); i += 2;
                            }
                        } else {
                            sb.append(n); i += 2;
                        }
                        break;
                    default:
                        sb.append(n); i += 2;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private void toggleTerminal() {
        int targetDp = terminalExpanded ? TERMINAL_COLLAPSED_DP : TERMINAL_EXPANDED_DP;
        ViewGroup.LayoutParams lp = terminalPanel.getLayoutParams();
        lp.height = dp(targetDp);
        terminalPanel.setLayoutParams(lp);
        terminalExpanded = !terminalExpanded;
        tvTerminalArrow.setText(terminalExpanded ? "▼" : "▶");
        terminalScroll.setVisibility(terminalExpanded ? View.VISIBLE : View.GONE);
    }

    private void expandTerminal() {
        if (!terminalExpanded) toggleTerminal();
    }

    private void terminalLog(String s) {
        tvTerminal.append(s + "\n");
        terminalScroll.post(() -> terminalScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ============== 菜单 ==============

    private void showMenu() {
        final List<String> items = new ArrayList<>();
        items.add(getString(R.string.ide_menu_settings));
        items.add(getString(R.string.ide_menu_signing));
        items.add(getString(R.string.ide_menu_toolchain));
        items.add(getString(R.string.ide_menu_language));
        items.add(getString(R.string.ide_menu_copy_log));
        items.add(getString(R.string.ide_menu_clear_log));
        items.add(getString(R.string.ide_menu_extensions));

        final int BASE_COUNT = 7;
        for (ExtensionManager.MenuItem m : extMenuItems) {
            items.add("⤷ " + m.title);
        }

        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(this, MainActivity.class);
                        i.putExtra("project", projectName);
                        startActivity(i);
                    } else if (which == 1) {
                        Intent i = new Intent(this, SigningActivity.class);
                        i.putExtra("project", projectName);
                        startActivity(i);
                    } else if (which == 2) {
                        expandTerminal();
                        testToolchain();
                    } else if (which == 3) {
                        LocaleHelper.toggle(this);
                        recreate();
                    } else if (which == 4) {
                        ClipboardManager cm = (ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText(
                                    "terminal", tvTerminal.getText().toString()));
                            toast(getString(R.string.ide_copied));
                        }
                    } else if (which == 5) {
                        tvTerminal.setText("");
                    } else if (which == 6) {
                        startActivity(new Intent(this, ExtensionActivity.class));
                    } else if (which >= BASE_COUNT) {
                        int extIdx = which - BASE_COUNT;
                        if (extIdx < extMenuItems.size()) {
                            ExtensionManager.MenuItem m = extMenuItems.get(extIdx);
                            safeRun(m.action);
                        }
                    }
                })
                .show();
    }

    private void testToolchain() {
        new Thread(() -> {
            ToolchainManager tm = new ToolchainManager(IdeActivity.this);

            logAsync("========== aapt2 ==========");
            File aapt2 = tm.getTool("aapt2");
            if (aapt2 == null) logAsync("[FAIL] libaapt2.so not found");
            else {
                logAsync("path=" + aapt2 + " canExec=" + aapt2.canExecute());
                logAsync(tm.exec(Arrays.asList(aapt2.getAbsolutePath(), "version")));
            }

            logAsync("========== zipalign ==========");
            File za = tm.getTool("zipalign");
            if (za == null) logAsync("[FAIL] libzipalign.so not found");
            else {
                logAsync("path=" + za + " canExec=" + za.canExecute());
                logAsync(tm.exec(Arrays.asList(za.getAbsolutePath())));
            }
        }).start();
    }

    private void logAsync(String s) {
        runOnUiThread(() -> terminalLog(s));
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ExtensionManager mgr = ExtensionManager.get(this);
        mgr.setActivity(this);

        int gen = mgr.getGeneration();
        if (gen != lastExtGeneration) {
            lastExtGeneration = gen;
            extMenuItems.clear();
            extMenuItems.addAll(mgr.getMenuItems());
            extSidebarPanels.clear();
            extSidebarPanels.addAll(mgr.getSidebarPanels());
            refreshExtPanel();
            renderExtensionPanels();
            terminalLog("扩展已刷新：菜单 " + extMenuItems.size()
                    + " 项，侧边栏面板 " + extSidebarPanels.size() + " 个");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ExtensionManager.get(this).setActivity(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ExtensionManager.get(this).setCurrentProject(null);
    }

    public class Bridge {
        @JavascriptInterface
        public void onReady() {
            runOnUiThread(() -> {});
        }

        @JavascriptInterface
        public void onDocChanged(String path) {
            runOnUiThread(() -> {
                for (EditorTab t : tabs) {
                    if (t.path.equals(path) && !t.dirty) {
                        t.dirty = true;
                        refreshTabBar();
                        return;
                    }
                }
            });
        }
    }
}