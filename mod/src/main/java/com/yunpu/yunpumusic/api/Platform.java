package com.yunpu.yunpumusic.api;

import net.minecraft.network.chat.Component;

/**
 * 支持的音乐平台。规范平台配色用于搜索结果中的平台标记：
 * 酷狗蓝色、网易红色、QQ 黄色。
 */
public enum Platform {
    KUGOU("kugou", "gui.yunpumusic.platform.kugou", 0xFF3D9BE9, 0xFF1E5F91),
    NETEASE("netease", "gui.yunpumusic.platform.netease", 0xFFE64A3B, 0xFF8E1F14),
    QQ("qq", "gui.yunpumusic.platform.qq", 0xFFF2C230, 0xFF9A7311);

    private final String id;
    private final String translationKey;
    private final int accent;
    private final int accentDark;

    Platform(String id, String translationKey, int accent, int accentDark) {
        this.id = id;
        this.translationKey = translationKey;
        this.accent = accent;
        this.accentDark = accentDark;
    }

    public String id() {
        return id;
    }

    public int accent() {
        return accent;
    }

    public int accentDark() {
        return accentDark;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public static Platform byId(String id) {
        for (Platform platform : values()) {
            if (platform.id.equals(id)) {
                return platform;
            }
        }
        return NETEASE;
    }
}
