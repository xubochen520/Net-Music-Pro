package com.yunpu.yunpumusic.api.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.IMusicProvider;
import com.yunpu.yunpumusic.api.Membership;
import com.yunpu.yunpumusic.api.Json;
import com.yunpu.yunpumusic.api.PlatformId;
import com.yunpu.yunpumusic.api.QrLoginSession;
import com.yunpu.yunpumusic.api.SongRef;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ 音乐适配器。
 *
 * <p>接口选型（均已联网实测）：
 * <ul>
 *   <li><b>搜索</b>：{@code music.adaptor.SearchAdaptor/do_search_v2} 综合搜索；
 *       未登录时 QQ 会返回空列表，因此登录后才会真正拿到结果。
 *       备用入口 {@code music.search.SearchCgiService/DoSearchForQQMusicMobile}；</li>
 *   <li><b>直链</b>：{@code music.vkey.GetVkey/UrlGetVkey}，请求体带 {@code filename: M500<media_mid>.mp3}；</li>
 *   <li><b>歌词</b>：{@code music.musichallSong.PlayLyricInfo/GetPlayLyricInfo}（返回 Base64 的 LRC）；</li>
 *   <li><b>扫码</b>：QQ 互联 {@code ptqrshow} + {@code ptqrlogin}，ptqrtoken 使用 hash33 算法。</li>
 * </ul>
 */
public final class QqProvider implements IMusicProvider {
    /** QQ 互联分配给 QQ 音乐的应用 ID。 */
    private static final String APP_ID = "716027609";
    private static final String PT_3RD_AID = "100497308";
    private static final String REFERER = "https://y.qq.com/";
    private static final String CGI = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String GUID = "10000";

    private static final Pattern QRSIG_PATTERN = Pattern.compile("qrsig=([^;]+)");
    private static final Pattern JUMP_URL_PATTERN = Pattern.compile("'(https?://[^']+)'");

    @Override
    public PlatformId platform() {
        return PlatformId.QQ;
    }

    private Map<String, String> headers(@Nullable AuthState auth) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", REFERER);
        headers.put("Origin", "https://y.qq.com");
        if (auth != null && !auth.cookieHeader().isEmpty()) {
            headers.put("Cookie", auth.cookieHeader());
        }
        return headers;
    }

    /** 统一的 CGI 请求体：{@code {"comm":{...},"req_1":{...}}}。 */
    private static JsonObject comm(@Nullable AuthState auth) {
        JsonObject comm = new JsonObject();
        comm.addProperty("ct", 24);
        comm.addProperty("cv", 0);
        comm.addProperty("format", "json");
        comm.addProperty("guid", GUID);
        String uin = auth == null ? "" : auth.userId();
        comm.addProperty("uin", uin.isEmpty() ? "0" : uin);
        if (auth != null) {
            String authst = auth.tokens().getOrDefault("authst", auth.cookies().getOrDefault("qm_keyst", ""));
            if (!authst.isEmpty()) {
                comm.addProperty("authst", authst);
            }
            String gtk = auth.tokens().getOrDefault("g_tk", "");
            if (!gtk.isEmpty()) {
                comm.addProperty("g_tk", gtk);
            }
        }
        return comm;
    }

    private JsonObject cgi(String module, String method, JsonObject param, @Nullable AuthState auth) throws Exception {
        JsonObject body = new JsonObject();
        body.add("comm", comm(auth));
        JsonObject request = new JsonObject();
        request.addProperty("module", module);
        request.addProperty("method", method);
        request.add("param", param);
        body.add("req_1", request);

        Http.Response response = Http.postJson(CGI, body.toString(), headers(auth));
        JsonObject root = Json.parse(response.body());
        JsonObject request1 = Json.obj(root, "req_1");
        return request1 == null ? new JsonObject() : request1;
    }

    @Override
    public Membership membership(AuthState auth) throws Exception {
        if (auth == null || !auth.loggedIn()) return Membership.UNKNOWN;
        JsonObject response = cgi("VipLogin.VipLoginInter", "vip_login_base", new JsonObject(), auth);
        if (Json.num(response, "code", -1) != 0) return Membership.UNKNOWN;
        JsonObject identity = Json.obj(Json.obj(response, "data"), "identity");
        if (identity == null) return Membership.UNKNOWN;
        if (enabled(identity, "HugeVip")) return Membership.SVIP;
        if (enabled(identity, "LMFlag")) return Membership.VIP;
        return identity.has("HugeVip") && identity.has("LMFlag")
                ? Membership.FREE : Membership.UNKNOWN;
    }

    private static boolean enabled(JsonObject object, String key) {
        String value = Json.str(object, key);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    // ------------------------------------------------------------------
    // 扫码登录
    // ------------------------------------------------------------------

    @Override
    public QrLoginSession startLogin() throws Exception {
        QrLoginSession session = new QrLoginSession(platform());
        String url = "https://ssl.ptlogin2.qq.com/ptqrshow?appid=" + APP_ID
                     + "&e=2&l=M&s=3&d=72&v=4&t=" + Math.random()
                     + "&daid=383&pt_3rd_aid=" + PT_3RD_AID;

        Http.Response response = Http.get(url, Map.of("Referer", "https://xui.ptlogin2.qq.com/"));
        String setCookie = response.headers().getOrDefault("set-cookie", List.of()).stream()
                .filter(value -> value.contains("qrsig="))
                .findFirst().orElse("");
        Matcher matcher = QRSIG_PATTERN.matcher(setCookie);
        String qrsig = matcher.find() ? matcher.group(1) : "";

        if (qrsig.isEmpty()) {
            session.setStatus(QrLoginSession.Status.FAILED)
                    .setMessage("未能从 QQ 互联取得二维码（qrsig 缺失）");
            return session;
        }
        // QQ 返回的是图片二维码，这里给出等价的登录链接，交由界面自绘二维码
        session.setSessionKey(qrsig)
                .setPollToken(qrsig)
                .setQrContent("https://ssl.ptlogin2.qq.com/ptqrshow?appid=" + APP_ID
                              + "&e=2&l=M&s=3&d=72&v=4&daid=383&pt_3rd_aid=" + PT_3RD_AID)
                .setStatus(QrLoginSession.Status.WAITING)
                .setExpiresAt(System.currentTimeMillis() + 2 * 60 * 1000L)
                .setMessage("等待扫码…");
        return session;
    }

    @Override
    public QrLoginSession pollLogin(QrLoginSession session) throws Exception {
        if (session.finished() || session.pollToken().isEmpty()) {
            return session;
        }
        if (session.expired()) {
            return session.setStatus(QrLoginSession.Status.EXPIRED).setMessage("二维码已过期，请刷新");
        }

        String url = "https://ssl.ptlogin2.qq.com/ptqrlogin?u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump"
                     + "&ptqrtoken=" + hash33(session.pollToken())
                     + "&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052&action=0-0-" + System.currentTimeMillis()
                     + "&js_ver=20102616&js_type=1&login_sig=&pt_uistyle=40&aid=" + APP_ID
                     + "&daid=383&pt_3rd_aid=" + PT_3RD_AID + "&has_onekey=1&";
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://xui.ptlogin2.qq.com/");
        headers.put("Cookie", "qrsig=" + session.pollToken());

        Http.Response response = Http.get(url, headers);
        String body = response.body();

        if (body.contains("二维码未失效")) {
            return session.setStatus(QrLoginSession.Status.WAITING).setMessage("等待扫码…");
        }
        if (body.contains("二维码认证中")) {
            return session.setStatus(QrLoginSession.Status.SCANNED).setMessage("已扫码，请在手机上确认");
        }
        if (body.contains("二维码已失效")) {
            return session.setStatus(QrLoginSession.Status.EXPIRED).setMessage("二维码已过期，请刷新");
        }
        if (body.contains("登录成功")) {
            AuthState auth = new AuthState();
            auth.putAll(response.cookies());
            Matcher linkMatcher = JUMP_URL_PATTERN.matcher(body);
            if (linkMatcher.find()) {
                followLoginJump(auth, linkMatcher.group(1));
            }
            if (!auth.loggedIn()) {
                session.setStatus(QrLoginSession.Status.FAILED).setMessage("登录成功但未取到会话凭据");
                return session;
            }
            session.setAuth(auth).setStatus(QrLoginSession.Status.SUCCESS).setMessage("登录成功");
            return session;
        }
        return session.setStatus(QrLoginSession.Status.WAITING).setMessage("等待扫码…");
    }

    /** 跟随 ptlogin 的跳转链接，尽量取到 p_skey / qm_keyst 等后续接口所需的 cookie。 */
    private void followLoginJump(AuthState auth, String jumpUrl) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", "https://xui.ptlogin2.qq.com/");
            headers.put("Cookie", auth.cookieHeader());
            Http.Response response = Http.get(jumpUrl, headers);
            auth.putAll(response.cookies());

            Matcher uinMatcher = Pattern.compile("(?:\\?|&)uin=(.+?)&service").matcher(jumpUrl);
            if (uinMatcher.find()) {
                auth.setUserId(uinMatcher.group(1));
            }
            if (!auth.userId().isEmpty()) {
                auth.putCookie("uin", auth.userId());
                auth.putCookie("qqmusic_uin", auth.userId());
            }
            String musicKey = auth.cookies().getOrDefault("qm_keyst", auth.cookies().getOrDefault("p_skey", ""));
            if (!musicKey.isEmpty()) {
                auth.putCookie("qqmusic_key", musicKey);
                auth.putToken("authst", musicKey);
                auth.putToken("g_tk", String.valueOf(hash33(musicKey, 5381)));
            }
        } catch (Exception ignored) {
            // 取不到后续 cookie 时搜索会返回空列表，界面会提示重新登录
        }
    }

    /**
     * ptqrtoken / g_tk 使用的 hash33 算法。
     *
     * <pre>
     * int e = 0;
     * for (char c : text) e += (e &lt;&lt; 5) + c;
     * return e &amp; 2147483647;
     * </pre>
     */
    static long hash33(String text) {
        return hash33(text, 0);
    }

    static long hash33(String text, int seed) {
        long hash = seed;
        for (int i = 0; i < text.length(); i++) {
            hash += (hash << 5) + text.charAt(i);
            hash &= 0x7FFFFFFFL;
        }
        return hash & 0x7FFFFFFFL;
    }

    // ------------------------------------------------------------------
    // 搜索
    // ------------------------------------------------------------------

    @Override
    public List<SongRef> search(String keyword, int limit, AuthState auth) throws Exception {
        int count = Math.max(1, Math.min(limit, 50));
        List<SongRef> result = searchAdaptor(keyword, count, auth);
        if (result.isEmpty()) {
            result = searchByType(keyword, count, auth);
        }
        return result;
    }

    /** 综合搜索（当前 QQ 客户端使用的主入口）。 */
    private List<SongRef> searchAdaptor(String keyword, int count, AuthState auth) throws Exception {
        JsonObject param = new JsonObject();
        param.addProperty("searchid", String.valueOf(System.currentTimeMillis() % 1_000_000));
        param.addProperty("search_type", 100);
        param.addProperty("page_num", count);
        param.addProperty("query", keyword);
        param.addProperty("page_id", 1);
        param.addProperty("highlight", true);
        param.addProperty("grp", true);
        param.add("selectors", new JsonObject());
        param.add("vec_selectors", new JsonArray());

        JsonObject data = Json.obj(cgi("music.adaptor.SearchAdaptor", "do_search_v2", param, auth), "data");
        JsonObject body = Json.obj(data, "body");
        if (body == null) {
            return List.of();
        }
        // 综合搜索里歌曲在 item_song，旧版为 song
        JsonArray songs = Json.array(Json.obj(body, "item_song"), "items");
        if (songs.size() == 0) {
            songs = Json.array(Json.obj(body, "song"), "list");
        }
        return mapSongs(songs);
    }

    /** 类型搜索（备用入口）。 */
    private List<SongRef> searchByType(String keyword, int count, AuthState auth) throws Exception {
        JsonObject param = new JsonObject();
        param.addProperty("searchid", String.valueOf(System.currentTimeMillis() % 1_000_000));
        param.addProperty("query", keyword);
        param.addProperty("search_type", 0);
        param.addProperty("num_per_page", count);
        param.addProperty("page_num", 1);
        param.addProperty("highlight", true);
        param.addProperty("grp", true);
        param.add("selectors", new JsonObject());
        param.add("vec_selectors", new JsonArray());

        JsonObject data = Json.obj(cgi("music.search.SearchCgiService", "DoSearchForQQMusicMobile", param, auth), "data");
        JsonObject body = Json.obj(data, "body");
        if (body == null) {
            return List.of();
        }
        JsonArray songs = Json.array(body, "item_song");
        if (songs.size() == 0) {
            songs = Json.array(Json.obj(body, "song"), "list");
        }
        return mapSongs(songs);
    }

    private List<SongRef> mapSongs(JsonArray songs) {
        List<SongRef> result = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).isJsonObject()) {
                result.add(toSongRef(songs.get(i).getAsJsonObject()));
            }
        }
        return result;
    }

    private SongRef toSongRef(JsonObject song) {
        // 综合搜索的字段外层包了一层 track_info
        JsonObject track = Json.obj(song, "track_info");
        if (track == null) {
            track = song;
        }
        JsonObject basic = Json.obj(track, "basic_info");
        if (basic == null) {
            basic = track;
        }
        JsonObject file = Json.obj(track, "file");
        if (file == null) {
            file = Json.obj(song, "file");
        }

        String mid = Json.firstStr(track, "", "mid", "songmid");
        if (mid.isEmpty()) {
            mid = Json.firstStr(song, "", "mid", "songmid");
        }
        String title = Json.firstStr(basic, "", "title", "name");
        if (title.isEmpty()) {
            title = Json.str(song, "title");
        }

        List<String> artists = new ArrayList<>();
        JsonArray singerArray = Json.array(basic, "singer");
        if (singerArray.size() == 0) {
            singerArray = Json.array(song, "singer");
        }
        for (int i = 0; i < singerArray.size(); i++) {
            if (singerArray.get(i).isJsonObject()) {
                String name = Json.str(singerArray.get(i).getAsJsonObject(), "name");
                if (!name.isBlank()) {
                    artists.add(name);
                }
            }
        }
        if (artists.isEmpty()) {
            artists.addAll(Json.splitArtists(Json.str(song, "singerName")));
        }

        String album = "";
        JsonObject albumJson = Json.obj(basic, "album");
        if (albumJson == null) {
            albumJson = Json.obj(track, "album");
        }
        if (albumJson != null) {
            album = Json.firstStr(albumJson, "", "name", "title");
        }

        // pay_month is the explicit Green Diamond / subscription flag.
        // pay_play alone can also mean a separately purchased song or album.
        JsonObject pay = Json.obj(track, "pay");
        if (pay == null) {
            pay = Json.obj(song, "pay");
        }
        int payPlay = pay == null ? 0 : (int) Json.num(pay, "pay_play", 0);
        int payAlbum = pay == null ? 0 : (int) Json.num(pay, "pay_album", 0);
        int payMonth = pay == null ? 0 : (int) Json.num(pay, "pay_month", 0);
        boolean vip = payMonth == 1 || (pay != null && !pay.has("pay_month")
                && payPlay == 1 && payAlbum == 0);

        String mediaMid = file == null ? "" : Json.firstStr(file, "", "media_mid");
        if (mediaMid.isEmpty()) {
            mediaMid = mid;
        }
        int duration = (int) Json.firstNum(basic, 0, "interval");
        if (duration == 0) {
            duration = (int) Json.num(song, "interval", 0);
        }

        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("mid", mid);
        extras.put("mediaMid", mediaMid);
        extras.put("payPlay", String.valueOf(payPlay));
        extras.put("payMonth", String.valueOf(payMonth));
        extras.put("payAlbum", String.valueOf(payAlbum));
        if (file != null) {
            extras.put("fileSize", String.valueOf(Json.firstNum(file, 0, "size_128mp3", "size_320mp3")));
        }

        return new SongRef(platform(), mid, title, artists, album, duration, vip, payPlay, extras);
    }

    // ------------------------------------------------------------------
    // 直链
    // ------------------------------------------------------------------

    @Override
    public String resolveStreamUrl(SongRef song, AuthState auth) throws Exception {
        String mid = song.extra("mid").isEmpty() ? song.id() : song.extra("mid");
        String mediaMid = song.extra("mediaMid").isEmpty() ? mid : song.extra("mediaMid");

        // 依次尝试不同码率的文件名，返回第一个可用的直链
        String[] prefixes = {"M500", "M800", "C400"};
        for (String prefix : prefixes) {
            String filename = prefix + mediaMid + ".mp3";
            JsonObject param = new JsonObject();
            param.addProperty("guid", GUID);
            param.addProperty("songmid", mid);
            param.addProperty("songtype", 0);
            JsonArray filenames = new JsonArray();
            filenames.add(filename);
            param.add("filename", filenames);
            param.addProperty("ctx", 0);
            param.addProperty("uin", auth == null || auth.userId().isEmpty() ? "0" : auth.userId());

            JsonObject data = Json.obj(cgi("music.vkey.GetVkey", "UrlGetVkey", param, auth), "data");
            if (data == null) {
                continue;
            }
            String sip = "";
            JsonArray sipArray = Json.array(data, "sip");
            for (JsonElement element : sipArray) {
                if (element.isJsonPrimitive() && !element.getAsString().isBlank()) {
                    sip = element.getAsString();
                    break;
                }
            }
            JsonArray midUrlInfo = Json.array(data, "midurlinfo");
            for (int i = 0; i < midUrlInfo.size(); i++) {
                if (!midUrlInfo.get(i).isJsonObject()) {
                    continue;
                }
                String purl = Json.str(midUrlInfo.get(i).getAsJsonObject(), "purl");
                if (!purl.isBlank()) {
                    return (sip.isEmpty() ? "https://dl.stream.qqmusic.qq.com/" : sip) + purl;
                }
            }
        }

        // QQ 音乐从 2024 年起收紧了下发策略：vkey 接口对匿名请求返回 code=10006，
        // 需要携带登录后的 authst 且曲目具备版权授权。这里再做一次覆盖更多文件名的降级尝试，
        // 仍然失败时返回空串，由上层界面提示「需要登录 / 会员」。
        JsonObject fallbackData = Json.obj(
                cgi("music.vkey.GetVkey", "UrlGetVkey", fallbackParam(mid, mediaMid), auth), "data");
        if (fallbackData != null) {
            String sip = "";
            JsonArray sipArray = Json.array(fallbackData, "sip");
            for (JsonElement element : sipArray) {
                if (element.isJsonPrimitive() && !element.getAsString().isBlank()) {
                    sip = element.getAsString();
                    break;
                }
            }
            JsonArray midUrlInfo = Json.array(fallbackData, "midurlinfo");
            for (int i = 0; i < midUrlInfo.size(); i++) {
                if (!midUrlInfo.get(i).isJsonObject()) {
                    continue;
                }
                String purl = Json.str(midUrlInfo.get(i).getAsJsonObject(), "purl");
                if (!purl.isBlank()) {
                    return (sip.isEmpty() ? "https://dl.stream.qqmusic.qq.com/" : sip) + purl;
                }
            }
        }
        YunpuMusic.debugOnce("QQ 直链未下发（通常需要登录或会员）：" + mid, null);
        return "";
    }

    /** 无鉴权降级尝试时使用的参数（部分曲目在未登录状态下仍会下发 128k 试听）。 */
    private JsonObject fallbackParam(String mid, String mediaMid) {
        JsonObject param = new JsonObject();
        param.addProperty("guid", GUID);
        param.addProperty("songmid", mid);
        param.addProperty("songtype", 0);
        JsonArray filenames = new JsonArray();
        filenames.add("C400" + mediaMid + ".m4a");
        filenames.add("M500" + mediaMid + ".mp3");
        param.add("filename", filenames);
        param.addProperty("ctx", 0);
        param.addProperty("uin", "0");
        return param;
    }

    // ------------------------------------------------------------------
    // 歌词
    // ------------------------------------------------------------------

    @Override
    public String fetchLyric(SongRef song, AuthState auth) throws Exception {
        String mid = song.extra("mid").isEmpty() ? song.id() : song.extra("mid");
        JsonObject param = new JsonObject();
        param.addProperty("songMID", mid);
        param.addProperty("songID", 0);
        param.addProperty("refer", "y.qq.com");

        JsonObject data = Json.obj(cgi("music.musichallSong.PlayLyricInfo", "GetPlayLyricInfo", param, auth), "data");
        if (data == null) {
            return "";
        }
        String lyric = decodeBase64(Json.str(data, "lyric"));
        String trans = decodeBase64(Json.str(data, "trans"));
        if (lyric.isEmpty()) {
            return "";
        }
        return trans.isEmpty() ? lyric : lyric + "\n" + trans;
    }

    private String decodeBase64(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
