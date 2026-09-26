package com.yunpu.yunpumusic.api;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 某个平台的登录态。
 *
 * <p>只保存会话凭据（Cookie / token），<b>不保存账号密码</b>：
 * 扫码登录拿到的本来就是平台下发的临时会话，这也是本模组唯一接触到的凭据形态。
 */
public final class AuthState {
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private String userId = "";
    private String nickname = "";
    private String avatarUrl = "";
    private volatile Membership membership = Membership.UNKNOWN;
    /** token 型平台（QQ / 酷狗）额外保存的字段。 */
    private final Map<String, String> tokens = new LinkedHashMap<>();

    public Map<String, String> cookies() {
        return cookies;
    }

    public Map<String, String> tokens() {
        return tokens;
    }

    public String userId() {
        return userId;
    }

    public String nickname() {
        return nickname;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public boolean vip() {
        return membership == Membership.VIP || membership == Membership.SVIP;
    }

    public Membership membership() { return membership; }

    public AuthState setMembership(Membership membership) {
        this.membership = membership == null ? Membership.UNKNOWN : membership;
        return this;
    }

    public AuthState setUserId(String userId) {
        this.userId = userId == null ? "" : userId;
        return this;
    }

    public AuthState setNickname(String nickname) {
        this.nickname = nickname == null ? "" : nickname;
        return this;
    }

    public AuthState setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        return this;
    }

    public AuthState setVip(boolean vip) {
        this.membership = vip ? Membership.VIP : Membership.FREE;
        return this;
    }

    public AuthState putCookie(String name, String value) {
        if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
            cookies.put(name.trim(), value.trim());
        }
        return this;
    }

    public AuthState putAll(Map<String, String> values) {
        if (values != null) {
            values.forEach(this::putCookie);
        }
        return this;
    }

    public AuthState putToken(String name, String value) {
        if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
            tokens.put(name.trim(), value.trim());
        }
        return this;
    }

    public String cookieHeader() {
        StringBuilder sb = new StringBuilder();
        cookies.forEach((name, value) -> {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(name).append('=').append(value);
        });
        return sb.toString();
    }

    public boolean loggedIn() {
        return !cookies.isEmpty() || !tokens.isEmpty();
    }

    /** 界面上显示的账号名。 */
    public String displayName() {
        if (!nickname.isBlank()) {
            return nickname;
        }
        if (!userId.isBlank()) {
            return "UID " + userId;
        }
        return "";
    }

    public AuthState copy() {
        AuthState copy = new AuthState();
        copy.cookies.putAll(cookies);
        copy.tokens.putAll(tokens);
        copy.userId = userId;
        copy.nickname = nickname;
        copy.avatarUrl = avatarUrl;
        copy.membership = membership;
        return copy;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("userId", userId);
        json.addProperty("nickname", nickname);
        json.addProperty("avatar", avatarUrl);
        json.addProperty("vip", vip());
        json.addProperty("membership", membership.name());
        JsonObject cookieJson = new JsonObject();
        cookies.forEach(cookieJson::addProperty);
        json.add("cookies", cookieJson);
        JsonObject tokenJson = new JsonObject();
        tokens.forEach(tokenJson::addProperty);
        json.add("tokens", tokenJson);
        return json;
    }

    public static AuthState fromJson(JsonObject json) {
        AuthState state = new AuthState();
        if (json == null) {
            return state;
        }
        state.userId = Json.str(json, "userId");
        state.nickname = Json.str(json, "nickname");
        state.avatarUrl = Json.str(json, "avatar");
        try {
            state.membership = Membership.valueOf(Json.str(json, "membership", "UNKNOWN"));
        } catch (IllegalArgumentException ignored) {
            state.membership = Membership.UNKNOWN;
        }
        JsonObject cookieJson = Json.obj(json, "cookies");
        if (cookieJson != null) {
            cookieJson.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    state.cookies.put(entry.getKey(), entry.getValue().getAsString());
                }
            });
        }
        JsonObject tokenJson = Json.obj(json, "tokens");
        if (tokenJson != null) {
            tokenJson.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    state.tokens.put(entry.getKey(), entry.getValue().getAsString());
                }
            });
        }
        return state;
    }
}
