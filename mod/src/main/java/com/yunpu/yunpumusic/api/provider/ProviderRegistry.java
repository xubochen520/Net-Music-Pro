package com.yunpu.yunpumusic.api.provider;

import com.yunpu.yunpumusic.api.IMusicProvider;
import com.yunpu.yunpumusic.api.PlatformId;

import java.util.EnumMap;
import java.util.Map;

/** 平台适配器注册表：静态持有三个平台的实现，供服务端与客户端共用。 */
public final class ProviderRegistry {
    private static final Map<PlatformId, IMusicProvider> PROVIDERS = new EnumMap<>(PlatformId.class);

    static {
        register(new NetEaseProvider());
        register(new QqProvider());
        register(new KugouProvider());
    }

    private ProviderRegistry() {
    }

    public static void register(IMusicProvider provider) {
        PROVIDERS.put(provider.platform(), provider);
    }

    public static IMusicProvider get(PlatformId platform) {
        IMusicProvider provider = PROVIDERS.get(platform);
        if (provider == null) {
            throw new IllegalStateException("未注册的音乐平台适配器：" + platform);
        }
        return provider;
    }

    public static Map<PlatformId, IMusicProvider> all() {
        return Map.copyOf(PROVIDERS);
    }
}
