package com.yunpu.yunpumusic.api;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本模组自定义的音频地址协议。
 *
 * <p>当刻录台把曲目加入播放队列时，会使用
 * {@code https://yunpu.invalid/<platform>/<songId>?k=v} 作为「待解析地址」。
 * 客户端在真正读取音频流时，用当前登录态去平台换取直链，
 * 这样会员曲目也能在本地解码播放，而且不会把带鉴权的临时直链写进存档。
 */
public final class YunpuUrl {
    // Net Music first converts the saved address to java.net.URL. A custom scheme fails
    // at that step, so use the reserved .invalid host and intercept it before HTTP.
    public static final String PREFIX = "https://yunpu.invalid/";

    private YunpuUrl() {
    }

    public static String build(SongRef song) {
        StringBuilder sb = new StringBuilder(PREFIX)
                .append(song.platform().id()).append('/')
                .append(encode(song.id()));
        Map<String, String> params = new LinkedHashMap<>(song.extras());
        if (!song.title().isBlank()) {
            params.put("title", song.title());
        }
        params.put("duration", String.valueOf(song.durationSeconds()));
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            sb.append(first ? '?' : '&');
            first = false;
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    public static boolean matches(String url) {
        return url != null && url.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /** 解析出平台与歌曲 ID；格式不合法时返回 {@code null}。 */
    public static SongRef parse(String url) {
        if (!matches(url)) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceFirst("^/", "");
            int split = path.indexOf('/');
            if (!"yunpu.invalid".equalsIgnoreCase(uri.getHost()) || split <= 0 || split == path.length() - 1) {
                return null;
            }
            String platformId = path.substring(0, split);
            Map<String, String> extras = new LinkedHashMap<>();
            String query = uri.getRawQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        extras.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
                    }
                }
            }
            PlatformId platform = PlatformId.byId(platformId);
            String id = decode(path.substring(split + 1));
            String title = extras.getOrDefault("title", "");
            int duration = 0;
            try {
                duration = Integer.parseInt(extras.getOrDefault("duration", "0"));
            } catch (NumberFormatException ignored) {
                // 非关键字段，忽略
            }
            return new SongRef(platform, id, title.isBlank() ? id : title,
                    java.util.List.of(), "", duration, false, 0, extras);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String text) {
        return java.net.URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}
