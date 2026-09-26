package com.yunpu.yunpumusic.api;

/**
 * 平台标识。刻意与 UI 层的 {@code com.yunpu.yunpumusic.api.Platform} 分离，
 * 让第三方平台适配层保持「零 Minecraft 依赖」，可以直接用纯 JDK 跑离线测试。
 */
public enum PlatformId {
    KUGOU("kugou", "酷狗音乐"),
    NETEASE("netease", "网易云音乐"),
    QQ("qq", "QQ 音乐");

    private final String id;
    private final String displayName;

    PlatformId(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static PlatformId byId(String id) {
        for (PlatformId platform : values()) {
            if (platform.id.equalsIgnoreCase(id)) {
                return platform;
            }
        }
        return NETEASE;
    }
}
