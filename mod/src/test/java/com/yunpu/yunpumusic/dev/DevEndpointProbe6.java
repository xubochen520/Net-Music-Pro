package com.yunpu.yunpumusic.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.Json;
import com.yunpu.yunpumusic.api.UrlEncoder;
import com.yunpu.yunpumusic.api.provider.KugouProvider;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 开发期联调工具：打印 QQ 移动端搜索的字段结构，并寻找酷狗可用直链接口。不参与模组运行。 */
public final class DevEndpointProbe6 {
    private static final java.io.PrintStream OUT = new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String keyword = args.length > 0 ? args[0] : "起风了";

        OUT.println("############ QQ MOBILE SEARCH STRUCTURE ############");
        String data = "{\"comm\":{\"ct\":24,\"cv\":0},\"req_1\":{\"module\":\"music.search.SearchCgiService\","
                      + "\"method\":\"DoSearchForQQMusicMobile\",\"param\":{\"query\":\"" + keyword
                      + "\",\"num\":5,\"page_num\":1,\"search_type\":0,\"grp\":1}}}";
        Http.Response qq = Http.get("https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=" + UrlEncoder.encode(data),
                Map.of("Referer", "https://y.qq.com/"));
        JsonObject root = Json.parse(qq.body());
        JsonObject req = Json.obj(root, "req_1");
        JsonObject body = Json.obj(Json.obj(req, "data"), "body");
        OUT.println("body keys = " + (body == null ? "<null>" : body.keySet()));
        if (body != null) {
            for (String key : body.keySet()) {
                com.google.gson.JsonElement element = body.get(key);
                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    OUT.println("  " + key + " -> object keys " + object.keySet());
                    JsonArray list = Json.array(object, "list");
                    if (list.size() > 0) {
                        OUT.println("     first item keys: " + list.get(0).getAsJsonObject().keySet());
                        OUT.println("     first item: " + truncate(list.get(0).toString(), 500));
                    }
                } else if (element.isJsonArray()) {
                    OUT.println("  " + key + " -> array size " + element.getAsJsonArray().size());
                }
            }
        }
        OUT.println("RAW: " + truncate(qq.body(), 1800));

        OUT.println("\n############ KUGOU STREAM CANDIDATES ############");
        String hash = "1B742D449F9FC81C029B676814147C49";
        List<String> candidates = List.of(
                "getSongInfo-upper|http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=" + hash,
                "getSongInfo-lower|http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=" + hash.toLowerCase(),
                "trackercdn-dfid|http://trackercdn.kugou.com/i/v2/?key=" + KugouProvider.md5(hash + "kgcloudv2")
                + "&hash=" + hash + "&br=hq&appid=1005&pid=2&cmd=25&behavior=play&dfid=-&mid=1&userid=0&token=",
                "trackercdn-v1|http://trackercdn.kugou.com/i/?cmd=4&hash=" + hash + "&key="
                + KugouProvider.md5(hash + "kgcloudv2") + "&pid=1&forceDown=0&vip=0",
                "kugou-api-v2|http://www.kugou.com/yy/index.php?r=play/getdata&hash=" + hash
        );
        for (String candidate : candidates) {
            int split = candidate.indexOf('|');
            String name = candidate.substring(0, split);
            String url = candidate.substring(split + 1);
            try {
                Http.Response response = Http.get(url, Map.of("Referer", "https://www.kugou.com/",
                        "Cookie", "kg_mid=1; kg_dfid=1"));
                OUT.println("--- " + name + " status=" + response.status());
                OUT.println("    " + truncate(response.body(), 400));
            } catch (Exception e) {
                OUT.println("--- " + name + " EXCEPTION " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "<null>";
        }
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}
