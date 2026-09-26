package com.yunpu.yunpumusic.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Gson 便捷访问工具，避免到处写空值判断。 */
public final class Json {
    private Json() {
    }

    public static JsonObject parse(String text) {
        try {
            JsonElement element = JsonParser.parseString(text);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    public static JsonObject obj(JsonObject parent, String member) {
        if (parent == null) {
            return null;
        }
        JsonElement element = parent.get(member);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static JsonArray array(JsonObject parent, String member) {
        if (parent == null) {
            return new JsonArray();
        }
        JsonElement element = parent.get(member);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    public static String str(JsonObject parent, String member, String fallback) {
        if (parent == null) {
            return fallback;
        }
        JsonElement element = parent.get(member);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static String str(JsonObject parent, String member) {
        return str(parent, member, "");
    }

    /** 依次尝试多个成员名，返回第一个非空字符串（不同平台/接口版本字段名不一致）。 */
    public static String firstStr(JsonObject parent, String fallback, String... members) {
        for (String member : members) {
            String value = str(parent, member, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return fallback;
    }

    public static long num(JsonObject parent, String member, long fallback) {
        if (parent == null) {
            return fallback;
        }
        JsonElement element = parent.get(member);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException e) {
            try {
                return (long) Double.parseDouble(element.getAsString());
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
    }

    public static long firstNum(JsonObject parent, long fallback, String... members) {
        for (String member : members) {
            JsonElement element = parent == null ? null : parent.get(member);
            if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
                long value = num(parent, member, Long.MIN_VALUE);
                if (value != Long.MIN_VALUE) {
                    return value;
                }
            }
        }
        return fallback;
    }

    public static boolean bool(JsonObject parent, String member, boolean fallback) {
        if (parent == null) {
            return fallback;
        }
        JsonElement element = parent.get(member);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            try {
                return element.getAsInt() != 0;
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
    }

    /** 把 "a、b、c" / "a/b" / "a,b" 这类分隔的歌手串拆成列表。 */
    public static List<String> splitArtists(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split("[、,，/&;；|]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        if (result.isEmpty()) {
            result.add(raw.trim());
        }
        return result;
    }
}
