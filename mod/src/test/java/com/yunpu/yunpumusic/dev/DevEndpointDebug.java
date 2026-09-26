package com.yunpu.yunpumusic.dev;

import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.UrlEncoder;
import com.yunpu.yunpumusic.api.provider.KugouProvider;
import com.yunpu.yunpumusic.api.provider.QqProvider;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 开发期联调工具：直接打印 QQ 音乐 / 酷狗接口的原始响应，
 * 用于核对请求参数与签名算法。不参与模组运行。
 */
public final class DevEndpointDebug {
    public static void main(String[] args) throws Exception {
        java.io.PrintStream out = new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8);
        String keyword = args.length > 0 ? args[0] : "起风了";

        out.println("############ QQ MUSIC SEARCH ############");
        qqSearch(out, keyword, "https://u.y.qq.com/cgi-bin/musicu.fcg");
        qqSearch(out, keyword, "https://u6.y.qq.com/cgi-bin/musicu.fcg");

        out.println("\n############ QQ QR CODE ############");
        Map<String, String> qqHeaders = new HashMap<>();
        qqHeaders.put("Referer", "https://xui.ptlogin2.qq.com/");
        Http.Response qr = Http.get("https://ssl.ptlogin2.qq.com/ptqrshow?appid=716027609&e=2&l=M&s=3&d=72&v=4"
                                   + "&t=" + Math.random() + "&daid=383&pt_3rd_aid=100497308", qqHeaders);
        out.println("status=" + qr.status() + " content-type=" + qr.headers().get("content-type"));
        out.println("set-cookie=" + qr.headers().get("set-cookie"));
        out.println("body length=" + qr.body().length());

        out.println("\n############ KUGOU QR CODE ############");
        String appId = "1005";
        String clientVer = "20489";
        String salt = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
        String[] salts = {salt, "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt", ""};
        for (String candidate : salts) {
            String url = "https://login-user.kugou.com/v2/qrcode?appid=" + appId
                         + "&clientver=" + clientVer + "&clienttime=" + (System.currentTimeMillis() / 1000)
                         + "&signature=" + KugouProvider.md5(appId + "|" + clientVer + "|" + candidate);
            Http.Response response = Http.get(url, Map.of("Referer", "https://www.kugou.com/"));
            out.println("salt=" + (candidate.isEmpty() ? "<empty>" : "default") + " -> " + response.status()
                        + " : " + truncate(response.body(), 400));
            break;
        }

        out.println("\n############ KUGOU SEARCH ############");
        kugouSearch(out, keyword);
    }

    private static void qqSearch(java.io.PrintStream out, String keyword, String endpoint) throws Exception {
        String body = "{\"comm\":{\"ct\":24,\"cv\":0},\"req_1\":{\"module\":\"music.search.SearchCgiService\","
                      + "\"method\":\"DoSearchForQQMusicDesktop\",\"param\":{\"query\":\"" + keyword
                      + "\",\"num\":5,\"page_num\":1,\"search_type\":0,\"grp\":1}}}";
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://y.qq.com/");
        headers.put("Origin", "https://y.qq.com");
        Http.Response response = Http.postJson(endpoint, body, headers);
        out.println("endpoint=" + endpoint + " status=" + response.status());
        out.println("body=" + truncate(response.body(), 700));
    }

    private static void kugouSearch(java.io.PrintStream out, String keyword) throws Exception {
        String appId = "1005";
        String clientVer = "20489";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String params = "appid=" + appId
                        + "&clientver=" + clientVer
                        + "&clienttime=" + timestamp
                        + "&mid=" + timestamp
                        + "&uuid=" + timestamp
                        + "&dfid=-"
                        + "&keyword=" + UrlEncoder.encode(keyword)
                        + "&page=1&pagesize=5"
                        + "&type=0&iscorrection=1&privilege_filter=0&filter=10&platform=WebFilter";
        String salt = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
        java.util.List<String> pairs = new java.util.ArrayList<>();
        for (String pair : params.split("&")) {
            pairs.add(pair);
        }
        pairs.sort(String::compareTo);
        String signature = KugouProvider.md5(salt + String.join("", pairs) + salt);

        String url = "https://complexsearch.kugou.com/v2/search/song?" + params + "&signature=" + signature;
        Http.Response response = Http.get(url, Map.of("Referer", "https://www.kugou.com/"));
        out.println("status=" + response.status());
        out.println("body=" + truncate(response.body(), 700));

        // 对照：不带签名 / 换用移动端接口
        String mobile = "https://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword="
                        + UrlEncoder.encode(keyword) + "&page=1&pagesize=5&showtype=1";
        Http.Response mobileResponse = Http.get(mobile, Map.of("Referer", "https://www.kugou.com/"));
        out.println("mobile status=" + mobileResponse.status());
        out.println("mobile body=" + truncate(mobileResponse.body(), 700));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "<null>";
        }
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}
