package com.yunpu.yunpumusic.dev;

import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.UrlEncoder;
import com.yunpu.yunpumusic.api.provider.KugouProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 开发期联调工具：验证最终选定的搜索 / 直链接口。不参与模组运行。 */
public final class DevEndpointProbe4 {
    private static final java.io.PrintStream OUT = new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String keyword = args.length > 0 ? args[0] : "起风了";
        String encoded = UrlEncoder.encode(keyword);
        Map<String, String> kugouHeaders = Map.of("Referer", "https://www.kugou.com/");

        OUT.println("############ QQ MOBILE SEARCH ############");
        String data = "{\"comm\":{\"ct\":24,\"cv\":0},\"req_1\":{\"module\":\"music.search.SearchCgiService\","
                      + "\"method\":\"DoSearchForQQMusicMobile\",\"param\":{\"query\":\"" + keyword
                      + "\",\"num\":5,\"page_num\":1,\"search_type\":0,\"grp\":1}}}";
        Http.Response qq = Http.get("https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data="
                                    + UrlEncoder.encode(data), Map.of("Referer", "https://y.qq.com/"));
        String qqBody = qq.body();
        OUT.println("status=" + qq.status() + " length=" + qqBody.length());
        int songList = qqBody.indexOf("\"song\":");
        OUT.println("song node: " + (songList < 0 ? "<absent>" : qqBody.substring(songList, Math.min(qqBody.length(), songList + 700))));

        OUT.println("\n############ KUGOU SEARCH CANDIDATES ############");
        List<String> candidates = new ArrayList<>();
        candidates.add("songsearch_v2|http://songsearch.kugou.com/song_search_v2?keyword=" + encoded + "&page=1");
        candidates.add("mobilecdn_v3|http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=" + encoded
                       + "&page=1&pagesize=3&showtype=1");
        candidates.add("complexsearch_json|https://complexsearch.kugou.com/v2/search/song?keyword=" + encoded
                       + "&page=1&pagesize=3&platform=WebFilter&format=json");
        for (String candidate : candidates) {
            run(candidate, kugouHeaders);
        }

        OUT.println("\n############ KUGOU STREAM ############");
        String hash = "1b742d449f9fc81c029b676814147c49";
        List<String> streamCandidates = new ArrayList<>();
        streamCandidates.add("trackercdn-unblock|http://trackercdn.kugou.com/i/v2/?key="
                             + KugouProvider.md5(hash + "kgcloudv2") + "&hash=" + hash
                             + "&br=hq&appid=1005&pid=2&cmd=25&behavior=play");
        streamCandidates.add("trackercdn-plain|http://trackercdn.kugou.com/i/v2/?key="
                             + KugouProvider.md5(hash + "kgcloudv2") + "&hash=" + hash + "&appid=1005&pid=1&cmd=25&behavior=play");
        streamCandidates.add("m-kugou-getSongInfo|http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=" + hash);
        for (String candidate : streamCandidates) {
            run(candidate, kugouHeaders);
        }
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
