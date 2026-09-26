package com.yunpu.yunpumusic.dev;

import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.IMusicProvider;
import com.yunpu.yunpumusic.api.PlatformId;
import com.yunpu.yunpumusic.api.QrLoginSession;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.api.provider.KugouProvider;
import com.yunpu.yunpumusic.api.provider.NetEaseProvider;
import com.yunpu.yunpumusic.api.provider.QqProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * 开发期联调工具（不参与模组运行）：直接调用三个平台的真实接口，
 * 验证扫码登录会话创建、模糊搜索、歌词与直链解析是否可用。
 *
 * <p>用法：{@code java ... DevProviderProbe [关键词]}
 */
public final class DevProviderProbe {
    public static void main(String[] args) throws Exception {
        String keyword = args.length > 0 ? args[0] : "起风了";
        List<IMusicProvider> providers = new ArrayList<>(List.of(
                new NetEaseProvider(), new QqProvider(), new KugouProvider()));

        for (IMusicProvider provider : providers) {
            PlatformId platform = provider.platform();
            System.out.printf("%n================ %s (%s) ================%n", platform.id(), platform.displayName());

            // 1) 扫码登录会话
            try {
                QrLoginSession session = provider.startLogin();
                String qr = session.qrContent();
                System.out.printf("[login] status=%s key=%s%n", session.status(),
                        abbreviate(session.sessionKey(), 24));
                System.out.printf("[login] qr=%s (len=%d)%n", abbreviate(qr, 120), qr.length());
                if (session.status() != QrLoginSession.Status.FAILED) {
                    QrLoginSession polled = provider.pollLogin(session);
                    System.out.printf("[login] first poll -> %s : %s%n", polled.status(), polled.message());
                }
            } catch (Exception e) {
                System.out.printf("[login] EXCEPTION %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
            }

            // 2) 搜索
            try {
                long start = System.currentTimeMillis();
                List<SongRef> songs = provider.search(keyword, 5, null);
                long cost = System.currentTimeMillis() - start;
                System.out.printf("[search] %d result(s) in %dms%n", songs.size(), cost);
                for (SongRef song : songs) {
                    System.out.printf("   - %-30s | %-20s | %6s | vip=%-5s | id=%s | extras=%s%n",
                            abbreviate(song.title(), 30), abbreviate(song.artistText(), 20),
                            song.durationText(), song.vip(), abbreviate(song.id(), 16), song.extras().keySet());
                }
                if (!songs.isEmpty()) {
                    SongRef first = songs.get(0);
                    // 3) 歌词
                    try {
                        String lrc = provider.fetchLyric(first, null);
                        System.out.printf("[lyric] %d chars, head=%s%n", lrc.length(),
                                abbreviate(lrc.replace("\n", "\\n"), 100));
                    } catch (Exception e) {
                        System.out.printf("[lyric] EXCEPTION %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
                    }
                    // 4) 直链
                    try {
                        String url = provider.resolveStreamUrl(first, new AuthState());
                        System.out.printf("[stream] %s%n", abbreviate(url, 150));
                    } catch (Exception e) {
                        System.out.printf("[stream] EXCEPTION %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.out.printf("[search] EXCEPTION %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "<null>";
        }
        String flat = text.replace("\r", "");
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}
