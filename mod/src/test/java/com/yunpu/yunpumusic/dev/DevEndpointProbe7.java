package com.yunpu.yunpumusic.dev;

import com.google.gson.JsonObject;
import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.Json;
import com.yunpu.yunpumusic.api.UrlEncoder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 开发期联调工具：验证酷狗直链与 QQ 搜索参数。不参与模组运行。 */
public final class DevEndpointProbe7 {
    private static final java.io.PrintStream OUT = new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String keyword = args.length > 0 ? args[0] : "起风了";

        OUT.println("############ KUGOU STREAM v2 FULL PARAMS ############");
        String hashLower = "1b742d449f9fc81c029b676814147c49";
        String hashUpper = hashLower.toUpperCase();
        StringBuilder url = new StringBuilder("http://trackercdn.kugou.com/i/v2/?")
                .append("key=").append(com.yunpu.yunpumusic.api.provider.KugouProvider.md5(hashLower + "kgcloudv2"))
                .append("&hash=").append(hashLower)
                .append("&br=hq&appid=1005&pid=2&cmd=25&behavior=play&dfid=-&mid=1&userid=0&token=&vip=0&album_id=53303903");
        OUT.println(truncate(probe(url.toString()), 500));
        OUT.println("--- uppercase hash");
        OUT.println(truncate(probe(url.toString().replace(hashLower, hashUpper)
                .replace(com.yunpu.yunpumusic.api.provider.KugouProvider.md5(hashLower + "kgcloudv2"),
                        com.yunpu.yunpumusic.api.provider.KugouProvider.md5(hashUpper + "kgcloudv2"))), 500));
        OUT.println("--- v1 style");
        OUT.println(truncate(probe("http://trackercdn.kugou.com/i/?cmd=4&hash=" + hashLower + "&key="
                + com.yunpu.yunpumusic.api.provider.KugouProvider.md5(hashLower + "kgcloudv2")
                + "&pid=1&forceDown=0&vip=0"), 500));

        OUT.println("\n############ QQ SEARCH (correct mobile params) ############");
        String data = "{\"comm\":{\"ct\":24,\"cv\":0},\"req_1\":{\"module\":\"music.search.SearchCgiService\","
                      + "\"method\":\"DoSearchForQQMusicMobile\",\"param\":{\"searchid\":\"1\",\"query\":\"" + keyword
                      + "\",\"search_type\":0,\"num_per_page\":5,\"page_num\":1,\"highlight\":true,\"grp\":true,"
                      + "\"selectors\":{},\"vec_selectors\":[]}}}";
        OUT.println(truncate(probe("https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=" + UrlEncoder.encode(data)), 900));

        OUT.println("\n############ QQ SEARCH ADAPTOR ############");
        String adaptor = "{\"comm\":{\"ct\":24,\"cv\":0},\"req_1\":{\"module\":\"music.adaptor.SearchAdaptor\","
                         + "\"method\":\"do_search_v2\",\"param\":{\"searchid\":\"1\",\"search_type\":100,"
                         + "\"page_num\":5,\"query\":\"" + keyword + "\",\"page_id\":1,\"highlight\":true,\"grp\":true}}}";
        String adaptorBody = probe("https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=" + UrlEncoder.encode(adaptor));
com.google.gson.JsonObject adaptorRoot = Json.parse(adaptorBody.substring(adaptorBody.indexOf('{')));
com.google.gson.JsonObject adaptorBodyNode = Json.obj(Json.obj(Json.obj(adaptorRoot, "req_1"), "data"), "body");
if (adaptorBodyNode != null) {
    for (String key : adaptorBodyNode.keySet()) {
        com.google.gson.JsonElement element = adaptorBodyNode.get(key);
        if (element.isJsonObject()) {
            com.google.gson.JsonArray list = Json.array(element.getAsJsonObject(), "items");
            OUT.println("  " + key + " -> items=" + list.size());
            if (list.size() > 0) {
                OUT.println("     first: " + truncate(list.get(0).toString(), 600));
            }
        }
    }
}

        OUT.println("\n############ KUGOU QR (with dfid cookie) ############");
        Map<String, String> headers = Map.of(
                "Referer", "https://www.kugou.com/",
                "Cookie", "kg_mid=2333; kg_dfid=1abcdefghijklmnop; kg_dfid_collect=1abcdefghijklmnop",
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        long seconds = System.currentTimeMillis() / 1000;
        List<String> qrCandidates = List.of(
                "https://login-user.kugou.com/v2/qrcode?appid=1005&clientver=20489&clienttime=" + seconds
                + "&mid=1abcdefghijklmnop&dfid=1abcdefghijklmnop",
                "https://login-user.kugou.com/v2/qrcode?appid=1005&clientver=20489&clienttime=" + seconds
                + "&signature=" + com.yunpu.yunpumusic.api.provider.KugouProvider
                        .md5("1005|20489|" + seconds + "|NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"));
        for (String candidate : qrCandidates) {
            try {
                Http.Response response = Http.get(candidate, headers);
                OUT.println("--- " + truncate(candidate, 70) + " : " + truncate(response.body(), 300));
            } catch (Exception e) {
                OUT.println("--- EXCEPTION " + e.getMessage());
            }
        }
    }

    private static String probe(String url) {
        try {
            Http.Response response = Http.get(url, Map.of("Referer", "https://www.kugou.com/",
                    "Cookie", "kg_mid=1; kg_dfid=1"));
            return response.status() + " " + response.body();
        } catch (Exception e) {
            return "EXCEPTION " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "<null>";
        }
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    static {
        JsonObject unused = new JsonObject();
        Json.str(unused, "x");
    }
}
