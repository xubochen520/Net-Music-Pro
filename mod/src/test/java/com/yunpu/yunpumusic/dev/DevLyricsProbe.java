package com.yunpu.yunpumusic.dev;

import com.yunpu.yunpumusic.api.IMusicProvider;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.api.provider.KugouProvider;
import com.yunpu.yunpumusic.api.provider.NetEaseProvider;
import com.yunpu.yunpumusic.api.provider.QqProvider;
import com.yunpu.yunpumusic.client.LyricTimeline;

/** Live lyric coverage check; prints only counts, never lyrics or credentials. */
public final class DevLyricsProbe {
    public static void main(String[] args) {
        String keyword = args.length == 0 ? "起风了" : args[0];
        for (IMusicProvider provider : new IMusicProvider[]{new KugouProvider(), new QqProvider(), new NetEaseProvider()}) {
            try {
                var songs = provider.search(keyword, 5, null);
                System.out.println(provider.platform().id() + " results=" + songs.size());
                for (SongRef song : songs.stream().limit(3).toList()) {
                    try {
                        String lyric = provider.fetchLyric(song, null);
                        System.out.println("  lyricChars=" + lyric.length()
                                + " timedLines=" + LyricTimeline.parse(lyric).lineCount());
                    } catch (Exception e) {
                        System.out.println("  lyricError=" + e.getClass().getSimpleName());
                    }
                }
            } catch (Exception e) {
                System.out.println(provider.platform().id() + " searchError=" + e.getClass().getSimpleName());
            }
        }
    }
}
