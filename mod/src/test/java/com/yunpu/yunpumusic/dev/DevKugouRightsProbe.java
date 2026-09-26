package com.yunpu.yunpumusic.dev;

import com.google.gson.JsonObject;
import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.Json;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.api.provider.KugouProvider;
import com.yunpu.yunpumusic.client.LyricTimeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;

/** 手动联调：只输出会员等级和歌曲 VIP 计数，不输出账号凭据。 */
public final class DevKugouRightsProbe {
    public static void main(String[] args) throws Exception {
        KugouProvider provider = new KugouProvider();
        AuthState auth = null;
        if (args.length > 0 && !args[0].isBlank()) {
            JsonObject root = Json.parse(Files.readString(Path.of(args[0])));
            JsonObject account = Json.obj(root, "kugou");
            if (account != null) auth = AuthState.fromJson(account);
        }
        if (auth != null) System.out.println("membership=" + provider.membership(auth));
        String keyword = args.length > 1 ? args[1] : "游京";
        List<SongRef> songs = provider.search(keyword, 30, auth);
        long vipCount = songs.stream().filter(SongRef::vip).count();
        System.out.println("results=" + songs.size() + " vip=" + vipCount);
        for (SongRef song : songs.stream().limit(5).toList()) {
            System.out.println(song.title() + " | VIP=" + song.vip()
                    + " | privilege=" + song.extra("privilege")
                    + " payType=" + song.extra("payType"));
        }
        if (auth != null) {
            SongRef vipSong = songs.stream().filter(SongRef::vip).findFirst().orElse(null);
            if (vipSong != null) {
                try {
                    String lyric = provider.fetchLyric(vipSong, auth);
                    System.out.println("vipLyricChars=" + lyric.length()
                            + " timedLines=" + LyricTimeline.parse(lyric).lineCount());
                    String stream = provider.resolveStreamUrl(vipSong, auth);
                    System.out.println("vipStreamAvailable=" + !stream.isBlank()
                            + (stream.isBlank() ? "" : " host=" + URI.create(stream).getHost()));
                    if (!stream.isBlank()) {
                        try (HttpClient client = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(8))
                                .followRedirects(HttpClient.Redirect.NORMAL).build()) {
                            HttpRequest request = HttpRequest.newBuilder(URI.create(stream))
                                    .timeout(Duration.ofSeconds(12))
                                    .header("Range", "bytes=0-4095")
                                    .header("User-Agent", "Mozilla/5.0")
                                    .GET().build();
                            HttpResponse<java.io.InputStream> response = client.send(request,
                                    HttpResponse.BodyHandlers.ofInputStream());
                            try (var body = response.body()) {
                                byte[] prefix = body.readNBytes(4);
                                System.out.println("vipAudioHttp=" + response.statusCode()
                                        + " bytesRead=" + prefix.length
                                        + " contentType=" + response.headers().firstValue("content-type").orElse(""));
                            }
                        }
                    }
                    var oldExtras = new HashMap<>(vipSong.extras());
                    oldExtras.remove("albumAudioId");
                    SongRef oldDisc = new SongRef(vipSong.platform(), vipSong.id(), vipSong.title(),
                            vipSong.artists(), vipSong.album(), vipSong.durationSeconds(), vipSong.vip(),
                            vipSong.fee(), oldExtras);
                    System.out.println("existingDiscStreamAvailable="
                            + !provider.resolveStreamUrl(oldDisc, auth).isBlank());
                } catch (Exception e) {
                    System.out.println("vipStreamError=" + e.getClass().getSimpleName());
                }
            }
        }
    }
}
