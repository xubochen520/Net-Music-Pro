package com.yunpu.yunpumusic.dev;

import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.UrlEncoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 开发期联调工具：穷举候选接口，定位可用的搜索 / 扫码入口。不参与模组运行。 */
public final class DevEndpointProbe3 {
    private static final java.io.PrintStream OUT = new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String keyword = args.length > 0 ? args[0] : "起风了";
        String encoded = UrlEncoder.encode(keyword);
        long now = System.currentTimeMillis();

        OUT.println("############ QQ MUSIC SEARCH CANDIDATES ############");
        List<String> qq = new ArrayList<>();
        qq.add("c.y.qq.com/soso|https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=5&w=" + encoded
               + "&format=json&aggr=1&cr=1&lossless=0&new_json=1&platform=yqq.json");
        qq.add("c.y.qq.com/soso-old|https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=5&w=" + encoded
               + "&format=json&ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.song&t=0&aggr=1&cr=1&catZhida=1");
        qq.add("u.y.qq.com(mobile module)|https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data="
               + UrlEncoder.encode("{\"comm\":{\"ct\":24,\"cv\":0},\"req_1\":{\"module\":\"music.search.SearchCgiService\","
                                   + "\"method\":\"DoSearchForQQMusicMobile\",\"param\":{\"query\":\"" + keyword
                                   + "\",\"num\":5,\"page_num\":1,\"search_type\":0,\"grp\":1}}}"));
        for (String candidate : qq) {
            run(candidate, Map.of("Referer", "https://y.qq.com/"));
        }

        OUT.println("\n############ KUGOU QR CANDIDATES ############");
        List<String> kugouQr = new ArrayList<>();
        long seconds = now / 1000;
        kugouQr.add("v2-plat|https://login-user.kugou.com/v2/qrcode?appid=1005&clientver=20489&clienttime="
                    + seconds + "&plat=0&qrcode_ticket=");
        kugouQr.add("v2-minimal|https://login-user.kugou.com/v2/qrcode?appid=1005&clientver=20489");
        kugouQr.add("v2-mid|https://login-user.kugou.com/v2/qrcode?appid=1005&clientver=20489&clienttime="
                    + seconds + "&mid=" + now);
        kugouQr.add("v2-dfid|https://login-user.kugou.com/v2/qrcode?appid=1005&clientver=20489&clienttime="
                    + seconds + "&dfid=-&mid=" + now + "&uuid=" + now);
        kugouQr.add("v2-sig-variant|https://login-user.kugou.com/v2/qrcode?appid=1005&clientver=20489&clienttime="
                    + seconds + "&signature=" + com.yunpu.yunpumusic.api.provider.KugouProvider
                .md5("1005" + "20489" + "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"));
        kugouQr.add("v2-openapi|https://login-user.kugou.com/v2/qrcode?appid=1005&clientver=20489&clienttime="
                    + seconds + "&signature=" + com.yunpu.yunpumusic.api.provider.KugouProvider
                .md5("NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt" + "1005" + "20489" + seconds
                     + "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"));
        for (String candidate : kugouQr) {
            run(candidate, Map.of("Referer", "https://www.kugou.com/"));
        }

        OUT.println("\n############ KUGOU SEARCH (mobilecdn, http) ############");
        run("mobilecdn|http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=" + encoded
            + "&page=1&pagesize=3&showtype=1", Map.of("Referer", "https://www.kugou.com/"));

        OUT.println("\n############ KUGOU LYRIC / STREAM CANDIDATES ############");
        run("krcs|https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&hash=1b742d449f9fc81c029b676814147c49",
                Map.of("Referer", "https://www.kugou.com/"));
        run("trackercdn|https://trackercdn.kugou.com/i/v2/?appid=1005&version=20489&behavior=play&cmd=25&pid=1"
            + "&hash=1b742d449f9fc81c029b676814147c49&key="
            + com.yunpu.yunpumusic.api.provider.KugouProvider.md5("1b742d449f9fc81c029b676814147c49" + "kgcloudv2")
            + "&filesize=4000000", Map.of("Referer", "https://www.kugou.com/"));
    }

    private static void run(String candidate, Map<String, String> headers) {
        int split = candidate.indexOf('|');
        String name = split < 0 ? candidate : candidate.substring(0, split);
        String url = split < 0 ? candidate : candidate.substring(split + 1);
        try {
            Http.Response response = Http.get(url, headers);
            OUT.println("--- " + name + " status=" + response.status());
            OUT.println("    " + truncate(response.body(), 420));
        } catch (Exception e) {
            OUT.println("--- " + name + " EXCEPTION " + e.getClass().getSimpleName() + ": " + e.getMessage());
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
