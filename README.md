# APK Builder (v2)

> 在 Android 手机上离线打包 APK 的 IDE。
> An offline APK builder IDE that runs entirely on Android.

[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Branch](https://img.shields.io/badge/branch-v2-orange)]()

---

## ⚠️ 关于分支 / About branches

本仓库有两个分支：

| 分支 | 内容 | 包名 |
|---|---|---|
| `main` | v1 旧项目（保留） | `com.example.apkbuilder` |
| **`v2`** | **当前开发版（推荐）** | `com.example.androidstudio` |

本 README 描述的是 **v2**。旧版请切到 `main` 分支。

---

## ✨ 功能 / Features

### 构建能力
- **HTML → APK**：单文件 HTML 打成 WebView 壳
- **Java → APK**：ECJ 编译 + D8 转 dex + aapt2 打包 + 签名
- **模板系统**：Empty / Empty View（对标 Android Studio 模板）
- **完整 res/ 支持**：用户项目里的 `res/`、`AndroidManifest.xml` 真实生效
- **Release 签名**：导入 JKS / PKCS12 keystore 签 release 包
- **全离线**：aapt2 + ECJ + D8 + apksig 全部内置，无需联网

### IDE 界面（v2 新增）
- VSCode 风格侧边栏：📁 文件 | 🔍 搜索 | ⎇ Git | 🧩 扩展
- 侧边栏可拖拽宽度、可整体收起
- 多 tab 编辑器（CodeMirror，支持语法高亮、行号）
- 内嵌终端，构建日志实时滚动
- 项目全文本搜索（文件名 + 内容）
- 错误行号点击跳转

### 扩展系统（v2 新增）
- 扩展 = 独立 zip 包（`classes.dex` + `manifest.json`）
- 运行时加载，无需重新编译主 App
- 扩展可以：
  - 注册 ⋯ 菜单项
  - 注册侧边栏面板（按钮列表 或 自定义 View）
  - 使用自带的图标 PNG
  - 携带第三方 jar（自动转成 `lib/*.dex`）
- 内置 **Git 扩展**（基于 JGit 5.13）：init / status / add / commit / log / branches

### 中英双语
- 全部界面中英切换
- 深色主题

---

## 📸 截图 / Screenshots

> 待补充

---

## 📦 下载 / Download

从 [Releases](https://github.com/alice-bluearchive/apk-builder-android/releases) 下载最新的 `app-debug.apk`（约 42 MB）。

**首次启动**需要授予「所有文件访问」权限，用于读写 `/storage/emulated/0/apk-project/` 下的项目目录。

---

## 🛠 从源码构建 / Build from source

### 环境要求
- **JDK 17 或 21**（JDK 22+ 不兼容 AGP 8.x）
- **Android SDK**（`compileSdk 36`）
- **Gradle 8.14+**（用 wrapper 自带的即可）

### 步骤

```bash
git clone -b v2 https://github.com/alice-bluearchive/apk-builder-android.git
cd apk-builder-android

# 在 local.properties 里指定你的 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 构建
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 项目结构

```
app/src/main/
├── java/com/example/androidstudio/
│   ├── ApkBuilder.java              核心打包流程
│   ├── ProjectBuilder.java          构建调度器
│   ├── EcjCompiler.java             ECJ 动态加载 + 编译
│   ├── D8Wrapper.java               class → dex
│   ├── IdeActivity.java             IDE 主界面
│   ├── ExtensionManager.java        扩展加载 / 卸载
│   ├── ExtensionHost.java           扩展 API
│   └── ...
├── assets/
│   ├── template/                    工具链（aapt2 等，由 jniLibs 提供）
│   ├── templates/                   新建项目模板
│   └── editor/                      CodeMirror
├── jniLibs/arm64-v8a/               53 个 .so（aapt2 + 依赖）
└── res/                             主 App 资源
```

---

## 🧩 开发扩展 / Writing extensions

扩展是一个 zip 包，包含：

```
myext.zip
├── manifest.json        扩展元信息
├── classes.dex          编译后的 Java 代码
├── icon.png             （可选）侧边栏图标
└── lib/                 （可选）第三方 jar 转的 dex
    └── classes.dex
```

### manifest.json

```json
{
  "id": "myext",
  "name": "My Extension",
  "version": "1.0.0",
  "entryClass": "com.example.myext.MyExtension",
  "description": "What it does",
  "author": "Your name"
}
```

### 入口类

```java
package com.example.myext;

import android.widget.Toast;
import com.example.androidstudio.ExtensionHost;
import com.example.androidstudio.IExtension;
import com.example.androidstudio.SidebarPanelItem;
import com.example.androidstudio.SidebarPanelSpec;
import java.util.ArrayList;

public class MyExtension implements IExtension {
    private ExtensionHost host;

    @Override public String getId()      { return "myext"; }
    @Override public String getName()    { return "My Extension"; }
    @Override public String getVersion() { return "1.0.0"; }

    @Override
    public void onLoad(ExtensionHost host) {
        this.host = host;
        host.log("My extension loaded");

        // 菜单项
        host.registerMenuItem("Say hello", this::hello);

        // 侧边栏面板
        SidebarPanelSpec panel = new SidebarPanelSpec();
        panel.id = "myext-panel";
        panel.title = "My Extension";
        panel.iconName = "extensions";   // 内置图标集
        panel.items = new ArrayList<>();
        panel.items.add(new SidebarPanelItem("Say hello", this::hello));
        host.registerSidebarPanel(panel);
    }

    @Override public void onUnload() {}

    private void hello() {
        Toast.makeText(host.getAppContext(), "Hello!",
                Toast.LENGTH_SHORT).show();
    }
}
```

### 内置图标集

`files` / `search` / `git` / `extensions` / `terminal` / `robot` / `database` / `cloud` / `settings`

### 自定义 View 面板

```java
panel.viewFactory = ctx -> {
    LinearLayout v = new LinearLayout(ctx);
    v.setOrientation(LinearLayout.VERTICAL);
    // 放任意 View
    return v;
};
```

### 安装

1. 把 zip 拷到手机 `/sdcard/Download/`
2. 主 App → 任意项目 → 🧩 → 管理扩展 → 导入 ZIP
3. 退出并重进项目以加载

详见 [ext-template](https://github.com/alice-bluearchive/apk-builder-android/tree/v2/ext-template)（示例扩展骨架）。

---

## 🧰 工具链 / Toolchain

内置（`assets/template/` + `jniLibs/`）：

| 组件 | 用途 |
|---|---|
| `aapt2` | 资源编译 + 打包 |
| `zipalign` | APK 对齐 |
| `android.jar` | 编译期 API 存根（压缩 24 MB） |
| `ecj.dex` + `ecj.jar` | Eclipse Java 编译器（DexClassLoader 加载） |
| `lambda-patch.jar` | Lambda 表达式支持 |
| `apksig` | V1 + V2 签名 |
| `D8 (R8 库)` | class → dex |

---

## 📄 License

[MIT](LICENSE)

---

## 🙏 致谢 / Credits

- [Eclipse JDT](https://www.eclipse.org/jdt/) — ECJ 编译器
- [JGit](https://www.eclipse.org/jgit/) — Git 实现
- [CodeMirror](https://codemirror.net/) — 编辑器内核
- [Termux](https://termux.dev/) — aapt2 二进制的来源
