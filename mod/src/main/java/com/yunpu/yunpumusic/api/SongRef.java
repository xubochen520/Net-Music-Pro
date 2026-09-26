package com.yunpu.yunpumusic.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索结果条目。纯数据对象，不引用任何 Minecraft 类型，便于离线测试。
 */
public final class SongRef {
    private final PlatformId platform;
    private final String id;
    private final String title;
    private final List<String> artists;
    private final String album;
    private final int durationSeconds;
    private final boolean vip;
    private final int fee;
    /** 平台侧可用于直链解析的附加字段（如酷狗的 hash、QQ 的 media_mid）。 */
    private final Map<String, String> extras;

    public SongRef(PlatformId platform, String id, String title, List<String> artists, String album,
                   int durationSeconds, boolean vip, int fee, Map<String, String> extras) {
        this.platform = platform;
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.artists = artists == null ? List.of() : List.copyOf(artists);
        this.album = album == null ? "" : album;
        this.durationSeconds = Math.max(0, durationSeconds);
        this.vip = vip;
        this.fee = fee;
        this.extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    public PlatformId platform() {
        return platform;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public List<String> artists() {
        return artists;
    }

    public String artistText() {
        return String.join(" / ", artists);
    }

    public String album() {
        return album;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    /** 是否需要会员（各平台官方标记归一化后的结果）。 */
    public boolean vip() {
        return vip;
    }

    /** 平台原始付费标记，仅用于排查问题。 */
    public int fee() {
        return fee;
    }

    public String extra(String key) {
        return extras.getOrDefault(key, "");
    }

    public Map<String, String> extras() {
        return extras;
    }

    public String durationText() {
        if (durationSeconds <= 0) {
            return "--:--";
        }
        return "%d:%02d".formatted(durationSeconds / 60, durationSeconds % 60);
    }

    /** 用于跨平台比较的稳定键：平台 + 歌曲 ID。 */
    public String key() {
        return platform.id() + ":" + id;
    }

    // ------------------------------------------------------------------
    // 序列化（网络包 / 本地缓存共用）
    // ------------------------------------------------------------------

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("platform", platform.id());
        json.addProperty("id", id);
        json.addProperty("title", title);
        json.addProperty("artists", String.join("\u0001", artists));
        json.addProperty("album", album);
        json.addProperty("duration", durationSeconds);
        json.addProperty("vip", vip);
        json.addProperty("fee", fee);
        JsonObject extraJson = new JsonObject();
        extras.forEach(extraJson::addProperty);
        json.add("extras", extraJson);
        return json;
    }

    public String toJsonString() {
        return toJson().toString();
    }

    public static SongRef fromJson(String text) {
        JsonObject json = Json.parse(text);
        return fromJson(json);
    }

    public static SongRef fromJson(JsonObject json) {
        Map<String, String> extras = new LinkedHashMap<>();
        JsonObject extraJson = Json.obj(json, "extras");
        if (extraJson != null) {
            extraJson.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    extras.put(entry.getKey(), entry.getValue().getAsString());
                }
            });
        }
        String artists = Json.str(json, "artists");
        List<String> artistList = artists.isEmpty() ? List.of() : List.of(artists.split("\u0001"));
        return new SongRef(
                PlatformId.byId(Json.str(json, "platform", PlatformId.NETEASE.id())),
                Json.str(json, "id"),
                Json.str(json, "title"),
                artistList,
                Json.str(json, "album"),
                (int) Json.num(json, "duration", 0),
                Json.bool(json, "vip", false),
                (int) Json.num(json, "fee", 0),
                extras);
    }

    /** 便于调试。 */
    public static SongRef parseDebug(String text) {
        return fromJson(JsonParser.parseString(text).getAsJsonObject());
    }
}
