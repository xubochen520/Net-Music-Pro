package com.yunpu.yunpumusic.credentials;

import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.PlatformId;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * 本地凭据存储（客户端）。
 *
 * <p>扫码登录得到的会话凭据默认只保存在玩家自己的游戏目录：
 * {@code config/yunpumusic/credentials.json}。
 * 这样可以避免把账号会话交给服务器，同时也让普通玩家在自己的客户端上搜索与刻录。
 */
public final class LocalCredentialStore {
    private static final String FILE_NAME = "credentials.json";
    private static final Map<PlatformId, AuthState> SESSIONS = new EnumMap<>(PlatformId.class);
    private static boolean loaded;

    private LocalCredentialStore() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(YunpuMusic.MOD_ID).resolve(FILE_NAME);
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for (PlatformId platform : PlatformId.values()) {
                com.google.gson.JsonObject entry = com.yunpu.yunpumusic.api.Json.obj(root, platform.id());
                if (entry != null) {
                    SESSIONS.put(platform, AuthState.fromJson(entry));
                }
            }
            YunpuMusic.LOGGER.info("已加载本地音乐账号凭据：{}", SESSIONS.keySet());
        } catch (Exception e) {
            YunpuMusic.LOGGER.warn("本地凭据文件解析失败，将忽略：{}", path, e);
        }
    }

    public static synchronized void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            SESSIONS.forEach((platform, auth) -> root.add(platform.id(), auth.toJson()));
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(root.toString());
            }
        } catch (IOException e) {
            YunpuMusic.LOGGER.error("保存本地凭据失败：{}", path, e);
        }
    }

    @Nullable
    public static synchronized AuthState get(PlatformId platform) {
        load();
        return SESSIONS.get(platform);
    }

    public static synchronized void put(PlatformId platform, AuthState auth) {
        load();
        SESSIONS.put(platform, auth);
        save();
    }

    public static synchronized void remove(PlatformId platform) {
        load();
        SESSIONS.remove(platform);
        save();
    }

    public static synchronized Map<PlatformId, AuthState> all() {
        load();
        return Map.copyOf(SESSIONS);
    }
}
