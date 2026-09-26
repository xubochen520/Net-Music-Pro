package com.yunpu.yunpumusic.network;

import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.PlatformId;
import com.yunpu.yunpumusic.api.Membership;
import com.yunpu.yunpumusic.client.ClientTerminalState;
import com.yunpu.yunpumusic.credentials.Role;
import com.yunpu.yunpumusic.credentials.ServerCredentialStore;
import com.yunpu.yunpumusic.menu.YunpuMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** 本模组的网络包注册与发送入口。 */
public final class YunpuNetwork {
    private static final String VERSION = "1";

    private YunpuNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToClient(YunpuInitPayload.TYPE, YunpuInitPayload.STREAM_CODEC, YunpuInitPayload::handle);
        registrar.playToServer(YunpuCredentialPayload.TYPE, YunpuCredentialPayload.STREAM_CODEC, YunpuCredentialPayload::handle);
        registrar.playToServer(YunpuBurnPayload.TYPE, YunpuBurnPayload.STREAM_CODEC, YunpuBurnPayload::handle);
        YunpuMusic.LOGGER.info("云谱刻录台网络包已注册");
    }

    /** 向玩家同步终端初始化信息（权限等级 + 服务端已存凭据的平台）。 */
    public static void sendInit(ServerPlayer player, BlockPos pos) {
        Role role = Role.resolve(player);
        Map<PlatformId, Membership> stored = new EnumMap<>(PlatformId.class);
        ServerCredentialStore.all(player.server).forEach((platform, auth) -> {
            if (auth.loggedIn()) {
                stored.put(platform, auth.membership());
            }
        });
        PacketDistributor.sendToPlayer(player, YunpuInitPayload.of(role.ordinal(), stored));
    }

    /** 客户端把本地登录态同步给同客户端的状态管理器（无网络往返）。 */
    public static void acceptLocalInit(int roleOrdinal, Set<PlatformId> stored) {
        ClientTerminalState.acceptInit(YunpuInitPayload.of(roleOrdinal, stored));
    }

    public static void burnDisc(BlockPos pos, com.yunpu.yunpumusic.api.SongRef song) {
        PacketDistributor.sendToServer(new YunpuBurnPayload(pos, song));
    }

    public static void sendToServer(BlockPos pos, PlatformId platform, com.yunpu.yunpumusic.api.AuthState auth, boolean store) {
        PacketDistributor.sendToServer(new YunpuCredentialPayload(pos, platform, auth, store));
    }
}
