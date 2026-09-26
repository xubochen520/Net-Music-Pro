package com.yunpu.yunpumusic.api;

import java.nio.charset.StandardCharsets;

/** URL 编码工具（JDK 的 URLEncoder 是表单语义，会把空格编成 +，这里统一成 %20）。 */
public final class UrlEncoder {
    private UrlEncoder() {
    }

    public static String encode(String text) {
        if (text == null) {
            return "";
        }
        return java.net.URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
