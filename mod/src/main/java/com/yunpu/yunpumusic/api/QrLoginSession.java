package com.yunpu.yunpumusic.api;

import javax.annotation.Nullable;

/**
 * 一次扫码登录会话。
 *
 * <p>三个平台的扫码协议不同（网易是 unikey、QQ 是 qrsig/ptqrtoken、酷狗是二维码 id + token），
 * 统一抽象成「轮询 + 状态机」：界面只关心状态与二维码内容。
 */
public final class QrLoginSession {

    public enum Status {
        /** 正在获取二维码 */
        REQUESTING,
        /** 二维码已生成，等待扫码 */
        WAITING,
        /** 已扫码，等待手机端确认 */
        SCANNED,
        /** 已确认，但平台还要求二次确认（例如网易的授权提示） */
        CONFIRMING,
        /** 登录成功 */
        SUCCESS,
        /** 二维码已过期，需要刷新 */
        EXPIRED,
        /** 用户取消了登录 */
        CANCELLED,
        /** 出错 */
        FAILED
    }

    private final PlatformId platform;
    private volatile Status status = Status.REQUESTING;
    private volatile String message = "";
    private String qrContent = "";
    private String sessionKey = "";
    private String pollToken = "";
    private long createdAt = System.currentTimeMillis();
    private long expiresAt;
    @Nullable
    private volatile AuthState auth;

    public QrLoginSession(PlatformId platform) {
        this.platform = platform;
    }

    public PlatformId platform() {
        return platform;
    }

    public Status status() {
        return status;
    }

    public QrLoginSession setStatus(Status status) {
        this.status = status;
        return this;
    }

    public String message() {
        return message;
    }

    public QrLoginSession setMessage(String message) {
        this.message = message == null ? "" : message;
        return this;
    }

    /** 二维码里真正要编码的文本（通常是一个 URL）。 */
    public String qrContent() {
        return qrContent;
    }

    public QrLoginSession setQrContent(String qrContent) {
        this.qrContent = qrContent == null ? "" : qrContent;
        return this;
    }

    /** 平台返回的会话键（网易 unikey / 酷狗二维码 id）。 */
    public String sessionKey() {
        return sessionKey;
    }

    public QrLoginSession setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey == null ? "" : sessionKey;
        return this;
    }

    /** 轮询所需的附加令牌（QQ 的 qrsig）。 */
    public String pollToken() {
        return pollToken;
    }

    public QrLoginSession setPollToken(String pollToken) {
        this.pollToken = pollToken == null ? "" : pollToken;
        return this;
    }

    public long createdAt() {
        return createdAt;
    }

    public QrLoginSession setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public QrLoginSession setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public boolean expired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    public boolean finished() {
        return status == Status.SUCCESS || status == Status.EXPIRED
               || status == Status.CANCELLED || status == Status.FAILED;
    }

    @Nullable
    public AuthState auth() {
        return auth;
    }

    public QrLoginSession setAuth(AuthState auth) {
        this.auth = auth;
        return this;
    }
}
