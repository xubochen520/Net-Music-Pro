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

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 酷狗音乐适配器。
 *
 * <p>接口选型（均已联网实测）：
 * <ul>
 *   <li><b>搜索</b>：{@code songsearch.kugou.com/song_search_v2} —— 公开接口，返回
 *       SongName / SingerName / Duration / FileHash / Privilege / PayType，支持模糊匹配歌名与歌手；</li>
 *   <li><b>直链</b>：{@code m.kugou.com/app/i/getSongInfo.php?cmd=playInfo}，
 *       免费曲目直接返回 {@code url}；失败时回退 {@code trackercdn.kugou.com/i/v2}；</li>
 *   <li><b>歌词</b>：{@code krcs.kugou.com/search} + {@code lyrics.kugou.com/download}（KRC → LRC）；</li>
 *   <li><b>扫码</b>：{@code login-user.kugou.com} 官方二维码接口。酷狗对扫码请求有额外的风控校验，
 *       若被拒绝，界面会给出明确提示，玩家可改用本地凭据。</li>
 * </ul>
 */
public final class KugouProvider implements IMusicProvider {
    private static final String APP_ID = "1005";
    private static final String CLIENT_VER = "20489";
    private static final String REFERER = "https://www.kugou.com/";
    private static final String QR_APP_ID = "1001";
    private static final String SRC_APP_ID = "2919";
    private static final String QR_PAGE = "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=" + APP_ID + "&";
    /** 官方 Web 端签名盐值。 */
    private static final String SALT = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
    private static final String ANDROID_SALT = "OIlwieks28dk2k092lksi2UIkp";
    private static final String PLAY_KEY_SALT = "57ae12eb6890223e355ccfcb74edf70d";
    private static final String GATEWAY_UA = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi";
    /** 同一次游戏会话内保持相同的设备标识，供创建二维码和轮询使用。 */
    private static final String QR_MID = md5(UUID.randomUUID().toString());

    @Override
    public PlatformId platform() {
        return PlatformId.KUGOU;
    }

    private Map<String, String> headers(@Nullable AuthState auth) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", REFERER);
        headers.put("Origin", "https://www.kugou.com");
        // 酷狗接口要求携带设备指纹，否则部分接口返回 20010
        String mid = auth == null ? QR_MID : auth.cookies().getOrDefault("kg_mid", QR_MID);
        String dfid = auth == null ? "-" : auth.cookies().getOrDefault("kg_dfid", "-");
        String device = "kg_mid=" + mid + "; kg_dfid=" + dfid;
        headers.put("Cookie", auth != null && !auth.cookieHeader().isEmpty()
                ? auth.cookieHeader() + "; " + device : device);
        return headers;
    }

    // ------------------------------------------------------------------
    // 扫码登录
    // ------------------------------------------------------------------

    @Override
    public QrLoginSession startLogin() throws Exception {
        QrLoginSession session = new QrLoginSession(platform());
        Map<String, String> params = qrParams(QR_APP_ID);
        params.put("type", "1");
        params.put("plat", "4");
        params.put("srcappid", SRC_APP_ID);
        params.put("qrcode_txt", QR_PAGE);
        Http.Response response = Http.get(signedQrUrl("/v2/qrcode", params), qrHeaders(params));
        JsonObject json = Json.parse(response.body());
        JsonObject data = Json.obj(json, "data");
        String qrcode = data == null ? "" : Json.firstStr(data, "", "qrcode", "qr_code");
        if (qrcode.isEmpty()) {
            int errorCode = (int) Json.num(json, "error_code", 0);
            session.setStatus(QrLoginSession.Status.FAILED)
                    .setMessage("酷狗二维码获取失败（HTTP " + response.status() + "，错误码 " + errorCode + "）");
            return session;
        }
        session.setSessionKey(qrcode)
                .setQrContent(QR_PAGE + "qrcode=" + UrlEncoder.encode(qrcode))
                .setStatus(QrLoginSession.Status.WAITING)
                .setExpiresAt(System.currentTimeMillis() + 2 * 60 * 1000L)
                .setMessage("等待扫码…");
        return session;
    }

    @Override
    public QrLoginSession pollLogin(QrLoginSession session) throws Exception {
        if (session.finished() || session.sessionKey().isEmpty()) {
            return session;
        }
        if (session.expired()) {
            return session.setStatus(QrLoginSession.Status.EXPIRED).setMessage("二维码已过期，请刷新");
        }
        Map<String, String> params = qrParams(APP_ID);
        params.put("plat", "4");
        params.put("srcappid", SRC_APP_ID);
        params.put("qrcode", session.sessionKey());
        Http.Response response = Http.get(signedQrUrl("/v2/get_userinfo_qrcode", params), qrHeaders(params));
        JsonObject json = Json.parse(response.body());
        JsonObject data = Json.obj(json, "data");
        int errorCode = (int) Json.num(json, "error_code", 0);
        if (!response.ok() || errorCode != 0 || data == null) {
            return session.setStatus(QrLoginSession.Status.FAILED)
                    .setMessage("酷狗登录状态查询失败（错误码 " + errorCode + "）");
        }
        int status = (int) Json.num(data, "status", -1);

        switch (status) {
            case 0 -> session.setStatus(QrLoginSession.Status.EXPIRED).setMessage("二维码已过期，请刷新");
            case 1 -> session.setStatus(QrLoginSession.Status.WAITING).setMessage("等待扫码…");
            case 2 -> session.setStatus(QrLoginSession.Status.SCANNED).setMessage("已扫码，请在手机上确认");
            case 4 -> {
                AuthState auth = new AuthState();
                auth.putAll(response.cookies());
                auth.putCookie("kg_mid", QR_MID);
                auth.putCookie("kg_dfid", "-");
                if (data != null) {
                    auth.setUserId(Json.firstStr(data, "", "userid", "user_id", "uid"));
                    auth.setNickname(Json.firstStr(data, "", "nickname", "username", "nick_name"));
                    auth.setAvatarUrl(Json.firstStr(data, "", "pic", "avatar", "headimg"));
                    String token = Json.firstStr(data, "", "token");
                    if (!token.isEmpty()) {
                        auth.putCookie("token", token);
                    }
                    if (!auth.userId().isEmpty()) {
                        auth.putCookie("userid", auth.userId());
                        auth.putCookie("KugooID", auth.userId());
                    }
                    String vipType = Json.firstStr(data, "", "vip_type", "vipType");
                    if (!vipType.isEmpty()) {
                        auth.setMembership(kugouTier(vipType));
                        auth.putCookie("vip_type", vipType);
                    }
                }
                if (!auth.loggedIn()) {
                    session.setStatus(QrLoginSession.Status.FAILED).setMessage("登录成功但未取到会话凭据");
                    return session;
                }
                session.setAuth(auth).setStatus(QrLoginSession.Status.SUCCESS)
                        .setMessage(auth.displayName().isEmpty() ? "登录成功" : "登录成功：" + auth.displayName());
            }
            default -> session.setStatus(QrLoginSession.Status.FAILED)
                    .setMessage("酷狗返回了未知扫码状态：" + status);
        }
        return session;
    }

    @Override
    public Membership membership(AuthState auth) throws Exception {
        if (auth == null || !auth.loggedIn()) return Membership.UNKNOWN;
        // 扫码接口给出的 vip_type 是登录态的一部分；旧版凭据可能只保存了它，
        // 因此先恢复这一值，再尝试从会员中心刷新过期状态。
        Membership loginTier = kugouTier(auth.cookies().getOrDefault("vip_type", ""));
        if (loginTier == Membership.UNKNOWN) loginTier = auth.membership();
        Membership unionTier = Membership.UNKNOWN;
        // 扫码只保证返回 token/uid；会员产品经 Android 网关签名查询。
        try {
            Map<String, String> params = gatewayParams(auth);
            params.put("busi_type", "concept");
            Map<String, String> gatewayHeaders = gatewayHeaders(auth, params);
            gatewayHeaders.put("x-router", "kugouvip.kugou.com");
            Http.Response unionResponse = Http.get(
                    signedGatewayUrl("/v1/get_union_vip", params), gatewayHeaders);
            JsonObject union = Json.parse(unionResponse.body());
            if (unionResponse.ok() && Json.num(union, "status", 0) == 1
                    && Json.num(union, "error_code", -1) == 0) {
                JsonObject data = Json.obj(union, "data");
                unionTier = unionTier(data);
                if (unionTier == Membership.VIP || unionTier == Membership.SVIP) return unionTier;
            }
        } catch (Exception ignored) {
            // union-vip 不可用时仍可尝试 Web 会员中心。
        }
        // The QR response supplies the account's membership type. Refresh it from the
        // authenticated user endpoint when possible, otherwise preserve the known tier.
        Map<String, String> webHeaders = headers(auth);
        webHeaders.put("Referer", "https://vip.kugou.com/");
        webHeaders.put("X-Requested-With", "XMLHttpRequest");
        Http.Response response = Http.get("https://vip.kugou.com/recharge/roleinfo?n="
                + System.currentTimeMillis(), webHeaders);
        JsonObject root = Json.parse(response.body());
        if (!response.ok() || Json.num(root, "errno", -1) != 0
                || Json.num(root, "error_code", -1) != 0)
            return unionTier == Membership.UNKNOWN ? loginTier : unionTier;
        if (!root.has("vipRemains") || !root.has("role"))
            return unionTier == Membership.UNKNOWN ? loginTier : unionTier;
        int role = (int) Json.firstNum(root, 0, "role", "user_type", "userType");
        if (Json.num(root, "isExpiredMember", 0) != 0) return Membership.FREE;
        String expires = Json.firstStr(root, "", "vipEndTime", "rawVipEndTime");
        if (!expires.isBlank() && !activeUntil(expires)) return Membership.FREE;
        if (role == 6 || role == 11 || role == 13) return Membership.SVIP;
        if (role == 1 || role == 2) return Membership.VIP;
        if ((role == 31 || role == 33) && activeUntil(Json.str(root, "musicEndTime")))
            return Membership.VIP;
        return Membership.FREE;
    }

    private static Membership kugouTier(String type) {
        if ("0".equals(type)) return Membership.FREE;
        if ("11".equals(type) || "21".equals(type)) return Membership.SVIP;
        try { return Integer.parseInt(type) > 0 ? Membership.VIP : Membership.UNKNOWN; }
        catch (NumberFormatException e) { return Membership.UNKNOWN; }
    }

    private static Membership unionTier(@Nullable JsonObject data) {
        if (data == null) return Membership.UNKNOWN;
        if (activeUntil(Json.firstStr(data, "", "su_vip_end_time", "svip_end_time",
                "super_vip_end_time"))) return Membership.SVIP;
        for (var element : Json.array(data, "busi_vip")) {
            if (!element.isJsonObject()) continue;
            JsonObject product = element.getAsJsonObject();
            String type = Json.firstStr(product, "", "product_type", "productType");
            if ("svip".equalsIgnoreCase(type) && activeProduct(product)) return Membership.SVIP;
        }
        String productType = Json.firstStr(data, "", "product_type", "productType");
        if ("svip".equalsIgnoreCase(productType) && activeProduct(data)) return Membership.SVIP;
        Membership classic = kugouTier(Json.firstStr(data, "", "vip_type", "vipType"));
        String vipEnd = Json.firstStr(data, "", "vip_end_time", "vipEndTime");
        if (classic != Membership.UNKNOWN && !vipEnd.isBlank()
                && !activeUntil(vipEnd)) classic = Membership.FREE;
        if (classic == Membership.SVIP || classic == Membership.VIP) return classic;
        if (Json.num(data, "is_vip", 0) > 0 && activeUntil(vipEnd)) return Membership.VIP;
        if (Json.num(data, "m_type", 0) > 0
                && activeUntil(Json.str(data, "m_end_time"))) return Membership.VIP;
        if (Json.num(data, "y_type", 0) > 0
                && activeUntil(Json.str(data, "y_end_time"))) return Membership.VIP;
        return classic;
    }

    private static boolean activeProduct(JsonObject product) {
        return activeUntil(Json.firstStr(product, "", "end_time", "endtime",
                "expire_time", "vip_end_time", "vipEndTime"));
    }

    private static boolean activeUntil(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            long end = Long.parseLong(raw);
            if (end > 10_000_000_000L) end /= 1000;
            return end > System.currentTimeMillis() / 1000;
        } catch (NumberFormatException ignored) { }
        try { return Instant.parse(raw).isAfter(Instant.now()); }
        catch (RuntimeException ignored) { }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .toInstant(ZoneOffset.ofHours(8)).isAfter(Instant.now());
        } catch (RuntimeException ignored) { }
        try { return LocalDate.parse(raw).plusDays(1).atStartOfDay()
                .toInstant(ZoneOffset.ofHours(8)).isAfter(Instant.now()); }
        catch (RuntimeException ignored) { return false; }
    }

    private static Map<String, String> gatewayParams(AuthState auth) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("dfid", auth.cookies().getOrDefault("kg_dfid", "-"));
        params.put("mid", auth.cookies().getOrDefault("kg_mid", QR_MID));
        params.put("uuid", "-");
        params.put("appid", APP_ID);
        params.put("clientver", CLIENT_VER);
        params.put("clienttime", Long.toString(System.currentTimeMillis() / 1000));
        params.put("token", auth.cookies().getOrDefault("token", ""));
        params.put("userid", auth.userId());
        return params;
    }

    private static Map<String, String> gatewayHeaders(AuthState auth, Map<String, String> params) {
        Map<String, String> result = headersForGateway(auth);
        result.put("User-Agent", GATEWAY_UA);
        result.put("dfid", params.get("dfid"));
        result.put("mid", params.get("mid"));
        result.put("clienttime", params.get("clienttime"));
        result.put("kg-rc", "1");
        result.put("kg-thash", "5d816a0");
        result.put("kg-rec", "1");
        result.put("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F");
        return result;
    }

    private static Map<String, String> headersForGateway(AuthState auth) {
        Map<String, String> result = new HashMap<>();
        result.put("Referer", "https://vip.kugou.com/");
        result.put("Cookie", auth.cookieHeader() + "; kg_mid="
                + auth.cookies().getOrDefault("kg_mid", QR_MID) + "; kg_dfid="
                + auth.cookies().getOrDefault("kg_dfid", "-"));
        return result;
    }

    private static String signedGatewayUrl(String path, Map<String, String> params) {
        StringBuilder signing = new StringBuilder(ANDROID_SALT);
        params.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> signing.append(entry.getKey()).append('=').append(entry.getValue()));
        String signature = md5(signing.append(ANDROID_SALT).toString());
        StringBuilder url = new StringBuilder("https://gateway.kugou.com").append(path).append('?');
        params.forEach((key, value) -> url.append(key).append('=').append(UrlEncoder.encode(value)).append('&'));
        return url.append("signature=").append(signature).toString();
    }

    private static Map<String, String> qrParams(String appId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appid", appId);
        params.put("clientver", CLIENT_VER);
        params.put("clienttime", Long.toString(System.currentTimeMillis() / 1000));
        params.put("dfid", "-");
        params.put("mid", QR_MID);
        params.put("uuid", "-");
        return params;
    }

    private static Map<String, String> qrHeaders(Map<String, String> params) {
        Map<String, String> result = new HashMap<>();
        result.put("Referer", REFERER);
        result.put("dfid", params.get("dfid"));
        result.put("mid", params.get("mid"));
        result.put("clienttime", params.get("clienttime"));
        return result;
    }

    private static String signedQrUrl(String path, Map<String, String> params) {
        String joined = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .reduce("", String::concat);
        StringBuilder url = new StringBuilder("https://login-user.kugou.com").append(path).append('?');
        params.forEach((key, value) -> url.append(key).append('=').append(UrlEncoder.encode(value)).append('&'));
        return url.append("signature=").append(md5(SALT + joined + SALT)).toString();
    }

    // ------------------------------------------------------------------
    // 搜索
    // ------------------------------------------------------------------

    @Override
    public List<SongRef> search(String keyword, int limit, AuthState auth) throws Exception {
        int pageSize = Math.max(1, Math.min(limit, 50));
        String userId = auth == null || auth.userId().isBlank() ? "-1" : auth.userId();
        String token = auth == null ? "" : auth.cookies().getOrDefault("token", "");
        String mid = auth == null ? QR_MID : auth.cookies().getOrDefault("kg_mid", QR_MID);
        String url = "https://songsearch.kugou.com/song_search_v2?keyword=" + UrlEncoder.encode(keyword)
                     + "&page=1&pagesize=" + pageSize
                     + "&userid=" + UrlEncoder.encode(userId)
                     + "&clientver=2000&platform=WebFilter&tag=em&filter=2"
                     + "&iscorrection=1&privilege_filter=0&filter_ver=2&appid=1014"
                     + "&token=" + UrlEncoder.encode(token) + "&mid=" + UrlEncoder.encode(mid);
        JsonObject root = Json.parse(Http.get(url, headers(auth)).body());
        JsonArray lists = Json.array(Json.obj(root, "data"), "lists");

        List<SongRef> result = new ArrayList<>();
        for (int i = 0; i < lists.size(); i++) {
            if (lists.get(i).isJsonObject()) {
                result.add(toSongRef(lists.get(i).getAsJsonObject()));
            }
        }
        return enrichPrivileges(result, auth);
    }

    /** 旧搜索接口会把付费字段清零；播放权限接口按 hash 返回真实权益。 */
    private List<SongRef> enrichPrivileges(List<SongRef> songs, @Nullable AuthState auth) {
        if (songs.isEmpty()) return songs;
        JsonArray resources = new JsonArray();
        for (SongRef song : songs) {
            if (song.id().isEmpty()) continue;
            JsonObject resource = new JsonObject();
            resource.addProperty("type", "audio");
            resource.addProperty("page_id", 0);
            resource.addProperty("hash", song.id());
            // 播放接口只传 hash；权限查询必须采用相同的 album_id=0，
            // 否则某些合辑会返回不同的专辑授权结果，VIP 标签会漏报。
            resource.addProperty("album_id", 0);
            resources.add(resource);
        }
        if (resources.isEmpty()) return songs;
        JsonObject request = new JsonObject();
        request.addProperty("appid", Integer.parseInt(APP_ID));
        request.addProperty("area_code", 1);
        request.addProperty("behavior", "play");
        request.addProperty("clientver", Integer.parseInt(CLIENT_VER));
        request.add("resource", resources);
        JsonArray qualities = new JsonArray();
        qualities.add("128");
        request.add("qualities", qualities);
        Map<String, String> requestHeaders = headers(auth);
        requestHeaders.put("x-router", "media.store.kugou.com");
        try {
            Http.Response response = Http.postJson(
                    "https://gateway.kugou.com/v2/get_res_privilege/lite",
                    request.toString(), requestHeaders);
            JsonObject root = Json.parse(response.body());
            if (!response.ok() || Json.num(root, "error_code", -1) != 0) return songs;
            Map<String, JsonObject> byHash = new HashMap<>();
            for (var element : Json.array(root, "data")) {
                if (element.isJsonObject()) {
                    JsonObject info = element.getAsJsonObject();
                    String hash = Json.str(info, "hash");
                    if (!hash.isEmpty()) byHash.put(hash, info);
                }
            }
            List<SongRef> enriched = new ArrayList<>(songs.size());
            for (SongRef song : songs) {
                JsonObject info = byHash.get(song.id());
                if (info == null) { enriched.add(song); continue; }
                int privilege = (int) Json.num(info, "privilege", 0);
                int payType = (int) Json.num(info, "pay_type", 0);
                int pkgPrice = (int) Json.num(info, "pkg_price", 0);
                boolean vip = song.vip() || privilege >= 10;
                Map<String, String> extras = new LinkedHashMap<>(song.extras());
                extras.put("privilege", Integer.toString(privilege));
                extras.put("payType", Integer.toString(payType));
                enriched.add(new SongRef(platform(), song.id(), song.title(), song.artists(), song.album(),
                        song.durationSeconds(), vip, payType, extras));
            }
            return enriched;
        } catch (Exception ignored) {
            // 网络波动时仍展示搜索结果，并保留搜索响应自身的付费标记。
            return songs;
        }
    }

    private SongRef toSongRef(JsonObject song) {
        String hash = Json.firstStr(song, "", "FileHash", "filehash", "hash");
        String hqHash = Json.firstStr(song, "", "HQFileHash");
        String title = clean(Json.firstStr(song, "", "SongName", "songname"));
        List<String> artists = Json.splitArtists(clean(Json.firstStr(song, "", "SingerName", "singername")));
        String album = clean(Json.firstStr(song, "", "AlbumName", "album_name"));

        int duration = (int) Json.firstNum(song, 0, "Duration", "timelen");
        if (duration > 10_000) {
            duration = duration / 1000;
        }

        int privilege = (int) Json.firstNum(song, 0, "Privilege", "privilege");
        int payType = (int) Json.firstNum(song, 0, "PayType", "pay_type");
        boolean vip = privilege >= 10;

        long fileSize = Json.firstNum(song, 0, "FileSize", "filesize");
        long hqFileSize = Json.firstNum(song, 0, "HQFileSize");
        int hqPrivilege = (int) Json.firstNum(song, 0, "HQPrivilege");

        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("hash", hash);
        extras.put("albumId", Json.firstStr(song, "", "AlbumID", "album_id"));
        extras.put("albumAudioId", Json.firstStr(song, "", "MixSongID", "EMixSongID", "AlbumAudioID"));
        extras.put("fileSize", String.valueOf(fileSize));
        extras.put("privilege", String.valueOf(privilege));
        extras.put("payType", String.valueOf(payType));
        if (!hqHash.isEmpty() && hqPrivilege == 0) {
            extras.put("hqHash", hqHash);
            extras.put("hqFileSize", String.valueOf(hqFileSize));
        }

        return new SongRef(platform(), hash, title, artists, album, duration, vip, payType, extras);
    }

    private static String clean(String text) {
        return text.replace("<em>", "").replace("</em>", "").trim();
    }

    // ------------------------------------------------------------------
    // 直链
    // ------------------------------------------------------------------

    @Override
    public String resolveStreamUrl(SongRef song, AuthState auth) throws Exception {
        String hash = song.extra("hash").isEmpty() ? song.id() : song.extra("hash");
        List<String> candidates = new ArrayList<>();
        if (!song.extra("hqHash").isEmpty()) {
            candidates.add(song.extra("hqHash"));
        }
        candidates.add(hash);

        for (String candidate : candidates) {
            String resolved = mobilePlayUrl(candidate, song.extra("albumId"), auth);
            if (!resolved.isBlank()) return resolved;
            if (auth != null && auth.loggedIn()) {
                resolved = gatewayPlayUrl(candidate, song, auth);
                if (!resolved.isBlank()) return resolved;
            }
        }
        return "";
    }

    private String mobilePlayUrl(String hash, String albumId, @Nullable AuthState auth) {
        String url = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=" + UrlEncoder.encode(hash)
                + "&key=" + md5(hash + "kgcloud")
                + "&album_id=" + UrlEncoder.encode(albumId.isBlank() ? "0" : albumId)
                + "&pid=1&forceDown=0&vip=" + (auth != null && auth.vip() ? "1" : "65530");
        if (auth != null && !auth.userId().isBlank()) {
            url += "&userid=" + UrlEncoder.encode(auth.userId());
            url += "&token=" + UrlEncoder.encode(auth.cookies().getOrDefault("token", ""));
        }
        try {
            Map<String, String> requestHeaders = headers(auth);
            requestHeaders.put("Referer", "https://m.kugou.com/");
            JsonObject response = Json.parse(Http.get(url, requestHeaders).body());
            return playableUrl(response);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String gatewayPlayUrl(String hash, SongRef song, AuthState auth) {
        try {
            Map<String, String> params = gatewayParams(auth);
            String lowerHash = hash.toLowerCase(java.util.Locale.ROOT);
            String mid = params.get("mid");
            params.put("album_id", Long.toString(parseLong(song.extra("albumId"), 0)));
            params.put("area_code", "1");
            params.put("hash", lowerHash);
            params.put("ssa_flag", "is_fromtrack");
            params.put("version", "11430");
            params.put("quality", "128");
            params.put("album_audio_id", Long.toString(parseLong(song.extra("albumAudioId"), 0)));
            params.put("behavior", "play");
            params.put("pid", "2");
            params.put("cmd", "26");
            params.put("pidversion", "3001");
            params.put("IsFreePart", auth.vip() ? "0" : "1");
            params.put("cdnBackup", "1");
            params.put("module", "");
            params.put("key", md5(lowerHash + PLAY_KEY_SALT + APP_ID + mid + auth.userId()));
            Map<String, String> requestHeaders = gatewayHeaders(auth, params);
            requestHeaders.put("x-router", "trackercdn.kugou.com");
            JsonObject response = Json.parse(Http.get(signedGatewayUrl("/v5/url", params), requestHeaders).body());
            return playableUrl(response);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String playableUrl(JsonObject response) {
        if (Json.num(response, "status", 0) != 1) return "";
        JsonObject data = Json.obj(response, "data");
        if (trial(response) || data != null && trial(data)) return "";
        for (JsonObject object : new JsonObject[]{response, data}) {
            if (object == null) continue;
            for (String field : new String[]{"url", "play_url", "backupUrl", "backup_url", "play_backup_url"}) {
                if (!object.has(field) || object.get(field).isJsonNull()) continue;
                var value = object.get(field);
                if (value.isJsonPrimitive() && value.getAsString().startsWith("http")) return value.getAsString();
                if (value.isJsonArray()) {
                    for (var item : value.getAsJsonArray()) {
                        if (item.isJsonPrimitive() && item.getAsString().startsWith("http")) return item.getAsString();
                    }
                }
            }
        }
        return "";
    }

    private static boolean trial(JsonObject object) {
        for (String field : new String[]{"is_free_part", "isFreePart", "trial", "is_trial", "isTrial"}) {
            if (object.has(field) && (Json.bool(object, field, false)
                    || Json.num(object, field, 0) > 0)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 歌词
    // ------------------------------------------------------------------

    @Override
    public String fetchLyric(SongRef song, AuthState auth) throws Exception {
        String hash = song.extra("hash").isEmpty() ? song.id() : song.extra("hash");
        String searchUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&hash=" + hash;
        JsonObject search = Json.parse(Http.get(searchUrl, headers(auth)).body());
        JsonArray candidates = Json.array(search, "candidates");
        if (candidates.size() == 0) {
            return "";
        }
        JsonObject candidate = candidates.get(0).getAsJsonObject();
        String id = Json.str(candidate, "id");
        String accessKey = Json.str(candidate, "accesskey");
        if (id.isEmpty() || accessKey.isEmpty()) {
            return "";
        }
        String download = "https://lyrics.kugou.com/download?ver=1&client=pc&id=" + id
                          + "&accesskey=" + accessKey + "&fmt=lrc&charset=utf8";
        JsonObject lyricJson = Json.parse(Http.get(download, headers(auth)).body());
        String content = Json.str(lyricJson, "content");
        if (content.isEmpty()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return content;
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    public static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(text);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
