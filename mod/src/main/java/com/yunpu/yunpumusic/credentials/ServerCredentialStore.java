package com.yunpu.yunpumusic.credentials;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.PlatformId;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * 服务端凭据存储。
 *
 * <p><b>只有服主 / OP 才能写入</b>：写入的内容是扫码登录得到的会话凭据，
 * 用于让刻录台在无人登录时也能解析会员曲目的直链。
 *
 * <p>凭据不会明文落盘：以世界种子派生的密钥做 AES-GCM 加密后写入
 * {@code <存档>/serverconfig/yunpumusic/credentials.json}。
 * 这样即使存档被复制走，也无法直接读出会话凭据（除非同时拿到世界种子与加盐值）。
 */
public final class ServerCredentialStore {
    private static final String DIR = "yunpumusic";
    private static final String FILE_NAME = "credentials.json";
    private static final int PBKDF2_ITERATIONS = 65_536;
    private static final int KEY_LENGTH = 256;

    private static final Map<PlatformId, AuthState> SESSIONS = new EnumMap<>(PlatformId.class);
    private static boolean loaded;
    @Nullable
    private static Path loadedFrom;

    private ServerCredentialStore() {
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig").resolve(DIR).resolve(FILE_NAME);
    }

    public static synchronized void load(MinecraftServer server) {
        Path path = file(server);
        if (loaded && path.equals(loadedFrom)) {
            return;
        }
        SESSIONS.clear();
        loaded = true;
        loadedFrom = path;
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            String saltText = root.has("salt") ? root.get("salt").getAsString() : "";
            byte[] salt = saltText.isEmpty() ? new byte[0] : Base64.getDecoder().decode(saltText);
            JsonObject data = root.has("data") ? root.getAsJsonObject("data") : new JsonObject();
            for (PlatformId platform : PlatformId.values()) {
                if (!data.has(platform.id())) {
                    continue;
                }
                String cipher = data.get(platform.id()).getAsString();
                String plain = CredentialCipher.decrypt(cipher, server, salt);
                if (plain != null && !plain.isEmpty()) {
                    SESSIONS.put(platform, AuthState.fromJson(JsonParser.parseString(plain).getAsJsonObject()));
                }
            }
            YunpuMusic.LOGGER.info("已加载服务端音乐账号凭据：{}", SESSIONS.keySet());
        } catch (Exception e) {
            YunpuMusic.LOGGER.warn("服务端凭据文件解析失败，将忽略：{}", path, e);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path path = file(server);
        try {
            Files.createDirectories(path.getParent());
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            JsonObject data = new JsonObject();
            SESSIONS.forEach((platform, auth) -> {
                String cipher = CredentialCipher.encrypt(auth.toJson().toString(), server, salt);
                if (cipher != null) {
                    data.addProperty(platform.id(), cipher);
                }
            });

            JsonObject root = new JsonObject();
            root.addProperty("salt", Base64.getEncoder().encodeToString(salt));
            root.addProperty("note", "会话凭据由扫码登录获得，已使用世界种子派生的密钥加密；请勿公开此文件。");
            root.add("data", data);
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            YunpuMusic.LOGGER.error("保存服务端凭据失败：{}", path, e);
        }
    }

    @Nullable
    public static synchronized AuthState get(MinecraftServer server, PlatformId platform) {
        load(server);
        return SESSIONS.get(platform);
    }

    public static synchronized void put(MinecraftServer server, PlatformId platform, AuthState auth) {
        load(server);
        SESSIONS.put(platform, auth);
        save(server);
    }

    public static synchronized void remove(MinecraftServer server, PlatformId platform) {
        load(server);
        SESSIONS.remove(platform);
        save(server);
    }

    public static synchronized Map<PlatformId, AuthState> all(MinecraftServer server) {
        load(server);
        return Map.copyOf(SESSIONS);
    }
}
