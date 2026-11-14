

---

# APK Builder (v3)

> 在 Android 手机上离线打包 APK 的现代 IDE。
> An offline APK builder IDE that runs entirely on Android.

[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Branch](https://img.shields.io/badge/branch-v3-purple)]()

---

## ⚠️ 关于分支 / About branches

本仓库有多个分支：

| 分支 | 内容 | 包名 |
|---|---|---|
| `main` | v1 老项目（保留） | `com.example.apkbuilder` |
| `v2` | v2 稳定开发版 | `com.example.androidstudio` |
| **`v3`** | **当前开发版（推荐）** | `com.example.androidstudio` |

本 README 描述的是 **v3**。旧版请切到对应分支。

---

## ✨ 功能 / Features

### 🚀 全新 IDE 体验 (v3 核心)
- **VSCode 风格布局**：活动栏、可拖拽侧边栏、底部状态栏（显示光标行列、语言、错误数）。
- **沉浸式全屏编辑器**：独立 `EditorActivity`，彻底释放屏幕空间，专注敲代码。
- **现代命令面板**：点击顶部标题唤起 Command Palette，支持模糊搜索文件、执行构建/终端/设置等命令。
- **真·代码折叠**：支持 Java/JS/C++ 的 `{}` 折叠，一键收起长方法。
- **智能补全**：自动补全括号、引号，并关闭了恼人的智能缩进。
- **快捷工具栏**：悬浮竖排按钮（撤销/重做/注释/格式化），随手可用。

### 🛠 构建能力
- **HTML → APK**：单文件 HTML 打成 WebView 壳。
- **Java → APK**：ECJ 编译 + D8 转 dex + aapt2 打包 + 签名。
- **Gradle 规范**：新建项目自动生成 `src/main/java|res|assets` 标准结构。
- **完整 res/ 支持**：用户项目里的 `res/`、`AndroidManifest.xml` 真实生效。
- **Release 签名**：导入 JKS / PKCS12 keystore 签 release 包。
- **全离线**：aapt2 + ECJ + D8 + apksig 全部内置，无需联网。
- **进度条与报错解析**：终端内嵌 ASCII 进度条，构建失败自动提取问题列表，点击即可跳转报错行。

### 🌿 原生 Git 集成 (v3 新增)
- 基于 JGit 5.13，无需外挂扩展，直接在侧边栏进行版本控制。
- 支持 `Git Init`、`Status`、`Add`、`Commit`、`Log`。
- 初始化时自动生成 `.gitignore`，防止把编译产物提交到仓库。

### 🧩 扩展系统 & 本地商店
- 扩展 = 独立 zip 包（`classes.dex` + `manifest.json`）。
- **安全私有目录**：扩展解压到 `/data/user/0/.../files/extensions/`，防篡改。
- **本地商店**：读取 `/sdcard/apk-builder/store/` 下的 zip 包，一键安装/重装/卸载。
- **Dex 安全校验**：安装时校验 `classes.dex` 魔数，拦截恶意文件。

### 🌍 其它
- **全界面中英双语**：彻底清除硬编码，支持即时切换。
- **智能新建项目**：两次弹窗，支持中文应用名 + 自定义英文包名。

---

## 📸 截图 / Screenshots

看我干什么我没有

---

## 📥 下载 / Download

从 [Releases](https://github.com/alice-bluearchive/apk-builder-android/releases) 下载最新的 `app-debug.apk`。

**首次启动**需要授予「所有文件访问」权限，用于读写 `/storage/emulated/0/apk-project/` 下的项目目录。

---

## 🛠 从源码构建 / Build from source

### 环境要求
- **JDK 17 或 21**（JDK 22+ 不兼容 AGP 8.x）
- **Android SDK**（`compileSdk 34` 或以上）
- **Gradle 8.14+**（用 wrapper 自带的即可）

### 步骤

git clone -b v3 https://github.com/alice-bluearchive/apk-builder-android.git
cd apk-builder-android

# 在 local.properties 里指定你的 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 构建
./gradlew :app:assembleDebug

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 项目结构
app/src/main/
├── java/com/example/androidstudio/
│   ├── ApkBuilder.java              核心打包流程
│   ├── ProjectBuilder.java          构建调度器
│   ├── EcjCompiler.java             ECJ 动态加载 + 编译
│   ├── D8Wrapper.java               class → dex
│   ├── IdeActivity.java             主工作区（文件树/终端）
│   ├── EditorActivity.java          全屏沉浸式代码编辑器
│   ├── GitManager.java              原生 JGit 封装
│   ├── ExtensionManager.java        扩展加载 / 卸载
│   ├── ExtensionStoreActivity.java  本地扩展商店
│   └── ...
├── assets/
│   ├── template/                    工具链（aapt2 等，由 jniLibs 提供）
│   ├── templates/                   新建项目模板
│   └── editor/                      CodeMirror (含折叠/补全插件)
├── jniLibs/arm64-v8a/               53 个 .so（aapt2 + 依赖）
└── res/                             主 App 资源（含双语适配）
---


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

### 安装

1. 将 zip 包放到手机 `/sdcard/apk-builder/store/` 目录下。
2. 在 App 内进入 扩展 → 商店 → 点击 INSTALL。

详见 [ext-template](https://github.com/alice-bluearchive/apk-builder-android/tree/v3/ext-template)（示例扩展骨架）。

---

## 🎉 工具链 / Toolchain

内置（`assets/template/` + `jniLibs/`）：

| 组件 | 用途 |
|---|---|
| `aapt2` | 资源编译 + 打包 |
| `zipalign` | APK 对齐 |
| `android.jar` | 编译时 API 存根（压缩 24 MB） |
| `ecj.dex` + `ecj.jar` | Eclipse Java 编译器（DexClassLoader 加载） |
| `lambda-patch.jar` | Lambda 表达式支持 |
| `apksig` | V1 + V2 签名 |
| `D8 (R8 库)` | class → dex |
| `JGit` | 原生 Git 版本控制 |

---


