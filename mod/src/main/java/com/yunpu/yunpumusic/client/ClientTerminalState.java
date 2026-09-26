package com.yunpu.yunpumusic.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.yunpu.yunpumusic.YunpuMusic;
import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.IMusicProvider;
import com.yunpu.yunpumusic.api.Membership;
import com.yunpu.yunpumusic.api.PlatformId;
import com.yunpu.yunpumusic.api.QrLoginSession;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.api.provider.ProviderRegistry;
import com.yunpu.yunpumusic.credentials.LocalCredentialStore;
import com.yunpu.yunpumusic.credentials.Role;
import com.yunpu.yunpumusic.network.YunpuInitPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端终端状态：扫码轮询与模糊搜索
 * 都异步执行，界面只读取这里缓存的结果，保证 GUI 永不阻塞。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientTerminalState {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4,
            new ThreadFactoryBuilder().setNameFormat("yunpu-terminal-%d").setDaemon(true).build());

    private static final Map<PlatformId, QrLoginSession> LOGIN_SESSIONS = new ConcurrentHashMap<>();
    private static final Set<QrLoginSession> POLLING = ConcurrentHashMap.newKeySet();
    private static final Map<PlatformId, AuthState> AUTH = new ConcurrentHashMap<>();
    private static final Set<PlatformId> MEMBERSHIP_CHECKING = ConcurrentHashMap.newKeySet();
    private static final Map<PlatformId, Long> MEMBERSHIP_CHECKED_AT = new ConcurrentHashMap<>();
    private static final Map<PlatformId, List<SongRef>> RESULTS = new ConcurrentHashMap<>();
    private static final Map<PlatformId, AtomicInteger> SEARCH_VERSIONS = new ConcurrentHashMap<>();
    private static final Map<PlatformId, Boolean> SEARCHING = new ConcurrentHashMap<>();
    private static final Map<PlatformId, String> SEARCH_ERRORS = new ConcurrentHashMap<>();

    private static Role role = Role.PLAYER;
    private static final Set<PlatformId> SERVER_STORED = EnumSet.noneOf(PlatformId.class);
    private static final Map<PlatformId, Membership> SERVER_MEMBERSHIP = new ConcurrentHashMap<>();

    private static String lastQuery = "";
    private ClientTerminalState() {
    }

    // ------------------------------------------------------------------
    // 服务端 / 菜单同步
    // ------------------------------------------------------------------

    public static void acceptInit(YunpuInitPayload payload) {
        role = Role.byOrdinal(payload.roleOrdinal());
        SERVER_STORED.clear();
        SERVER_MEMBERSHIP.clear();
        for (PlatformId platform : PlatformId.values()) {
            if (payload.stored(platform)) {
                SERVER_STORED.add(platform);
            }
        }
        for (String entry : payload.platformNames().split(",")) {
            String[] pair = entry.split(":", 2);
            if (pair.length != 2) continue;
            try {
                PlatformId id = PlatformId.byId(pair[0]);
                SERVER_MEMBERSHIP.put(id, Membership.valueOf(pair[1]));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public static void setMenuRole(Role menuRole) {
        // 菜单数据槽只作为兜底，真正的角色以初始化包为准
        if (role == Role.PLAYER) {
            role = menuRole;
        }
    }

    public static Role role() {
        return role;
    }

    public static boolean serverStored(PlatformId platform) {
        return SERVER_STORED.contains(platform);
    }

    public static Membership serverMembership(PlatformId platform) {
        return SERVER_MEMBERSHIP.getOrDefault(platform, Membership.UNKNOWN);
    }

    public static boolean canStoreOnServer() {
        return role.canStoreOnServer();
    }

    // ------------------------------------------------------------------
    // 登录态
    // ------------------------------------------------------------------

    @Nullable
    public static AuthState auth(PlatformId platform) {
        LocalCredentialStore.load();
        AuthState local = AUTH.get(platform);
        if (local != null) {
            refreshMembership(platform, local);
            return local;
        }
        AuthState loaded = LocalCredentialStore.get(platform);
        if (loaded != null) {
            AUTH.put(platform, loaded);
            refreshMembership(platform, loaded);
        }
        return loaded;
    }

    public static boolean loggedIn(PlatformId platform) {
        AuthState auth = auth(platform);
        return auth != null && auth.loggedIn();
    }

    /** 兼容服务端已有凭据的情况：界面显示为「服务端已登录」。 */
    public static boolean available(PlatformId platform) {
        return loggedIn(platform) || serverStored(platform);
    }

    @Nullable
    public static QrLoginSession session(PlatformId platform) {
        return LOGIN_SESSIONS.get(platform);
    }

    public static void startLogin(PlatformId platform) {
        IMusicProvider provider = ProviderRegistry.get(platform);
        QrLoginSession session = new QrLoginSession(platform);
        LOGIN_SESSIONS.put(platform, session);
        EXECUTOR.submit(() -> {
            try {
                QrLoginSession created = provider.startLogin();
                // 刷新/关闭弹窗后，旧请求不能覆盖新会话。
                LOGIN_SESSIONS.replace(platform, session, created);
            } catch (Exception e) {
                YunpuMusic.LOGGER.warn("发起扫码登录失败：{}", platform.id(), e);
                if (LOGIN_SESSIONS.get(platform) == session) {
                    session.setStatus(QrLoginSession.Status.FAILED)
                            .setMessage(shortMessage(e));
                }
            }
        });
    }

    public static void cancelLogin(PlatformId platform) {
        QrLoginSession session = LOGIN_SESSIONS.remove(platform);
        if (session != null) {
            session.setStatus(QrLoginSession.Status.CANCELLED).setMessage("已取消");
        }
    }

    /** 由界面每 tick 调用：推进一次扫码轮询。 */
    public static void tickLogin(PlatformId platform) {
        QrLoginSession session = LOGIN_SESSIONS.get(platform);
        if (session == null || session.finished()) {
            return;
        }
        if (session.status() == QrLoginSession.Status.REQUESTING) {
            return;
        }
        if (!POLLING.add(session)) {
            return;
        }
        synchronized (session) {
            long now = System.currentTimeMillis();
            if (now - session.createdAt() < 900) {
                POLLING.remove(session);
                return;
            }
            session.setCreatedAt(now);
        }
        IMusicProvider provider = ProviderRegistry.get(platform);
        EXECUTOR.submit(() -> {
            try {
                if (LOGIN_SESSIONS.get(platform) != session) {
                    return;
                }
                QrLoginSession polled = provider.pollLogin(session);
                if (LOGIN_SESSIONS.get(platform) == session
                    && polled.status() == QrLoginSession.Status.SUCCESS && polled.auth() != null) {
                    AUTH.put(platform, polled.auth());
                    LocalCredentialStore.put(platform, polled.auth());
                    refreshMembership(platform, polled.auth());
                }
            } catch (Exception e) {
                YunpuMusic.LOGGER.debug("扫码轮询失败：{}", platform.id(), e);
                if (LOGIN_SESSIONS.get(platform) == session) {
                    session.setMessage(shortMessage(e));
                }
            } finally {
                POLLING.remove(session);
            }
        });
    }

    public static void logout(PlatformId platform) {
        AUTH.remove(platform);
        MEMBERSHIP_CHECKED_AT.remove(platform);
        LocalCredentialStore.remove(platform);
        LOGIN_SESSIONS.remove(platform);
        SEARCH_VERSIONS.computeIfAbsent(platform, unused -> new AtomicInteger()).incrementAndGet();
        SEARCHING.remove(platform);
        SEARCH_ERRORS.remove(platform);
        RESULTS.remove(platform);
    }

    private static void refreshMembership(PlatformId platform, AuthState auth) {
        if (!auth.loggedIn()) return;
        long now = System.currentTimeMillis();
        if (now - MEMBERSHIP_CHECKED_AT.getOrDefault(platform, 0L) < 15 * 60_000L
                || !MEMBERSHIP_CHECKING.add(platform)) return;
        MEMBERSHIP_CHECKED_AT.put(platform, now);
        EXECUTOR.submit(() -> {
            try {
                Membership membership = ProviderRegistry.get(platform).membership(auth);
                if (AUTH.get(platform) == auth && membership != Membership.UNKNOWN) {
                    auth.setMembership(membership);
                    LocalCredentialStore.put(platform, auth);
                }
            } catch (Exception e) {
                YunpuMusic.LOGGER.debug("会员状态查询失败：{}", platform.id(), e);
            } finally {
                MEMBERSHIP_CHECKING.remove(platform);
            }
        });
    }

    // ------------------------------------------------------------------
    // 搜索
    // ------------------------------------------------------------------

    public static boolean searching(PlatformId platform) {
        return SEARCHING.getOrDefault(platform, false);
    }

    public static String searchError(PlatformId platform) {
        return SEARCH_ERRORS.getOrDefault(platform, "");
    }

    public static String lastQuery() {
        return lastQuery;
    }

    public static List<SongRef> results(PlatformId platform) {
        return RESULTS.getOrDefault(platform, List.of());
    }

    /** 模糊搜索：同时按歌名与歌手匹配（由各平台接口内部完成）。 */
    public static void search(PlatformId platform, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        IMusicProvider provider = ProviderRegistry.get(platform);
        AuthState auth = auth(platform);
        int version = SEARCH_VERSIONS.computeIfAbsent(platform, unused -> new AtomicInteger()).incrementAndGet();
        SEARCHING.put(platform, true);
        SEARCH_ERRORS.put(platform, "");
        lastQuery = keyword;
        EXECUTOR.submit(() -> {
            try {
                List<SongRef> songs = provider.search(keyword, 30, auth);
                if (SEARCH_VERSIONS.get(platform).get() == version)
                    RESULTS.put(platform, new ArrayList<>(songs));
            } catch (Exception e) {
                YunpuMusic.LOGGER.warn("搜索失败：{} / {}", platform.id(), keyword, e);
                if (SEARCH_VERSIONS.get(platform).get() == version) {
                    SEARCH_ERRORS.put(platform, shortMessage(e));
                    RESULTS.put(platform, List.of());
                }
            } finally {
                if (SEARCH_VERSIONS.get(platform).get() == version)
                    SEARCHING.put(platform, false);
            }
        });
    }

    private static String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 120) + "…" : message;
    }
}
