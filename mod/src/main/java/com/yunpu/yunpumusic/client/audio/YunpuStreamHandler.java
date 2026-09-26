package com.yunpu.yunpumusic.client.audio;

import com.github.tartaricacid.netmusic.client.api.AudioStreamHandlerManager;
import com.github.tartaricacid.netmusic.client.api.IAudioStreamHandler;
import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.IMusicProvider;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.api.YunpuUrl;
import com.yunpu.yunpumusic.api.provider.ProviderRegistry;
import com.yunpu.yunpumusic.credentials.LocalCredentialStore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.net.URL;

/**
 * 云谱占位 URL 的音频流处理器。
 *
 * <p>它挂在 Net Music 的 {@code AudioStreamHandlerManager} 上，
 * 因此无论音频是原版唱片机、广播喇叭还是本模组发出的，
 * 只要地址是本模组协议，就会在播放前实时向平台换取直链（并带上必要的请求头）。
 * 这也是会员曲目能在客户端本地解码播放的关键。
 */
@OnlyIn(Dist.CLIENT)
public final class YunpuStreamHandler implements IAudioStreamHandler {
    @Override
    public boolean canHandle(URL url) {
        return YunpuUrl.matches(url.toString());
    }

    @Override
    public AudioInputStream handle(URL url) throws UnsupportedAudioFileException, IOException {
        SongRef song = YunpuUrl.parse(url.toString());
        if (song == null) {
            throw new UnsupportedAudioFileException("无法解析的云谱地址：" + url);
        }
        IMusicProvider provider = ProviderRegistry.get(song.platform());
        AuthState auth = LocalCredentialStore.get(song.platform());
        String streamUrl;
        try {
            streamUrl = provider.resolveStreamUrl(song, auth);
        } catch (Exception e) {
            throw new IOException("解析 " + song.platform().id() + " 直链失败", e);
        }
        if (streamUrl == null || streamUrl.isBlank()) {
            throw new UnsupportedAudioFileException(
                    "该曲目当前无法播放（可能需要会员或已下架）：" + song.title());
        }
        YunpuMusic.LOGGER.debug("云谱直链解析成功：{} -> {}", song.title(), streamUrl);
        // 交给 Net Music 原有的直链处理器读取（其中已包含 User-Agent / Range 等必要请求头）
        return AudioStreamHandlerManager.handle(java.net.URI.create(streamUrl).toURL());
    }

    @Override
    public int getPriority() {
        // 高于 Net Music 的 DirectHttpHandler，确保自定义协议优先被接管
        return 1500;
    }
}
