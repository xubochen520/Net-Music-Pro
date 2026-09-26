package com.yunpu.yunpumusic.network;

import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.menu.YunpuMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests one atomic disc burn; slot validation and consumption happen on the server. */
public record YunpuBurnPayload(BlockPos pos, SongRef song) implements CustomPacketPayload {
    public static final Type<YunpuBurnPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YunpuMusic.MOD_ID, "burn_disc"));
    public static final StreamCodec<ByteBuf, YunpuBurnPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, YunpuBurnPayload::pos,
            PayloadCodecs.SONG, YunpuBurnPayload::song,
            YunpuBurnPayload::new);

    public static void handle(YunpuBurnPayload payload, IPayloadContext context) {
        if (!context.flow().isServerbound()) return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof YunpuMenu menu)
                    || !menu.pos().equals(payload.pos()) || !menu.stillValid(player)) return;
            boolean success = menu.burn(payload.song());
            player.displayClientMessage(Component.translatable(success
                    ? "gui.yunpumusic.burn.success" : "gui.yunpumusic.burn.failed"), true);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
