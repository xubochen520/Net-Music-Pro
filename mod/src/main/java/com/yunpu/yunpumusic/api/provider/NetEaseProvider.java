package com.yunpu.yunpumusic.api.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.IMusicProvider;
import com.yunpu.yunpumusic.api.Membership;
import com.yunpu.yunpumusic.api.Json;
import com.yunpu.yunpumusic.api.PlatformId;
import com.yunpu.yunpumusic.api.QrLoginSession;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.api.UrlEncoder;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网易云音乐适配器。
 *
 * <p>使用网易云 Web 端的公开接口：
 * <ul>
 *   <li>扫码登录：{@code /api/login/qrcode/unikey} + {@code /api/login/qrcode/client/login}</li>
 *   <li>搜索：{@code /api/search/get/web}（type=1 单曲，自带 hiName/artists/album/fee 字段）</li>
 *   <li>直链：{@code /api/song/enhance/player/url/v1}（携带登录 cookie 可获得会员码率）</li>
 *   <li>歌词：{@code /api/song/lyric}（lv/tv 同时取原文与翻译）</li>
 * </ul>
 */
public final class NetEaseProvider implements IMusicProvider {
    private static final String BASE = "https://music.163.com";
    private static final String REFERER = "https://music.163.com/";
    /** 网易云 Web 端写死的固定值：扫码登录类型。 */
    private static final String QR_TYPE = "3";

    @Override
    public PlatformId platform() {
        return PlatformId.NETEASE;
    }

    private Map<String, String> headers(AuthState auth) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", REFERER);
        headers.put("Origin", BASE);
        if (auth != null && !auth.cookieHeader().isEmpty()) {
            headers.put("Cookie", auth.cookieHeader());
        }
        return headers;
    }

    @Override
    public Membership membership(AuthState auth) throws Exception {
        if (auth == null || !auth.loggedIn()) return Membership.UNKNOWN;
        Http.Response response = Http.postForm(BASE + "/api/music-vip-membership/client/vip/info", "", headers(auth));
        JsonObject root = Json.parse(response.body());
        if (response.ok() && Json.num(root, "code", -1) == 200) {
            Membership parsed = parseMembership(Json.obj(root, "data"));
            if (parsed != Membership.UNKNOWN) return parsed;
        }
        // The account endpoint is a fallback for older Web sessions whose VIP
        // membership response contains only the growth-level package.
        Http.Response accountResponse = Http.get(BASE + "/api/nuser/account/get", headers(auth));
        JsonObject account = Json.parse(accountResponse.body());
        if (!accountResponse.ok() || Json.num(account, "code", -1) != 200) return Membership.UNKNOWN;
        Membership profileTier = parseMembership(Json.obj(account, "profile"));
        if (profileTier != Membership.UNKNOWN) return profileTier;
        return parseMembership(Json.obj(account, "account"));
    }

    private static Membership parseMembership(JsonObject data) {
        if (data == null) return Membership.UNKNOWN;
        JsonObject detail = Json.obj(data, "vipDetail");
        if (detail == null) detail = Json.obj(data, "fullVipDetail");
        if (detail != null) {
            if (activeMembership(Json.obj(detail, "15"))) return Membership.SVIP;
            if (activeMembership(Json.obj(detail, "6"))) return Membership.VIP;
        }
        long type = Json.firstNum(data, -1, "vipType", "vip_type");
        if (type == 15) return Membership.SVIP;
        if (type == 6 || type == 10 || type == 11) return Membership.VIP;
        if (type == 0) return Membership.FREE;
        return Membership.UNKNOWN;
    }

    private static boolean activeMembership(JsonObject value) {
        if (value == null) return false;
        long expires = Json.firstNum(value, 0, "expireTime", "endTime", "expireDate");
        if (expires > 0) return expires > (expires < 10_000_000_000L
                ? System.currentTimeMillis() / 1000 : System.currentTimeMillis());
        return Json.num(value, "status", 0) == 1;
    }

    // ------------------------------------------------------------------
    // 扫码登录
    // ------------------------------------------------------------------

    @Override
    public QrLoginSession startLogin() throws Exception {
        QrLoginSession session = new QrLoginSession(platform());
        Http.Response keyResponse = Http.postForm(BASE + "/api/login/qrcode/unikey",
                "type=" + QR_TYPE, headers(null));
        long code = Json.num(Json.parse(keyResponse.body()), "code", -1);
        String unikey = Json.str(Json.parse(keyResponse.body()), "unikey");
        if (code != 200 || unikey.isEmpty()) {
            session.setStatus(QrLoginSession.Status.FAILED)
                    .setMessage("网易云二维码获取失败（code=" + code + "）");
            return session;
        }
        session.setSessionKey(unikey)
                .setQrContent("https://music.163.com/login?codekey=" + unikey)
                .setStatus(QrLoginSession.Status.WAITING)
                .setExpiresAt(System.currentTimeMillis() + 5 * 60 * 1000L);
        return session;
    }

    @Override
    public QrLoginSession pollLogin(QrLoginSession session) throws Exception {
        if (session.sessionKey().isEmpty() || session.finished()) {
            return session;
        }
        if (session.expired()) {
            return session.setStatus(QrLoginSession.Status.EXPIRED).setMessage("二维码已过期，请刷新");
        }

        Http.Response response = Http.postForm(BASE + "/api/login/qrcode/client/login",
                "type=" + QR_TYPE + "&key=" + session.sessionKey(), headers(null));
        JsonObject json = Json.parse(response.body());
        int code = (int) Json.num(json, "code", -1);
        String message = Json.str(json, "message");
        switch (code) {
            case 800 -> session.setStatus(QrLoginSession.Status.EXPIRED).setMessage("二维码已过期，请刷新");
            case 801 -> session.setStatus(QrLoginSession.Status.WAITING).setMessage("等待扫码…");
            case 802 -> session.setStatus(QrLoginSession.Status.SCANNED).setMessage("已扫码，请在手机上确认");
            case 803 -> {
                // 登录成功：响应头里带着 MUSIC_U 等 cookie
                AuthState auth = new AuthState();
                auth.putAll(response.cookies());
                JsonObject body = Json.obj(json, "body");
                if (body != null) {
                    auth.setUserId(Json.str(body, "userId"))
                            .setNickname(Json.str(body, "nickname"))
                            .setAvatarUrl(Json.str(body, "avatarUrl"));
                }
                // 部分情况下 cookie 只在 Set-Cookie 的 nickname 字段里
                if (auth.nickname().isEmpty() && response.cookies().containsKey("nickname")) {
                    auth.setNickname(response.cookies().get("nickname"));
                }
                if (!auth.loggedIn()) {
                    session.setStatus(QrLoginSession.Status.FAILED).setMessage("登录成功但未取到会话凭据");
                    return session;
                }
                session.setAuth(auth)
                        .setStatus(QrLoginSession.Status.SUCCESS)
                        .setMessage(auth.displayName().isEmpty() ? "登录成功" : "登录成功：" + auth.displayName());
            }
            default -> session.setStatus(QrLoginSession.Status.FAILED)
                    .setMessage(StringUtils.isBlank(message) ? ("登录失败（code=" + code + "）") : message);
        }
        return session;
    }

    // ------------------------------------------------------------------
    // 搜索
    // ------------------------------------------------------------------

    @Override
    public List<SongRef> search(String keyword, int limit, AuthState auth) throws Exception {
        String url = BASE + "/api/search/get/web?s=" + UrlEncoder.encode(keyword)
                     + "&type=1&limit=" + Math.max(1, Math.min(limit, 50)) + "&offset=0";
        Http.Response response = Http.get(url, headers(auth));
        JsonObject root = Json.parse(response.body());
        JsonObject result = Json.obj(root, "result");
        JsonArray songs = Json.array(result, "songs");
        List<SongRef> list = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            if (!songs.get(i).isJsonObject()) {
                continue;
            }
            JsonObject song = songs.get(i).getAsJsonObject();
            list.add(toSongRef(song));
        }
        return list;
    }

    private SongRef toSongRef(JsonObject song) {
        long id = Json.num(song, "id", 0);
        String title = Json.str(song, "name");
        String transName = firstTransName(song);

        List<String> artists = new ArrayList<>();
        JsonArray artistArray = Json.array(song, "artists");
        for (int i = 0; i < artistArray.size(); i++) {
            if (artistArray.get(i).isJsonObject()) {
                String name = Json.str(artistArray.get(i).getAsJsonObject(), "name");
                if (!name.isBlank()) {
                    artists.add(name);
                }
            }
        }
        if (artists.isEmpty()) {
            artists.addAll(Json.splitArtists(Json.str(song, "artistName")));
        }

        String album = "";
        JsonObject albumJson = Json.obj(song, "album");
        if (albumJson != null) {
            album = Json.str(albumJson, "name");
        }

        // Match Net Music's own needVip(): fee=1 requires membership.
        // fee=4 is a separately purchased album and must not be labeled VIP.
        int fee = (int) Json.num(song, "fee", 0);
        boolean vip = fee == 1;

        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("fee", String.valueOf(fee));
        if (!transName.isEmpty()) {
            extras.put("transName", transName);
        }
        long durationMs = Json.num(song, "duration", Json.num(song, "dt", 0));

        return new SongRef(platform(), String.valueOf(id), title, artists, album,
                (int) (durationMs / 1000), vip, fee, extras);
    }

    private String firstTransName(JsonObject song) {
        JsonArray transNames = Json.array(song, "transNames");
        if (transNames.size() == 0) {
            transNames = Json.array(song, "tns");
        }
        if (transNames.size() > 0 && transNames.get(0).isJsonPrimitive()) {
            return transNames.get(0).getAsString();
        }
        return "";
    }

    // ------------------------------------------------------------------
    // 直链
    // ------------------------------------------------------------------

    @Override
    public String resolveStreamUrl(SongRef song, AuthState auth) throws Exception {
        // 优先走 v1 接口：未登录时也能返回 128k 试听直链，登录后按会员等级返回更高码率
        String url = BASE + "/api/song/enhance/player/url/v1?encodeType=mp3&level=exhigh&ids=["
                     + song.id() + "]";
        try {
            String resolved = parsePlayerUrl(Http.get(url, headers(auth)).body());
            if (!resolved.isEmpty()) {
                return resolved;
            }
        } catch (IOException ignored) {
            // 回退到旧的 outer/url 接口
        }
        return BASE + "/song/media/outer/url?id=" + song.id() + ".mp3";
    }

    private String parsePlayerUrl(String body) {
        JsonObject root = Json.parse(body);
        JsonArray data = Json.array(root, "data");
        for (int i = 0; i < data.size(); i++) {
            if (!data.get(i).isJsonObject()) {
                continue;
            }
            JsonObject entry = data.get(i).getAsJsonObject();
            String url = Json.str(entry, "url");
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
    }

    // ------------------------------------------------------------------
    // 歌词
    // ------------------------------------------------------------------

    @Override
    public String fetchLyric(SongRef song, AuthState auth) throws Exception {
        String url = BASE + "/api/song/lyric?id=" + song.id() + "&lv=-1&kv=-1&tv=-1";
        JsonObject root = Json.parse(Http.get(url, headers(auth)).body());
        StringBuilder lrc = new StringBuilder();

        JsonObject original = Json.obj(root, "lrc");
        String originalText = original == null ? "" : Json.str(original, "lyric");

        JsonObject translated = Json.obj(root, "tlyric");
        String translatedText = translated == null ? "" : Json.str(translated, "lyric");

        if (!originalText.isBlank()) {
            lrc.append(originalText);
        }
        if (!translatedText.isBlank()) {
            lrc.append('\n').append(translatedText);
        }
        return lrc.toString();
    }
}
