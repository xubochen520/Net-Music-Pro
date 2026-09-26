package com.yunpu.yunpumusic.network;

import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.PlatformId;
import com.yunpu.yunpumusic.api.Membership;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：终端初始化信息。
 *
 * <p>告知客户端当前玩家的权限等级、服务端是否已存有各平台凭据，
 * 以及这台刻录台上一次点播的曲目，界面据此决定是否显示「服务端凭据」入口。
 */
public record YunpuInitPayload(int roleOrdinal, int storedPlatformMask, String platformNames) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<YunpuInitPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(YunpuMusic.MOD_ID, "terminal_init"));

    public static final StreamCodec<ByteBuf, YunpuInitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, YunpuInitPayload::roleOrdinal,
            ByteBufCodecs.VAR_INT, YunpuInitPayload::storedPlatformMask,
            ByteBufCodecs.STRING_UTF8, YunpuInitPayload::platformNames,
            YunpuInitPayload::new);

    public static YunpuInitPayload of(int roleOrdinal, java.util.Set<PlatformId> stored) {
        java.util.Map<PlatformId, Membership> tiers = new java.util.EnumMap<>(PlatformId.class);
        for (PlatformId platform : stored) tiers.put(platform, Membership.UNKNOWN);
        return of(roleOrdinal, tiers);
    }

    public static YunpuInitPayload of(int roleOrdinal, java.util.Map<PlatformId, Membership> stored) {
        int mask = 0;
        StringBuilder names = new StringBuilder();
        for (PlatformId platform : stored.keySet()) {
            mask |= 1 << platform.ordinal();
            if (!names.isEmpty()) {
                names.append(',');
            }
            names.append(platform.id()).append(':')
                    .append(stored.getOrDefault(platform, Membership.UNKNOWN).name());
        }
        return new YunpuInitPayload(roleOrdinal, mask, names.toString());
    }

    public boolean stored(PlatformId platform) {
        return (storedPlatformMask & (1 << platform.ordinal())) != 0;
    }

    public static void handle(YunpuInitPayload payload, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> com.yunpu.yunpumusic.client.ClientTerminalState.acceptInit(payload));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
