package com.yunpu.yunpumusic.credentials;

import net.minecraft.network.chat.Component;

/**
 * 玩家在该服务器上的权限等级。
 *
 * <p>决定登录凭据能否写入服务端：只有服主与管理员可以把扫码得到的会话凭据
 * 保存到存档里，让整台刻录台在无人登录时也能播放会员曲目；
 * 普通玩家只能把凭据保存在本地客户端。
 */
public enum Role {
    /** 单人游戏主机 / 服务器首位玩家。 */
    FOUNDER(3, "gui.yunpumusic.role.founder", 0xFFF2C230),
    /** 拥有 OP 权限的玩家。 */
    OPERATOR(2, "gui.yunpumusic.role.operator", 0xFF6FD3A0),
    /** 普通玩家：凭据仅保存在本地。 */
    PLAYER(0, "gui.yunpumusic.role.player", 0xFF9AA4B2);

    private final int permissionLevel;
    private final String translationKey;
    private final int color;

    Role(int permissionLevel, String translationKey, int color) {
        this.permissionLevel = permissionLevel;
        this.translationKey = translationKey;
        this.color = color;
    }

    public int permissionLevel() {
        return permissionLevel;
    }

    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    /** 是否允许把凭据写入服务端。 */
    public boolean canStoreOnServer() {
        return this != PLAYER;
    }

    public static Role byOrdinal(int ordinal) {
        Role[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PLAYER;
    }

    /**
     * 判定玩家角色：单人存档主机 / 服务器拥有者视为服主，拥有 OP（2 级）权限的视为管理员。
     */
    public static Role resolve(net.minecraft.server.level.ServerPlayer player) {
        var server = player.server;
        if (server != null && server.isSingleplayerOwner(player.getGameProfile())) {
            return FOUNDER;
        }
        if (player.hasPermissions(4)) {
            return FOUNDER;
        }
        if (player.hasPermissions(2)) {
            return OPERATOR;
        }
        return PLAYER;
    }

    /** 依据权限等级判定角色（保留给需要直接按等级判定的场景）。 */
    public static Role fromPermissionLevel(int level) {
        if (level >= 3) {
            return FOUNDER;
        }
        if (level >= 2) {
            return OPERATOR;
        }
        return PLAYER;
    }
}
