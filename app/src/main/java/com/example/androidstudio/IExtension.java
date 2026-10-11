package com.example.androidstudio;

/**
 * 扩展接口。扩展里也要有一份同名同包名的拷贝（不编译进 dex，
 * 而是靠主 App 的 ClassLoader 提供）。
 */
public interface IExtension {
    String getId();
    String getName();
    String getVersion();

    /**
     * 扩展被加载时调用，一般在这里 host.registerMenuItem(...) 注册自己的入口。
     */
    void onLoad(ExtensionHost host);

    /** 主 App 退出前调用。 */
    void onUnload();
}