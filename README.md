# APK Builder for Android

[![Release](https://img.shields.io/github/v/release/alice-bluearchive/apk-builder-android)](https://github.com/alice-bluearchive/apk-builder-android/releases)
[![License](https://img.shields.io/github/license/alice-bluearchive/apk-builder-android)](https://github.com/alice-bluearchive/apk-builder-android/blob/main/LICENSE)

**📥 [下载最新 APK](https://github.com/alice-bluearchive/apk-builder-android/releases/latest)**

在 Android 设备上离线打包 APK 的工具。选 HTML 或 Java 文件，一键生成可安装的 APK，**全程不需要电脑**。

An offline APK builder for Android. Pick HTML or Java source, get a working APK — no PC required.

---

## ✨ 功能

- **HTML 模式** — 选一个 HTML 文件，打包成 WebView 应用
- **Java 模式** — 选一个或多个 `.java` 文件，编译后打包成 APK
- **支持 Java 8 特性** — lambda、Stream、方法引用等
- **内置代码编辑器** — CodeMirror，语法高亮、行号、自动缩进
- **完全离线** — 内置 aapt2 / ECJ / D8 / apksig，不需要联网
- **可定制** — 包名、应用名、图标（任意图片格式）、权限
- **项目管理** — 项目存在 `/storage/emulated/0/apk-project/`，随时切换
- **多语言** — 中文 / English

---

## 🛠 内置的工具链

| 工具 | 用途 | 来源 |
|---|---|---|
| aapt2 | 资源编译、链接 | Termux |
| zipalign | APK 对齐 | Termux |
| ECJ | Java 编译器 | Eclipse |
| LambdaMetafactory 补丁 | 支持 lambda | OpenJDK |
| D8 (R8) | `.class` → `.dex` | Google |
| apksig | APK 签名 | Google |

全部预置在 `app/src/main/assets/template/`，首次使用时释放到 App 私有目录。

---

## 📱 使用

### HTML 模式

1. 新建项目 → 选「HTML」
2. 填包名（如 `com.example.mywebapp`）、应用名
3. 点「选择源文件」，选一个 `.html`
4. 点「生成 APK」

### Java 模式

1. 新建项目 → 选「Java」
2. 填包名（如 `com.example.lens`）
3. **可以直接点生成**用项目自带模板；或**点「选择源文件」**导入你的 `.java`
4. 用「编辑源文件」修改 `src/` 里的文件
5. 点「生成 APK」

**Java 模式的最小示例**：

```java
package com.example.myapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello from Java mode");
        setContentView(tv);
    }
}
```

---

## 🏗 构建（开发者）

### 环境

- JDK 17 或 21
- Android SDK（build-tools 36.0.0）
- Gradle 8.14.3（已通过 wrapper 配置）

### 编译

```bash
git clone https://github.com/alice-bluearchive/apk-builder-android.git
cd apk-builder-android
./gradlew assembleDebug
```

生成的 APK 在 `app/build/outputs/apk/debug/app-debug.apk`。

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📂 项目结构

```
app/src/main/
├── assets/
│   ├── editor/                          # CodeMirror 编辑器
│   ├── template/                        # 内置工具链
│   │   ├── android.jar.zip              # android.jar（解压后使用）
│   │   ├── ecj.dex / ecj.jar            # ECJ 编译器
│   │   ├── lambda-patch.jar             # LambdaMetafactory 支持
│   │   ├── classes.dex / classes2.dex   # HTML 模式预编译模板
│   │   └── ...
│   └── keystore/
│       └── debug.p12                    # 默认签名（可公开）
├── java/com/example/apkbuilder/
│   ├── MainActivity.java                # 主界面
│   ├── ProjectListActivity.java         # 项目列表
│   ├── EditorActivity.java              # 代码编辑器
│   ├── ApkBuilder.java                  # APK 打包核心
│   ├── EcjCompiler.java                 # ECJ 封装
│   ├── D8Wrapper.java                   # D8 封装
│   ├── ProjectStore.java                # 项目管理
│   ├── ProjectConfig.java               # 项目配置
│   ├── ProjectAdapter.java              # 项目列表适配器
│   ├── LocaleHelper.java                # 语言切换
│   └── ToolchainManager.java            # aapt2/zipalign 调用
└── jniLibs/arm64-v8a/                   # aapt2 / zipalign 及依赖 .so
```

---

## ⚠️ 已知限制

- **只支持 arm64-v8a 设备**（aapt2 等二进制只打包了这个架构）
- **Java 模式单入口**：生成的 APK 入口 Activity 固定为 `<包名>.MainActivity`
- **targetSdk 34**：为了兼容生成的 APK 直接安装
- **首次生成慢**：需要解压约 30 MB 的工具链到私有目录（5~10 秒）

---

## 📄 协议

MIT License — 详见 [LICENSE](LICENSE)

---

## 🙏 致谢

- [Termux](https://termux.dev/) — aapt2 / zipalign 的 Android 版二进制
- [Eclipse JDT](https://www.eclipse.org/jdt/) — ECJ 编译器
- [R8](https://r8.googlesource.com/r8) — D8 编译器
- [apksig](https://github.com/google/apksig) — APK 签名库
- [CodeMirror](https://codemirror.net/) — 代码编辑器