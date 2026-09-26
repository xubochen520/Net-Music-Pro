package com.yunpu.yunpumusic;

import com.yunpu.yunpumusic.init.YunpuBlocks;
import com.yunpu.yunpumusic.init.YunpuItems;
import com.yunpu.yunpumusic.init.YunpuMenus;
import com.yunpu.yunpumusic.init.YunpuSounds;
import com.yunpu.yunpumusic.network.YunpuNetwork;
import com.yunpu.yunpumusic.client.audio.YunpuDiscResolver;
import com.github.tartaricacid.netmusic.api.resolver.MusicPlayResolverManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Net Music Pro —— Net Music 附属模组。
 *
 * <p>本模组为 <a href="https://modrinth.com/mod/net-music">Net Music</a> 添加一台双格高的
 * 「云谱刻录台」方块：玩家可以通过现代化的多平台终端界面扫码登录酷狗、QQ 音乐与网易云音乐，
 * 对三个平台的曲库做歌曲名 / 歌手的模糊搜索，并在结果中直观看到 VIP 与平台标记，
 * 随后刻录到 Net Music 唱片；唱片机播放时提供歌词。
 *
 * <p>登录凭据默认只保存在玩家本地；只有服务器服主 / OP 才能把凭据写入服务端存档，
 * 供刻录机解析会员曲目。
 */
@Mod(YunpuMusic.MOD_ID)
public class YunpuMusic {
    public static final String MOD_ID = "yunpumusic";
    public static final Logger LOGGER = LoggerFactory.getLogger("NetMusicPro");

    /** 网络失败这类高频日志只在首次出现时完整打印，避免刷屏。 */
    private static final java.util.Set<String> LOGGED_ONCE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public YunpuMusic(IEventBus modEventBus, ModContainer modContainer) {
        MusicPlayResolverManager.registerResolver(new YunpuDiscResolver());
        YunpuBlocks.BLOCKS.register(modEventBus);
        YunpuBlocks.BLOCK_ENTITIES.register(modEventBus);
        YunpuItems.ITEMS.register(modEventBus);
        YunpuItems.TABS.register(modEventBus);
        YunpuMenus.MENUS.register(modEventBus);
        YunpuSounds.SOUNDS.register(modEventBus);
        modEventBus.addListener(YunpuNetwork::register);
    }

    /** 同一主题只提示一次的警告日志。 */
    public static void warnOnce(String message, Throwable throwable) {
        if (LOGGED_ONCE.add("W:" + message)) {
            LOGGER.warn(message, throwable);
        }
    }

    /** 同一主题只记录一次的调试日志。 */
    public static void debugOnce(String message, Throwable throwable) {
        if (LOGGED_ONCE.add("D:" + message)) {
            LOGGER.debug(message, throwable);
        }
    }
}
