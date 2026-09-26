package com.yunpu.yunpumusic.network;

import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.PlatformId;
import com.yunpu.yunpumusic.credentials.Role;
import com.yunpu.yunpumusic.credentials.ServerCredentialStore;
import com.yunpu.yunpumusic.menu.YunpuMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：把扫码登录得到的会话凭据存入服务端。
 *
 * <p><b>权限校验在服务端完成</b>：只有服主 / OP 的请求会被接受，
 * 普通玩家即使伪造该数据包也会被直接拒绝。
 */
public record YunpuCredentialPayload(BlockPos pos, PlatformId platform, AuthState auth, boolean store)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<YunpuCredentialPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(YunpuMusic.MOD_ID, "terminal_credential"));

    public static final StreamCodec<ByteBuf, YunpuCredentialPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, YunpuCredentialPayload::pos,
            PayloadCodecs.PLATFORM, YunpuCredentialPayload::platform,
            PayloadCodecs.AUTH, YunpuCredentialPayload::auth,
            ByteBufCodecs.BOOL, YunpuCredentialPayload::store,
            YunpuCredentialPayload::new);

    public static void handle(YunpuCredentialPayload payload, IPayloadContext context) {
        if (!context.flow().isServerbound()) {
            return;
        }
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof YunpuMenu menu) || !menu.pos().equals(payload.pos())) {
                YunpuMusic.LOGGER.warn("忽略来自 {} 的非终端凭据写入请求", player.getGameProfile().getName());
                return;
            }
            Role role = Role.resolve(player);
            if (!role.canStoreOnServer()) {
                player.displayClientMessage(Component.translatable("gui.yunpumusic.server_store.denied"), false);
                return;
            }
            if (payload.store()) {
                ServerCredentialStore.put(player.server, payload.platform(), payload.auth());
                player.displayClientMessage(Component.translatable("gui.yunpumusic.server_store.saved",
                        payload.platform().displayName()), false);
            } else {
                ServerCredentialStore.remove(player.server, payload.platform());
                player.displayClientMessage(Component.translatable("gui.yunpumusic.server_store.cleared",
                        payload.platform().displayName()), false);
            }
            com.yunpu.yunpumusic.network.YunpuNetwork.sendInit(player, payload.pos());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
