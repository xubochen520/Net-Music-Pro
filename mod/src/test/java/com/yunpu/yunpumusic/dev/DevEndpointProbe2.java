package com.yunpu.yunpumusic.dev;

import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.UrlEncoder;
import com.yunpu.yunpumusic.api.provider.KugouProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开发期联调工具：逐个尝试候选接口，找出真正可用的搜索 / 扫码入口。
 * 不参与模组运行。
 */
public final class DevEndpointProbe2 {
    private static final java.io.PrintStream OUT = new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String keyword = args.length > 0 ? args[0] : "起风了";

        OUT.println("############ QQ SEARCH -(full response) ############");
        String body = "{\"comm\":{\"ct\":24,\"cv\":0},\"req_1\":{\"module\":\"music.search.SearchCgiService\","
                      + "\"method\":\"DoSearchForQQMusicDesktop\",\"param\":{\"query\":\"" + keyword
                      + "\",\"num\":5,\"page_num\":1,\"search_type\":0,\"grp\":1}}}";
        Map<String, String> qqHeaders = new HashMap<>();
        qqHeaders.put("Referer", "https://y.qq.com/");
        qqHeaders.put("Origin", "https://y.qq.com");
        Http.Response qq = Http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body, qqHeaders);
        OUT.println(qq.body());

        OUT.println("\n############ KUGOU CANDIDATES ############");
        kugouCandidates(keyword);
    }

    private static void kugouCandidates(String keyword) throws Exception {
        Map<String, String> headers = Map.of("Referer", "https://www.kugou.com/");
        String encoded = UrlEncoder.encode(keyword);
        long now = System.currentTimeMillis();

        List<String> candidates = new ArrayList<>();
        candidates.add("mobilecdn-http|http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword="
                       + encoded + "&page=1&pagesize=5&showtype=1");
        candidates.add("complexsearch-nosign|https://complexsearch.kugou.com/v2/search/song?appid=1005&clientver=20489&clienttime="
                       + now + "&mid=" + now + "&uuid=" + now + "&dfid=-&keyword=" + encoded
                       + "&page=1&pagesize=5&type=0&iscorrection=1&privilege_filter=0&filter=10&platform=WebFilter");
        candidates.add("complexsearch-sorted-sign|" + complexSearchSigned(keyword, now));
        candidates.add("kugou-v2-qrcode|https://login-user.kugou.com/v2/qrcode?appid=1005&clientver=20489&clienttime="
                       + (now / 1000) + "&signature=" + KugouProvider.md5("1005|20489|NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"));
        candidates.add("kugou-qrcode-legacy|https://login-user.kugou.com/qrcode?appid=1005&clientver=20489&clienttime="
                       + (now / 1000));

        for (String candidate : candidates) {
            int split = candidate.indexOf('|');
            String name = candidate.substring(0, split);
            String url = candidate.substring(split + 1);
            try {
                Http.Response response = Http.get(url, headers);
                OUT.println("--- " + name + " status=" + response.status());
                OUT.println("    " + truncate(response.body(), 500));
            } catch (Exception e) {
                OUT.println("--- " + name + " EXCEPTION " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    /** 按酷狗 Web 端签名规则：salt + 排序后的 k=v 串 + salt 的 MD5。 */
    private static String complexSearchSigned(String keyword, long now) {
        String params = "appid=1005&clientver=20489&clienttime=" + now + "&mid=" + now + "&uuid=" + now
                        + "&dfid=-&keyword=" + UrlEncoder.encode(keyword)
                        + "&page=1&pagesize=5&type=0&iscorrection=1&privilege_filter=0&filter=10&platform=WebFilter";
        String salt = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
        List<String> pairs = new ArrayList<>(List.of(params.split("&")));
        pairs.sort(String::compareTo);
        String signature = KugouProvider.md5(salt + String.join("", pairs) + salt);
        return "https://complexsearch.kugou.com/v2/search/song?" + params + "&signature=" + signature;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "<null>";
        }
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}
