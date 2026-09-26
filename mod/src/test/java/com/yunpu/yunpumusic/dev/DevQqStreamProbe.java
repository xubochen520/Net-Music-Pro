package com.yunpu.yunpumusic.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.Http;
import com.yunpu.yunpumusic.api.Json;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.api.provider.QqProvider;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 开发期联调工具：定位 QQ 音乐直链解析失败的原因。不参与模组运行。 */
public final class DevQqStreamProbe {
    private static final java.io.PrintStream OUT = new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String keyword = args.length > 0 ? args[0] : "起风了";
        QqProvider provider = new QqProvider();
        List<SongRef> songs = provider.search(keyword, 5, null);
        if (songs.isEmpty()) {
            OUT.println("搜索无结果，无法继续");
            return;
        }
        SongRef song = songs.get(0);
        OUT.println("song: " + song.title() + " / mid=" + song.extra("mid")
                    + " mediaMid=" + song.extra("mediaMid") + " vip=" + song.vip());

        String mid = song.extra("mid");
        String mediaMid = song.extra("mediaMid");

        OUT.println("\n### variant A: UrlGetVkey (filename M500) ###");
        dump(provider, "UrlGetVkey", mid, mediaMid, "M500", null);

        OUT.println("\n### variant B: CgiGetVkey (filename M500) ###");
        dump(provider, "CgiGetVkey", mid, mediaMid, "M500", null);

        OUT.println("\n### variant C: UrlGetVkey with RSK/guid+uin from a real comm ###");
        dump(provider, "UrlGetVkey", mid, mediaMid, "M500", "full-comm");
    }

    private static void dump(QqProvider provider, String method, String mid, String mediaMid,
                             String prefix, String mode) throws Exception {
        JsonObject param = new JsonObject();
        param.addProperty("guid", "10000");
        param.addProperty("songmid", mid);
        param.addProperty("songtype", 0);
        JsonArray filenames = new JsonArray();
        filenames.add(prefix + mediaMid + ".mp3");
        param.add("filename", filenames);
        param.addProperty("ctx", 0);
        param.addProperty("uin", "0");

        JsonObject comm = new JsonObject();
        comm.addProperty("ct", 24);
        comm.addProperty("cv", 0);
        if ("full-comm".equals(mode)) {
            comm.addProperty("format", "json");
            comm.addProperty("guid", "10000");
            comm.addProperty("uin", "0");
        }
        JsonObject request = new JsonObject();
        request.addProperty("module", "music.vkey.GetVkey");
        request.addProperty("method", method);
        request.add("param", param);
        JsonObject body = new JsonObject();
        body.add("comm", comm);
        body.add("req_1", request);

        Http.Response response = Http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body.toString(),
                Map.of("Referer", "https://y.qq.com/", "Origin", "https://y.qq.com"));
        OUT.println("response: " + truncate(response.body(), 900));
        OUT.println("resolved: [" + provider.resolveStreamUrl(
                new SongRef(song_platform(), mid, "", List.of(), "", 0, false, 0,
                        Map.of("mid", mid, "mediaMid", mediaMid)), new AuthState()) + "]");
    }

    private static com.yunpu.yunpumusic.api.PlatformId song_platform() {
        return com.yunpu.yunpumusic.api.PlatformId.QQ;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "<null>";
        }
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    static {
        Json.str(new JsonObject(), "x");
    }
}
