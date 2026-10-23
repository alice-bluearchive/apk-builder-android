# APK Builder (v2)

> 鍦?Android 鎵嬫満涓婄绾挎墦鍖?APK 鐨?IDE銆?
> An offline APK builder IDE that runs entirely on Android.

[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Branch](https://img.shields.io/badge/branch-v2-orange)]()

---

## 鈿狅笍 鍏充簬鍒嗘敮 / About branches

鏈粨搴撴湁涓や釜鍒嗘敮锛?

| 鍒嗘敮 | 鍐呭 | 鍖呭悕 |
|---|---|---|
| `main` | v1 鏃ч」鐩紙淇濈暀锛?| `com.example.apkbuilder` |
| **`v2`** | **褰撳墠寮€鍙戠増锛堟帹鑽愶級** | `com.example.androidstudio` |

鏈?README 鎻忚堪鐨勬槸 **v2**銆傛棫鐗堣鍒囧埌 `main` 鍒嗘敮銆?

---

## 鉁?鍔熻兘 / Features

### 鏋勫缓鑳藉姏
- **HTML 鈫?APK**锛氬崟鏂囦欢 HTML 鎵撴垚 WebView 澹?
- **Java 鈫?APK**锛欵CJ 缂栬瘧 + D8 杞?dex + aapt2 鎵撳寘 + 绛惧悕
- **妯℃澘绯荤粺**锛欵mpty / Empty View锛堝鏍?Android Studio 妯℃澘锛?
- **瀹屾暣 res/ 鏀寔**锛氱敤鎴烽」鐩噷鐨?`res/`銆乣AndroidManifest.xml` 鐪熷疄鐢熸晥
- **Release 绛惧悕**锛氬鍏?JKS / PKCS12 keystore 绛?release 鍖?
- **鍏ㄧ绾?*锛歛apt2 + ECJ + D8 + apksig 鍏ㄩ儴鍐呯疆锛屾棤闇€鑱旂綉

### IDE 鐣岄潰锛坴2 鏂板锛?
- VSCode 椋庢牸渚ц竟鏍忥細馃搧 鏂囦欢 | 馃攳 鎼滅储 | 鈳?Git | 馃З 鎵╁睍
- 渚ц竟鏍忓彲鎷栨嫿瀹藉害銆佸彲鏁翠綋鏀惰捣
- 澶?tab 缂栬緫鍣紙CodeMirror锛屾敮鎸佽娉曢珮浜€佽鍙凤級
- 鍐呭祵缁堢锛屾瀯寤烘棩蹇楀疄鏃舵粴鍔?
- 椤圭洰鍏ㄦ枃鏈悳绱紙鏂囦欢鍚?+ 鍐呭锛?
- 閿欒琛屽彿鐐瑰嚮璺宠浆

### 鎵╁睍绯荤粺锛坴2 鏂板锛?
- 鎵╁睍 = 鐙珛 zip 鍖咃紙`classes.dex` + `manifest.json`锛?
- 杩愯鏃跺姞杞斤紝鏃犻渶閲嶆柊缂栬瘧涓?App
- 鎵╁睍鍙互锛?
    - 娉ㄥ唽 鈰?鑿滃崟椤?
    - 娉ㄥ唽渚ц竟鏍忛潰鏉匡紙鎸夐挳鍒楄〃 鎴?鑷畾涔?View锛?
    - 浣跨敤鑷甫鐨勫浘鏍?PNG
    - 鎼哄甫绗笁鏂?jar锛堣嚜鍔ㄨ浆鎴?`lib/*.dex`锛?
- 鍐呯疆 **Git 鎵╁睍**锛堝熀浜?JGit 5.13锛夛細init / status / add / commit / log / branches

### 涓嫳鍙岃
- 鍏ㄩ儴鐣岄潰涓嫳鍒囨崲
- 娣辫壊涓婚

---

## 馃摳 鎴浘 / Screenshots

> 寰呰ˉ鍏?

---

## 馃摝 涓嬭浇 / Download

浠?[Releases](https://github.com/alice-bluearchive/apk-builder-android/releases) 涓嬭浇鏈€鏂扮殑 `app-debug.apk`锛堢害 42 MB锛夈€?

**棣栨鍚姩**闇€瑕佹巿浜堛€屾墍鏈夋枃浠惰闂€嶆潈闄愶紝鐢ㄤ簬璇诲啓 `/storage/emulated/0/apk-project/` 涓嬬殑椤圭洰鐩綍銆?

---

## 馃洜 浠庢簮鐮佹瀯寤?/ Build from source

### 鐜瑕佹眰
- **JDK 17 鎴?21**锛圝DK 22+ 涓嶅吋瀹?AGP 8.x锛?
- **Android SDK**锛坄compileSdk 36`锛?
- **Gradle 8.14+**锛堢敤 wrapper 鑷甫鐨勫嵆鍙級

### 姝ラ

```bash
git clone -b v2 https://github.com/alice-bluearchive/apk-builder-android.git
cd apk-builder-android

# 鍦?local.properties 閲屾寚瀹氫綘鐨?SDK 璺緞
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 鏋勫缓
./gradlew :app:assembleDebug
```

浜х墿锛歚app/build/outputs/apk/debug/app-debug.apk`

### 椤圭洰缁撴瀯

```
app/src/main/
鈹溾攢鈹€ java/com/example/androidstudio/
鈹?  鈹溾攢鈹€ ApkBuilder.java              鏍稿績鎵撳寘娴佺▼
鈹?  鈹溾攢鈹€ ProjectBuilder.java          鏋勫缓璋冨害鍣?
鈹?  鈹溾攢鈹€ EcjCompiler.java             ECJ 鍔ㄦ€佸姞杞?+ 缂栬瘧
鈹?  鈹溾攢鈹€ D8Wrapper.java               class 鈫?dex
鈹?  鈹溾攢鈹€ IdeActivity.java             IDE 涓荤晫闈?
鈹?  鈹溾攢鈹€ ExtensionManager.java        鎵╁睍鍔犺浇 / 鍗歌浇
鈹?  鈹溾攢鈹€ ExtensionHost.java           鎵╁睍 API
鈹?  鈹斺攢鈹€ ...
鈹溾攢鈹€ assets/
鈹?  鈹溾攢鈹€ template/                    宸ュ叿閾撅紙aapt2 绛夛紝鐢?jniLibs 鎻愪緵锛?
鈹?  鈹溾攢鈹€ templates/                   鏂板缓椤圭洰妯℃澘
鈹?  鈹斺攢鈹€ editor/                      CodeMirror
鈹溾攢鈹€ jniLibs/arm64-v8a/               53 涓?.so锛坅apt2 + 渚濊禆锛?
鈹斺攢鈹€ res/                             涓?App 璧勬簮
```

---

## 馃З 寮€鍙戞墿灞?/ Writing extensions

鎵╁睍鏄竴涓?zip 鍖咃紝鍖呭惈锛?

```
myext.zip
鈹溾攢鈹€ manifest.json        鎵╁睍鍏冧俊鎭?
鈹溾攢鈹€ classes.dex          缂栬瘧鍚庣殑 Java 浠ｇ爜
鈹溾攢鈹€ icon.png             锛堝彲閫夛級渚ц竟鏍忓浘鏍?
鈹斺攢鈹€ lib/                 锛堝彲閫夛級绗笁鏂?jar 杞殑 dex
    鈹斺攢鈹€ classes.dex
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

### 鍏ュ彛绫?

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

        // 鑿滃崟椤?
        host.registerMenuItem("Say hello", this::hello);

        // 渚ц竟鏍忛潰鏉?
        SidebarPanelSpec panel = new SidebarPanelSpec();
        panel.id = "myext-panel";
        panel.title = "My Extension";
        panel.iconName = "extensions";   // 鍐呯疆鍥炬爣闆?
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

### 鍐呯疆鍥炬爣闆?

`files` / `search` / `git` / `extensions` / `terminal`

### 鑷畾涔?View 闈㈡澘

```java
panel.viewFactory = ctx -> {
    LinearLayout v = new LinearLayout(ctx);
    v.setOrientation(LinearLayout.VERTICAL);
    // 鏀句换鎰?View
    return v;
};
```

### 瀹夎

1. 鎶?zip 鎷峰埌鎵嬫満 `/sdcard/Download/`
2. 涓?App 鈫?浠绘剰椤圭洰 鈫?馃З 鈫?绠＄悊鎵╁睍 鈫?瀵煎叆 ZIP
3. 閫€鍑哄苟閲嶈繘椤圭洰浠ュ姞杞?

璇﹁ [ext-template](https://github.com/alice-bluearchive/apk-builder-android/tree/v2/ext-template)锛堢ず渚嬫墿灞曢鏋讹級銆?

---

## 馃О 宸ュ叿閾?/ Toolchain

鍐呯疆锛坄assets/template/` + `jniLibs/`锛夛細

| 缁勪欢 | 鐢ㄩ€?|
|---|---|
| `aapt2` | 璧勬簮缂栬瘧 + 鎵撳寘 |
| `zipalign` | APK 瀵归綈 |
| `android.jar` | 缂栬瘧鏈?API 瀛樻牴锛堝帇缂?24 MB锛?|
| `ecj.dex` + `ecj.jar` | Eclipse Java 缂栬瘧鍣紙DexClassLoader 鍔犺浇锛?|
| `lambda-patch.jar` | Lambda 琛ㄨ揪寮忔敮鎸?|
| `apksig` | V1 + V2 绛惧悕 |
| `D8 (R8 搴?` | class 鈫?dex |

---

## 馃搫 License

[MIT](LICENSE)

---

## 馃檹 鑷磋阿 / Credits

- [Eclipse JDT](https://www.eclipse.org/jdt/) 鈥?ECJ 缂栬瘧鍣?
- [JGit](https://www.eclipse.org/jgit/) 鈥?Git 瀹炵幇
- [CodeMirror](https://codemirror.net/) 鈥?缂栬緫鍣ㄥ唴鏍?
- [Termux](https://termux.dev/) 鈥?aapt2 浜岃繘鍒剁殑鏉ユ簮