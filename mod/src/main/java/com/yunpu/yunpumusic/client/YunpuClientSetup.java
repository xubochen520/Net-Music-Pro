package com.yunpu.yunpumusic.client;

import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.client.audio.YunpuStreamHandler;
import com.yunpu.yunpumusic.init.YunpuMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.concurrent.atomic.AtomicBoolean;


/** 客户端注册入口：菜单界面与自定义音频协议处理器。 */
@EventBusSubscriber(modid = YunpuMusic.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class YunpuClientSetup {
    private static final AtomicBoolean STREAM_HANDLER_REGISTERED = new AtomicBoolean();

    private YunpuClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(YunpuMenus.YUNPU_TERMINAL.get(), YunpuTerminalScreen::new);
    }

    /**
     * 注册云谱占位 URL 的音频流处理器。
     *
     * <p>必须在客户端启动阶段完成：Net Music 会在加载完成事件里冻结处理器列表，
     * 之后再注册就无效了。
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!STREAM_HANDLER_REGISTERED.compareAndSet(false, true)) {
                return;
            }
            com.github.tartaricacid.netmusic.client.api.AudioStreamHandlerManager
                    .registerHandler(new YunpuStreamHandler());
            YunpuMusic.LOGGER.info("云谱刻录台已接管云谱唱片的占位 URL");
        });
    }
}
