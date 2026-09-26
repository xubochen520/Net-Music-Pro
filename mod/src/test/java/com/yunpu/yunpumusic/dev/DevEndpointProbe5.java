package com.yunpu.yunpumusic.dev;

import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.UrlEncoder;
import com.yunpu.yunpumusic.api.provider.KugouProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 开发期联调工具：验证最终接口（酷狗搜索 / 直链，QQ 搜索）。不参与模组运行。 */
public final class DevEndpointProbe5 {
    private static final java.io.PrintStream OUT = new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String keyword = args.length > 0 ? args[0] : "起风了";
        String encoded = UrlEncoder.encode(keyword);

        OUT.println("############ KUGOU SEARCH (song_search_v2) ############");
        Http.Response search = Http.get("http://songsearch.kugou.com/song_search_v2?keyword=" + encoded
                                        + "&page=1&pagesize=3", Map.of("Referer", "https://www.kugou.com/"));
        OUT.println("status=" + search.status());
        com.google.gson.JsonObject root = com.yunpu.yunpumusic.api.Json.parse(search.body());
        com.google.gson.JsonArray lists = com.yunpu.yunpumusic.api.Json.array(
                com.yunpu.yunpumusic.api.Json.obj(root, "data"), "lists");
        for (int i = 0; i < lists.size(); i++) {
            com.google.gson.JsonObject song = lists.get(i).getAsJsonObject();
            OUT.println("  - " + com.yunpu.yunpumusic.api.Json.str(song, "SongName")
                        + " | " + com.yunpu.yunpumusic.api.Json.str(song, "SingerName")
                        + " | dur=" + com.yunpu.yunpumusic.api.Json.num(song, "Duration", 0)
                        + " | hash=" + com.yunpu.yunpumusic.api.Json.str(song, "FileHash")
                        + " | Privilege=" + com.yunpu.yunpumusic.api.Json.num(song, "Privilege", -1)
                        + " | PayType=" + com.yunpu.yunpumusic.api.Json.num(song, "PayType", -1)
                        + " | HQPrivilege=" + com.yunpu.yunpumusic.api.Json.num(song, "HQPrivilege", -1)
                        + " | FileSize=" + com.yunpu.yunpumusic.api.Json.num(song, "FileSize", 0));
        }

        OUT.println("\n############ KUGOU STREAM (m.kugou.com getSongInfo) ############");
        String hash = "1b742d449f9fc81c029b676814147c49";
        Http.Response info = Http.get("http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=" + hash,
                Map.of("Referer", "https://www.kugou.com/"));
        com.google.gson.JsonObject infoJson = com.yunpu.yunpumusic.api.Json.parse(info.body());
        OUT.println("url=" + com.yunpu.yunpumusic.api.Json.str(infoJson, "url"));
        OUT.println("fileName=" + com.yunpu.yunpumusic.api.Json.str(infoJson, "fileName")
                    + " bitRate=" + com.yunpu.yunpumusic.api.Json.num(infoJson, "bitRate", 0)
                    + " timeLength=" + com.yunpu.yunpumusic.api.Json.num(infoJson, "timeLength", 0));

        OUT.println("\n############ QQ SEARCH CANDIDATES ############");
        Map<String, String> qqHeaders = new LinkedHashMap<>();
        qqHeaders.put("Referer", "https://y.qq.com/");
        List<String> candidates = new ArrayList<>();
        candidates.add("desktop+grp0|" + musicu("DoSearchForQQMusicDesktop", keyword, "\"search_type\":0,\"grp\":0,"));
        candidates.add("desktop+noextra|" + musicu("DoSearchForQQMusicDesktop", keyword, ""));
        candidates.add("mobile+grp0|" + musicu("DoSearchForQQMusicMobile", keyword, "\"search_type\":0,\"grp\":0,"));
        candidates.add("soso-http|http://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=5&w=" + encoded
                        + "&format=json&new_json=1&aggr=1&cr=1&lossless=0&catZhida=1&platform=yqq");
        candidates.add("soso-https-qzone|https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=5&w=" + encoded
                        + "&format=json&new_json=1");
        candidates.add("u.y.qq.com-get|https://u.y.qq.com/cgi-bin/musicu.fcg?data="
                       + UrlEncoder.encode("{\"comm\":{\"ct\":24,\"cv\":0},\"req_1\":{\"module\":\"music.search.SearchCgiService\","
                                           + "\"method\":\"DoSearchForQQMusicDesktop\",\"param\":{\"query\":\"" + keyword
                                           + "\",\"num\":5,\"page_num\":1,\"search_type\":0,\"grp\":0}}}"));
        for (String candidate : candidates) {
            int split = candidate.indexOf('|');
            String name = candidate.substring(0, split);
            String url = candidate.substring(split + 1);
            try {
                Http.Response response = "u.y.qq.com-get".equals(name) || name.contains("soso")
                        ? Http.get(url, qqHeaders)
                        : Http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", url, qqHeaders);
                String body = response.body();
                int songIndex = body.indexOf("\"song\":");
                String snippet = songIndex < 0 ? truncate(body, 260)
                        : body.substring(songIndex, Math.min(body.length(), songIndex + 400));
                OUT.println("--- " + name + " status=" + response.status() + " len=" + body.length());
                OUT.println("    " + truncate(snippet, 400));
            } catch (Exception e) {
                OUT.println("--- " + name + " EXCEPTION " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    private static String musicu(String method, String keyword, String extra) {
        return "{\"comm\":{\"ct\":24,\"cv\":0},\"req_1\":{\"module\":\"music.search.SearchCgiService\",\"method\":\""
               + method + "\",\"param\":{\"query\":\"" + keyword + "\",\"num\":5,\"page_num\":1," + extra + "\"grp\":1}}}";
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "<null>";
        }
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}
