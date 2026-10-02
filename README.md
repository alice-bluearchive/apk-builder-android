# APK Builder for Android

在 Android 设备上离线打包 APK 的工具。选 HTML 或 Java 文件，一键生成可安装的 APK，**全程不需要电脑**。

An offline APK builder for Android. Pick HTML or Java source, get a working APK — no PC required.

---

## ✨ 功能

- **HTML 模式** — 选一个 HTML 文件，打包成 WebView 应用
- **Java 模式** — 选一个或多个 `.java` 文件，编译后打包成 APK
- **完全离线** — 内置 aapt2 / ECJ / D8 / apksig，不需要联网
- **可定制** — 包名、应用名、图标（任意图片格式）、权限
- **项目管理** — 项目存在 `/storage/emulated/0/apk-project/`，随时切换
- **多语言** — 中文 / English / 跟随系统

---

## 🛠 内置的工具链

| 工具 | 用途 | 来源 |
|---|---|---|
| aapt2 | 资源编译、链接 | Termux |
| zipalign | APK 对齐 | Termux |
| ECJ | Java 编译器 | Eclipse |
| D8 (R8) | `.class` → `.dex` | Google |
| apksig | APK 签名 | Google |

全部预置在 `app/src/main/assets/template/`，首次使用时释放到 App 私有目录。

---

## 📱 使用

### HTML 模式

1. 新建项目
2. 模式选「HTML」
3. 填包名（如 `com.example.mywebapp`）、应用名
4. 点「选择源文件」，选一个 `.html`
5. 点「生成 APK」
6. 生成完成后会提示安装或打开目录

### Java 模式

1. 新建项目
2. 模式选「Java」
3. 填包名（**必须和 .java 里的 `package` 一致**）、应用名
4. 点「选择源文件」，可选多个 `.java`
5. 点「生成 APK」
6. 生成的 APK 里 `MainActivity` 必须是入口 Activity

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
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd YOUR_REPO
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
│   ├── template/                    # 内置工具链和模板
│   │   ├── android.jar              # 编译 classpath
│   │   ├── ecj.dex / ecj.jar        # ECJ 编译器
│   │   ├── classes.dex              # HTML 模式预编译模板
│   │   └── ...
│   └── keystore/
│       └── debug.p12                # 默认签名（可公开）
├── java/com/example/apkbuilder/
│   ├── MainActivity.java            # 主界面
│   ├── ProjectListActivity.java     # 项目列表
│   ├── ApkBuilder.java              # APK 打包核心
│   ├── EcjCompiler.java             # ECJ 封装
│   ├── D8Wrapper.java               # D8 封装
│   ├── ProjectStore.java            # 项目管理
│   ├── ProjectConfig.java           # 项目配置
│   ├── ToolchainManager.java        # aapt2/zipalign 调用
│   └── JdkManager.java              # （已弃用，保留）
└── jniLibs/arm64-v8a/               # aapt2 / zipalign 及依赖 .so
```

---

## ⚠️ 已知限制

- **只支持 arm64-v8a 设备**（aapt2 等二进制只打包了这个架构）
- **Java 模式单文件入口**：APK 的入口 Activity 类名必须写在 `AndroidManifest.xml` 的 `android:name` 里（当前版本内嵌模板固定为 `<包名>.MainActivity`）
- **targetSdk 34**：为了兼容生成的 APK 直接安装
- **首次生成慢**：需要解压约 30 MB 的工具链到私有目录

---

## 📄 协议

MIT License — 详见 [LICENSE](LICENSE)

---

## 🙏 致谢

- [Termux](https://termux.dev/) — aapt2 / zipalign 的 Android 版二进制
- [Eclipse JDT](https://www.eclipse.org/jdt/) — ECJ 编译器
- [R8](https://r8.googlesource.com/r8) — D8 编译器
- [apksig](https://github.com/google/apksig) — APK 签名库