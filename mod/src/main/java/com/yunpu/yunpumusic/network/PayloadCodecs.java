package com.yunpu.yunpumusic.network;

import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.PlatformId;
import com.yunpu.yunpumusic.api.QrLoginSession;
import com.yunpu.yunpumusic.api.SongRef;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 网络包用的编解码器，集中在 `一处方便核对两端约定。 */
public final class PayloadCodecs {
    private PayloadCodecs() {
    }

    public static final StreamCodec<ByteBuf, SongRef> SONG = StreamCodec.of(
            (buffer, song) -> ByteBufCodecs.STRING_UTF8.encode(buffer, song.toJsonString()),
            buffer -> SongRef.fromJson(ByteBufCodecs.STRING_UTF8.decode(buffer)));

    public static final StreamCodec<ByteBuf, AuthState> AUTH = StreamCodec.of(
            (buffer, auth) -> ByteBufCodecs.STRING_UTF8.encode(buffer, auth.toJson().toString()),
            buffer -> AuthState.fromJson(com.yunpu.yunpumusic.api.Json.parse(
                    ByteBufCodecs.STRING_UTF8.decode(buffer))));

    public static final StreamCodec<ByteBuf, PlatformId> PLATFORM = StreamCodec.of(
            (buffer, platform) -> ByteBufCodecs.STRING_UTF8.encode(buffer, platform.id()),
            buffer -> PlatformId.byId(ByteBufCodecs.STRING_UTF8.decode(buffer)));

    public static final StreamCodec<ByteBuf, QrLoginSession.Status> LOGIN_STATUS = StreamCodec.of(
            (buffer, status) -> ByteBufCodecs.VAR_INT.encode(buffer, status.ordinal()),
            buffer -> QrLoginSession.Status.values()[ByteBufCodecs.VAR_INT.decode(buffer)]);
}
