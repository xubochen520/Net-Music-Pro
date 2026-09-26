package com.yunpu.yunpumusic.client;

import com.github.tartaricacid.netmusic.config.GeneralConfig;
import com.github.tartaricacid.netmusic.api.lyric.LyricRecord;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.github.tartaricacid.netmusic.tileentity.TileEntityMusicPlayer;
import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.api.YunpuUrl;
import com.yunpu.yunpumusic.api.provider.ProviderRegistry;
import com.yunpu.yunpumusic.credentials.LocalCredentialStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** 为 Net Music 唱片机播放的云谱唱片绘制歌词。 */
@EventBusSubscriber(modid = YunpuMusic.MOD_ID, value = Dist.CLIENT)
public final class YunpuDiscLyricOverlay {
    private static final long EMPTY_RETRY_MS = 60_000;
    private static final Map<String, LyricRequest> LYRICS = new HashMap<>();
    private static final Map<BlockPos, PlayingDisc> PLAYING = new HashMap<>();
    private static ClientLevel previousLevel;

    private record LyricRequest(CompletableFuture<LyricTimeline> future, long startedAt) { }
    private record PlayingDisc(String key, long startedAtTick) { }

    private YunpuDiscLyricOverlay() { }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                || !GeneralConfig.ENABLE_PLAYER_LYRICS.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        if (level != previousLevel) {
            PLAYING.clear();
            LYRICS.clear();
            previousLevel = level;
        }
        var camera = event.getCamera();
        var cameraPos = camera.getPosition();
        int centerX = camera.getBlockPosition().getX() >> 4;
        int centerZ = camera.getBlockPosition().getZ() >> 4;
        for (int cx = centerX - 1; cx <= centerX + 1; cx++) {
            for (int cz = centerZ - 1; cz <= centerZ + 1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity entity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(entity instanceof TileEntityMusicPlayer player)) continue;
                    BlockPos pos = player.getBlockPos();
                    if (pos.distToCenterSqr(cameraPos) > 24 * 24) continue;
                    if (!player.isPlay()) {
                        PLAYING.remove(pos);
                        continue;
                    }
                    ItemMusicCD.SongInfo info = ItemMusicCD.getSongInfo(player.getPlayerInv().getStackInSlot(0));
                    if (info == null) continue;
                    SongRef song = YunpuUrl.parse(info.songUrl);
                    if (song == null) continue;

                    String key = song.key();
                    PlayingDisc playing = PLAYING.get(pos);
                    if (playing == null || !key.equals(playing.key())) {
                        player.lyricRecord = null;
                        int elapsed = info.songTime > 0 && player.getCurrentTime() > 0
                                ? Math.max(0, info.songTime * 20 + 64 - player.getCurrentTime()) : 0;
                        playing = new PlayingDisc(key, level.getGameTime() - elapsed);
                        PLAYING.put(pos.immutable(), playing);
                    }
                    LyricRequest request = LYRICS.get(key);
                    long now = System.currentTimeMillis();
                    if (request == null || request.future().isDone()
                            && request.future().getNow(null) != null
                            && request.future().getNow(null).isEmpty()
                            && now - request.startedAt() >= EMPTY_RETRY_MS) {
                        request = new LyricRequest(CompletableFuture.supplyAsync(() -> fetch(song)), now);
                        LYRICS.put(key, request);
                    }
                    LyricTimeline timeline = request.future().getNow(null);
                    if (timeline == null || timeline.isEmpty()) continue;
                    if (player.lyricRecord == null) {
                        player.lyricRecord = toRecord(timeline, song.title());
                        YunpuMusic.LOGGER.debug("已为唱片机加载 {} 行歌词：{}", timeline.lineCount(), song.key());
                    }
                    int elapsed = (int) Math.max(0, level.getGameTime() - playing.startedAtTick());
                    player.lyricRecord.updateCurrentLine(elapsed);
                }
            }
        }
    }

    /** Net Music's own renderer consumes this record and draws its normal lyric billboard. */
    private static LyricRecord toRecord(LyricTimeline timeline, String title) {
        var original = new Int2ObjectRBTreeMap<String>();
        var translated = new Int2ObjectRBTreeMap<String>();
        var lines = timeline.allLines();
        for (int i = 0; i < timeline.lineCount(); i++) {
            String[] parts = lines.get(i).split("\\n", 2);
            if (!parts[0].isBlank()) original.put(timeline.tickAt(i), parts[0]);
            if (parts.length > 1 && !parts[1].isBlank()) translated.put(timeline.tickAt(i), parts[1]);
        }
        if (!original.containsKey(0)) original.put(0, title);
        return translated.isEmpty() ? new LyricRecord(original) : new LyricRecord(original, translated);
    }

    private static LyricTimeline fetch(SongRef song) {
        try {
            String lrc = ProviderRegistry.get(song.platform()).fetchLyric(
                    song, LocalCredentialStore.get(song.platform()));
            return LyricTimeline.parse(lrc);
        } catch (Exception e) {
            YunpuMusic.debugOnce("歌词拉取失败：" + song.platform().id() + ":" + song.id(), e);
            return LyricTimeline.parse("");
        }
    }
}
