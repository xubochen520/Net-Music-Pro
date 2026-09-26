package com.yunpu.yunpumusic.api;

import java.util.List;

/**
 * 各音乐平台适配器。
 *
 * <p>实现必须保持「纯 JDK + Gson」：不引用任何 Minecraft 类型，
 * 这样适配层可以在没有游戏环境的条件下直接跑网络测试。
 */
public interface IMusicProvider {

    PlatformId platform();

    /** 发起一次扫码登录，返回的会话对象需要由调用方定期 {@link #pollLogin(QrLoginSession)}。 */
    QrLoginSession startLogin() throws Exception;

    /**
     * 轮询登录状态，内部更新 {@code session} 的状态并在成功时填充 {@link QrLoginSession#auth()}。
     *
     * @return 更新后的同一个会话对象
     */
    QrLoginSession pollLogin(QrLoginSession session) throws Exception;

    /**
     * 关键词模糊搜索（歌曲名 / 歌手 / 专辑均可）。
     *
     * @param keyword 关键词
     * @param limit   期望返回条数
     * @param auth    登录态，可为 {@code null}（未登录也能搜到公开曲库，只是拿不到会员直链）
     */
    List<SongRef> search(String keyword, int limit, AuthState auth) throws Exception;

    /**
     * 解析可播放直链。
     *
     * @return 直链；无法解析（例如无会员权限）时返回 {@code null}
     */
    String resolveStreamUrl(SongRef song, AuthState auth) throws Exception;

    /** 拉取 LRC 歌词文本（可能包含翻译行），失败返回空字符串。 */
    String fetchLyric(SongRef song, AuthState auth) throws Exception;

    /** Recheck the account's current membership; return UNKNOWN on ambiguous responses. */
    default Membership membership(AuthState auth) throws Exception { return Membership.UNKNOWN; }
}
