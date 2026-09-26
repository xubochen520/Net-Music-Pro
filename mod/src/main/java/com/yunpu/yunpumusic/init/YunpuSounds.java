package com.yunpu.yunpumusic.init;

import com.yunpu.yunpumusic.YunpuMusic;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YunpuSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, YunpuMusic.MOD_ID);

    /**
     * 登录成功提示音。指向模组自带的音频文件，
     * 没有音频资源时客户端会静默跳过，不影响功能。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> LOGIN_SUCCESS = SOUNDS.register("login_success",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(YunpuMusic.MOD_ID, "login_success")));

    private YunpuSounds() {
    }
}
