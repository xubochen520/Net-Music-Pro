package com.yunpu.yunpumusic.api;

import com.yunpu.yunpumusic.YunpuMusic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 轻量 HTTP 客户端：三个音乐平台共用的请求工具。
 *
 * <p>刻意只依赖 JDK 自带的 {@link HttpClient}，这样第三方平台适配层完全独立于 Minecraft，
 * 既便于离线单元测试，也不会把额外的网络库带进模组 JAR。
 */
public final class Http {
    public static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/124.0.0.0 Safari/537.36";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private Http() {
    }

    public static final class Response {
        private final int status;
        private final String body;
        private final Map<String, List<String>> headers;

        Response(int status, String body, Map<String, List<String>> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }

        public int status() {
            return status;
        }

        public String body() {
            return body == null ? "" : body;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public Map<String, List<String>> headers() {
            return headers;
        }

        /** 返回响应头中所有 Set-Cookie 的键值对（只保留 name=value 部分）。 */
        public Map<String, String> cookies() {
            Map<String, String> result = new LinkedHashMap<>();
            for (String header : headers.getOrDefault("set-cookie", List.of())) {
                for (String part : header.split(";")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        String name = part.substring(0, eq).trim();
                        String value = part.substring(eq + 1).trim();
                        if (!name.isEmpty() && !value.isEmpty()) {
                            result.put(name, value);
                        }
                    }
                }
            }
            return result;
        }
    }

    public static Response get(String url, Map<String, String> headers) throws IOException {
        return send(builder(url, headers).GET().build());
    }

    public static Response postForm(String url, String form, Map<String, String> headers) throws IOException {
        return send(builder(url, headers)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build());
    }

    public static Response postJson(String url, String json, Map<String, String> headers) throws IOException {
        return send(builder(url, headers)
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build());
    }

    private static HttpRequest.Builder builder(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", headers == null
                        ? DEFAULT_UA : headers.getOrDefault("User-Agent", DEFAULT_UA))
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (value != null && !value.isEmpty() && !key.equalsIgnoreCase("User-Agent")) {
                    builder.header(key, value);
                }
            });
        }
        return builder;
    }

    private static Response send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body(), response.headers().map());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP 请求被中断：" + request.uri(), e);
        }
    }

    /** 容错解析：请求失败时返回空，交由调用方决定回退行为。 */
    public static Optional<Response> tryGet(String url, Map<String, String> headers) {
        try {
            return Optional.of(get(url, headers));
        } catch (IOException e) {
            YunpuMusic.LOGGER.debug("GET 失败：{}", url, e);
            return Optional.empty();
        }
    }
}
