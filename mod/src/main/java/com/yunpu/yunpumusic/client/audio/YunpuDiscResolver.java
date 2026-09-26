package com.yunpu.yunpumusic.client.audio;

import com.github.tartaricacid.netmusic.api.resolver.IAsyncSongUrlResolver;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.yunpu.yunpumusic.api.YunpuUrl;

import java.util.concurrent.CompletableFuture;

/** Keep this add-on's disc URL intact so its stream handler can use the owner's account. */
public final class YunpuDiscResolver implements IAsyncSongUrlResolver {
    @Override
    public boolean canResolve(ItemMusicCD.SongInfo song) {
        return song != null && YunpuUrl.matches(song.songUrl);
    }

    @Override
    public CompletableFuture<ItemMusicCD.SongInfo> resolve(ItemMusicCD.SongInfo song) {
        return CompletableFuture.completedFuture(song);
    }

    @Override
    public int getPriority() {
        return 10_000;
    }
}
