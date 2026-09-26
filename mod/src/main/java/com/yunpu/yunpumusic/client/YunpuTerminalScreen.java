package com.yunpu.yunpumusic.client;

import com.yunpu.yunpumusic.api.AuthState;
import com.yunpu.yunpumusic.api.Membership;
import com.yunpu.yunpumusic.api.Platform;
import com.yunpu.yunpumusic.api.PlatformId;
import com.yunpu.yunpumusic.api.QrLoginSession;
import com.yunpu.yunpumusic.api.SongRef;
import com.yunpu.yunpumusic.menu.YunpuMenu;
import com.yunpu.yunpumusic.network.YunpuNetwork;
import com.yunpu.yunpumusic.util.QrCode;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

/** Compact music library and a separate, usable disc crafting page. */
@OnlyIn(Dist.CLIENT)
public class YunpuTerminalScreen extends AbstractContainerScreen<YunpuMenu> {
    private static final int PANEL = 0xFF101820;
    private static final int SURFACE = 0xFF192632;
    private static final int HOVER = 0xFF25394A;
    private static final int BORDER = 0xFF344858;
    private static final int TEXT = 0xFFE7F1F3;
    private static final int MUTED = 0xFF92A4AF;
    private static final int CYAN = 0xFF72D4D4;
    private static final int COPPER = 0xFFBD673B;
    private static final int GREEN = 0xFF7BD6A9;
    private static final int VIP = 0xFFEF625C;
    private static final int ROWS = 5;
    private static final int ROW_HEIGHT = 18;
    private static final Platform[] PLATFORMS = Platform.values();

    private PlatformId platform = PlatformId.KUGOU;
    @Nullable private SongRef selected;
    private String query = "";
    private boolean queryFocused;
    private boolean showQr;
    private int searchCooldown;
    private int scroll;
    private long openedAt = -1;
    private long switchAt;
    private int tabFrom;
    private int tabTo;
    private int colorFrom = Platform.KUGOU.accent();
    private int colorTo = Platform.KUGOU.accent();

    public YunpuTerminalScreen(YunpuMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 392;
        imageHeight = 216;
    }

    @Override
    protected void init() {
        super.init();
        if (openedAt < 0) openedAt = Util.getMillis();
        if (switchAt == 0) {
            tabFrom = 8;
            tabTo = 8;
        }
        ClientTerminalState.setMenuRole(menu.role());
    }

    private int x(int offset) { return leftPos + offset; }
    private int y(int offset) { return topPos + offset; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!opening()) {
            ClientTerminalState.tickLogin(platform);
            if (searchCooldown > 0 && --searchCooldown == 0 && query.length() >= 2) {
                ClientTerminalState.search(platform, query);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (showQr && !opening()) renderQr(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0081016);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (opening()) {
            renderOpening(graphics);
            return;
        }
        graphics.fill(x(-2), y(-2), x(394), y(218), COPPER);
        graphics.fill(x(0), y(0), x(392), y(216), PANEL);
        graphics.fill(x(0), y(0), x(392), y(23), SURFACE);
        graphics.fill(x(0), y(23), x(392), y(24), BORDER);
        graphics.drawString(font, Component.translatable("gui.yunpumusic.title"), x(9), y(8), CYAN, false);
        renderAccount(graphics);
        renderTabs(graphics, mouseX, mouseY);
        if (menu.burnPage()) renderBurnPage(graphics, mouseX, mouseY);
        else renderLibrary(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }

    private boolean opening() { return Util.getMillis() - openedAt < 1250; }

    private void renderOpening(GuiGraphics g) {
        g.fill(x(42), y(60), x(350), y(156), COPPER);
        g.fill(x(43), y(61), x(349), y(155), PANEL);
        g.drawString(font, Component.translatable("gui.yunpumusic.title"), x(57), y(74), CYAN, false);
        String line = Component.translatable("gui.yunpumusic.boot.connecting").getString();
        long elapsed = Math.max(0, Util.getMillis() - openedAt);
        int chars = (int) Math.min(line.length(), elapsed * line.length() / 950);
        String cursor = (elapsed / 220) % 2 == 0 ? "▌" : " ";
        g.drawString(font, "> " + line.substring(0, chars) + cursor, x(57), y(104), TEXT, false);
        g.fill(x(57), y(135), x(335), y(138), BORDER);
        g.fill(x(57), y(135), x(57 + (int) (278 * Math.min(elapsed, 1100) / 1100)), y(138), CYAN);
    }

    private void renderAccount(GuiGraphics g) {
        AuthState auth = ClientTerminalState.auth(platform);
        String account = auth != null && auth.loggedIn() ? auth.displayName()
                : ClientTerminalState.serverStored(platform) ? "服务端已登录" : "未登录";
        if (account.isBlank()) account = "已登录";
        Membership accountTier = auth != null && auth.loggedIn()
                ? auth.membership() : ClientTerminalState.serverMembership(platform);
        String tier = accountTier == Membership.UNKNOWN && !ClientTerminalState.available(platform)
                ? "" : accountTier.label();
        int right = 384;
        String role = ClientTerminalState.role().displayName().getString();
        g.drawString(font, role, x(right - font.width(role)), y(8), ClientTerminalState.role().color(), false);
        right -= font.width(role) + 11;
        if (!tier.isEmpty()) {
            g.drawString(font, tier, x(right - font.width(tier)), y(8), VIP, false);
            right -= font.width(tier) + 8;
        }
        g.drawString(font, trim(account, Math.max(30, right - 135)), x(132), y(8), MUTED, false);
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        int[] positions = {8, 104, 200};
        for (int i = 0; i < PLATFORMS.length; i++) {
            Platform p = PLATFORMS[i];
            PlatformId id = PlatformId.byId(p.id());
            int left = positions[i];
            boolean current = !menu.burnPage() && id == platform;
            g.fill(x(left), y(29), x(left + 92), y(48), current ? HOVER : SURFACE);
            g.drawString(font, p.displayName(), x(left + 8), y(35), current ? TEXT : MUTED, false);
            AuthState tabAuth = ClientTerminalState.auth(id);
            Membership tabTier = tabAuth != null && tabAuth.loggedIn()
                    ? tabAuth.membership() : ClientTerminalState.serverMembership(id);
            String tier = ClientTerminalState.available(id) ? switch (tabTier) {
                case VIP -> "VIP";
                case SVIP -> "SVIP";
                case FREE -> "普通";
                case UNKNOWN -> "?";
            } : "·";
            int tierColor = tabTier == Membership.VIP || tabTier == Membership.SVIP
                    ? VIP : ClientTerminalState.available(id) ? GREEN : BORDER;
            g.drawString(font, tier, x(left + 87 - font.width(tier)), y(35), tierColor, false);
        }
        button(g, 300, 29, 84, 19, "刻录唱片", mx, my, menu.burnPage() ? CYAN : COPPER);
        // Cubic Bezier (0.22, 1, 0.36, 1) moves the active underline between tabs.
        double t = Math.min(1, Math.max(0, (Util.getMillis() - switchAt) / 300.0));
        double eased = bezierEase(t);
        int underline = (int) Math.round(tabFrom + (tabTo - tabFrom) * eased);
        int style = blend(colorFrom, colorTo, eased);
        g.fill(x(underline), y(46), x(underline + (tabTo == 300 ? 84 : 92)), y(48), style);
        g.fill(x(8), y(50), x(384), y(51), style);
    }

    private void renderLibrary(GuiGraphics g, int mx, int my) {
        int searchColor = queryFocused ? HOVER : SURFACE;
        g.fill(x(8), y(53), x(384), y(72), searchColor);
        g.fill(x(8), y(71), x(384), y(72), queryFocused ? CYAN : BORDER);
        String shown = query.isEmpty() ? "搜索歌曲或歌手..." : query;
        g.drawString(font, trim(shown, 276), x(15), y(59), query.isEmpty() ? MUTED : TEXT, false);
        if (queryFocused && Util.getMillis() / 500 % 2 == 0) {
            int caret = Math.min(font.width(query), 275);
            g.fill(x(15 + caret), y(58), x(16 + caret), y(68), CYAN);
        }
        int count = ClientTerminalState.results(platform).size();
        String result = ClientTerminalState.searching(platform) ? "搜索中..." : count + " 条";
        g.drawString(font, result, x(378 - font.width(result)), y(59), MUTED, false);
        List<SongRef> songs = ClientTerminalState.results(platform);
        if (songs.isEmpty()) {
            String message = ClientTerminalState.searchError(platform).isBlank()
                    ? "输入关键词，搜索当前平台曲库" : ClientTerminalState.searchError(platform);
            g.drawCenteredString(font, trim(message, 320), x(196), y(112), MUTED);
        }
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= songs.size()) break;
            SongRef song = songs.get(index);
            int top = 76 + row * ROW_HEIGHT;
            boolean active = selected != null && selected.key().equals(song.key());
            boolean hover = inside(mx, my, x(8), y(top), 376, ROW_HEIGHT);
            g.fill(x(8), y(top), x(384), y(top + ROW_HEIGHT - 1),
                    active ? HOVER : hover ? 0xFF1E303D : row % 2 == 0 ? SURFACE : PANEL);
            if (active) g.fill(x(8), y(top), x(10), y(top + ROW_HEIGHT - 1), CYAN);
            g.drawString(font, trim(song.title(), 173), x(16), y(top + 4), TEXT, false);
            g.drawString(font, trim(song.artistText(), 100), x(204), y(top + 4), MUTED, false);
            g.drawString(font, song.durationText(), x(315), y(top + 4), MUTED, false);
            if (song.vip()) g.drawString(font, "[VIP]", x(353), y(top + 4), VIP, false);
        }
        if (songs.size() > ROWS) {
            g.fill(x(382), y(76), x(384), y(166), BORDER);
            int thumb = Math.max(10, 90 * ROWS / songs.size());
            int top = 76 + (90 - thumb) * scroll / Math.max(1, songs.size() - ROWS);
            g.fill(x(382), y(top), x(384), y(top + thumb), CYAN);
        }
        g.fill(x(8), y(170), x(384), y(189), SURFACE);
        g.drawString(font, selected == null ? "请选择一首歌曲" : trim(selected.title() + " · " + selected.artistText(), 210),
                x(15), y(176), selected == null ? MUTED : TEXT, false);
        button(g, 242, 172, 136, 15, "去刻录", mx, my, COPPER);
        renderFooter(g, mx, my);
    }

    private void renderFooter(GuiGraphics g, int mx, int my) {
        g.fill(x(8), y(193), x(384), y(211), 0xFF0B1219);
        button(g, 10, 195, 64, 14, ClientTerminalState.loggedIn(platform) ? "退出登录" : "扫码登录",
                mx, my, CYAN);
        if (ClientTerminalState.canStoreOnServer()) {
            button(g, 79, 195, 72, 14, ClientTerminalState.serverStored(platform) ? "取消同步" : "同步账号",
                    mx, my, COPPER);
        }
        g.drawString(font, trim("选曲后放入唱片，前往刻录", 218), x(159), y(198), MUTED, false);
    }

    private void renderBurnPage(GuiGraphics g, int mx, int my) {
        g.fill(x(8), y(53), x(384), y(87), SURFACE);
        g.drawString(font, "当前曲目", x(17), y(59), MUTED, false);
        g.drawString(font, selected == null ? "请在曲库选择歌曲" : trim(selected.title(), 290),
                x(17), y(72), selected == null ? MUTED : TEXT, false);
        if (selected != null && selected.vip()) g.drawString(font, "【vip】", x(339), y(72), VIP, false);
        g.drawString(font, "唱片（可覆盖）", x(12), y(91), MUTED, false);
        g.drawString(font, "成品", x(126), y(91), MUTED, false);
        g.fill(x(41), y(100), x(60), y(119), BORDER);
        g.fill(x(42), y(101), x(59), y(118), 0xFF071019);
        g.fill(x(132), y(100), x(151), y(119), BORDER);
        g.fill(x(133), y(101), x(150), y(118), 0xFF071019);
        g.drawString(font, "→", x(87), y(104), CYAN, false);
        button(g, 206, 99, 168, 20, "制作唱片", mx, my, menu.canBurn() && selected != null ? COPPER : BORDER);
        g.drawString(font, "背包", x(14), y(124), MUTED, false);
        g.fill(x(10), y(135), x(174), y(211), SURFACE);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            slotFrame(g, 13 + col * 18, 136 + row * 18);
        }
        for (int col = 0; col < 9; col++) slotFrame(g, 13 + col * 18, 193);
        g.fill(x(191), y(133), x(384), y(211), SURFACE);
        g.drawString(font, "刻录流程", x(202), y(143), CYAN, false);
        g.drawString(font, "1  放入空白或已刻录唱片", x(202), y(159), MUTED, false);
        g.drawString(font, "2  选择歌曲并点击制作", x(202), y(173), MUTED, false);
        g.drawString(font, "3  从右侧取出成品", x(202), y(187), MUTED, false);
        String status = selected == null ? "等待选择歌曲" : menu.canBurn() ? "可以制作" : "需要唱片 / 清空成品槽";
        g.drawString(font, trim(status, 170), x(202), y(201), menu.canBurn() ? GREEN : VIP, false);
    }

    private void slotFrame(GuiGraphics g, int sx, int sy) {
        g.fill(x(sx), y(sy), x(sx + 18), y(sy + 18), BORDER);
        g.fill(x(sx + 1), y(sy + 1), x(sx + 17), y(sy + 17), 0xFF071019);
    }

    private void button(GuiGraphics g, int bx, int by, int bw, int bh, String label, int mx, int my, int accent) {
        boolean hover = inside(mx, my, x(bx), y(by), bw, bh);
        g.fill(x(bx), y(by), x(bx + bw), y(by + bh), hover ? accent : HOVER);
        g.drawCenteredString(font, label, x(bx + bw / 2), y(by + (bh - 8) / 2), hover ? PANEL : TEXT);
    }

    /** Solves cubic Bezier x(t), then evaluates y(t). */
    static double bezierEase(double progress) {
        double low = 0, high = 1;
        for (int i = 0; i < 14; i++) {
            double t = (low + high) * 0.5;
            double inverse = 1 - t;
            double x = 3 * inverse * inverse * t * .22 + 3 * inverse * t * t * .36 + t * t * t;
            if (x < progress) low = t; else high = t;
        }
        double t = (low + high) * .5;
        double inverse = 1 - t;
        return 3 * inverse * inverse * t + 3 * inverse * t * t + t * t * t;
    }

    private static int blend(int from, int to, double progress) {
        int rgb = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int a = (from >>> shift) & 255;
            int b = (to >>> shift) & 255;
            rgb |= (int) Math.round(a + (b - a) * progress) << shift;
        }
        return rgb;
    }

    private void switchTab(int target, @Nullable PlatformId next) {
        int current = tabTo;
        tabFrom = (int) Math.round(tabFrom + (tabTo - tabFrom)
                * bezierEase(Math.min(1, Math.max(0, (Util.getMillis() - switchAt) / 300.0))));
        if (switchAt == 0) tabFrom = current;
        colorFrom = blend(colorFrom, colorTo,
                bezierEase(Math.min(1, Math.max(0, (Util.getMillis() - switchAt) / 300.0))));
        colorTo = next == null ? CYAN : Platform.byId(next.id()).accent();
        tabTo = target;
        switchAt = Util.getMillis();
        menu.setBurnPage(next == null);
        if (next != null) {
            if (next != platform) selected = null;
            platform = next;
            scroll = 0;
            if (query.length() >= 2) ClientTerminalState.search(platform, query);
        }
        queryFocused = false;
    }

    private void renderQr(GuiGraphics g, int mx, int my) {
        int left = x(109), top = y(15), right = left + 174, bottom = top + 188;
        g.fill(x(0), y(0), x(392), y(216), 0xD9000000);
        g.fill(left - 1, top - 1, right + 1, bottom + 1, COPPER);
        g.fill(left, top, right, bottom, PANEL);
        g.drawCenteredString(font, Platform.byId(platform.id()).displayName().getString() + " · 扫码登录",
                left + 87, top + 10, CYAN);
        QrLoginSession session = ClientTerminalState.session(platform);
        int qrX = left + 27, qrY = top + 27, size = 120;
        if (session != null && !session.qrContent().isBlank()
                && session.status() != QrLoginSession.Status.FAILED) {
            try {
                QrRenderer.draw(QrCode.encode(session.qrContent(), QrCode.Ecc.M), qrX, qrY, size, size,
                        0xFF081018, 0xFFF4F7F8,
                        (px, py, w, h, color) -> g.fill(px, py, px + w, py + h, color));
            } catch (RuntimeException e) {
                g.fill(qrX, qrY, qrX + size, qrY + size, SURFACE);
                g.drawCenteredString(font, "二维码绘制失败", left + 87, qrY + 55, VIP);
            }
        } else {
            g.fill(qrX, qrY, qrX + size, qrY + size, SURFACE);
            g.drawCenteredString(font, session != null && session.status() == QrLoginSession.Status.FAILED
                    ? "二维码获取失败" : "正在获取二维码...", left + 87, qrY + 55, MUTED);
        }
        String status = session == null ? "请稍候" : session.message();
        g.drawCenteredString(font, trim(status, 158), left + 87, top + 151, MUTED);
        button(g, 121, 180, 68, 14, "刷新", mx, my, CYAN);
        button(g, 203, 180, 68, 14, "关闭", mx, my, COPPER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (opening()) return true;
        int mx = (int) mouseX, my = (int) mouseY;
        if (showQr) {
            if (inside(mx, my, x(121), y(180), 68, 14)) ClientTerminalState.startLogin(platform);
            else if (inside(mx, my, x(203), y(180), 68, 14)) {
                ClientTerminalState.cancelLogin(platform);
                showQr = false;
            }
            return true;
        }
        int[] positions = {8, 104, 200};
        for (int i = 0; i < PLATFORMS.length; i++) {
            if (inside(mx, my, x(positions[i]), y(29), 92, 19)) {
                switchTab(positions[i], PlatformId.byId(PLATFORMS[i].id()));
                return true;
            }
        }
        if (inside(mx, my, x(300), y(29), 84, 19)) {
            switchTab(300, null);
            return true;
        }
        if (menu.burnPage()) {
            if (inside(mx, my, x(206), y(99), 168, 20)) {
                if (selected != null && menu.canBurn()) YunpuNetwork.burnDisc(menu.pos(), selected);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inside(mx, my, x(8), y(53), 376, 19)) {
            queryFocused = true;
            return true;
        }
        queryFocused = false;
        int row = (my - y(76)) / ROW_HEIGHT;
        if (inside(mx, my, x(8), y(76), 376, ROWS * ROW_HEIGHT) && row >= 0) {
            List<SongRef> songs = ClientTerminalState.results(platform);
            int index = scroll + row;
            if (index < songs.size()) selected = songs.get(index);
            return true;
        }
        if (inside(mx, my, x(242), y(172), 136, 15)) {
            switchTab(300, null);
            return true;
        }
        if (inside(mx, my, x(10), y(195), 64, 14)) {
            if (ClientTerminalState.loggedIn(platform)) ClientTerminalState.logout(platform);
            else { showQr = true; ClientTerminalState.startLogin(platform); }
            return true;
        }
        if (ClientTerminalState.canStoreOnServer() && inside(mx, my, x(79), y(195), 72, 14)) {
            AuthState auth = ClientTerminalState.auth(platform);
            if (auth != null && auth.loggedIn())
                YunpuNetwork.sendToServer(menu.pos(), platform, auth, !ClientTerminalState.serverStored(platform));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.burnPage() || showQr) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int maximum = Math.max(0, ClientTerminalState.results(platform).size() - ROWS);
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maximum);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && showQr) {
            ClientTerminalState.cancelLogin(platform);
            showQr = false;
            return true;
        }
        if (queryFocused && !menu.burnPage()) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                searchCooldown = 20;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                ClientTerminalState.search(platform, query);
                searchCooldown = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && minecraft != null) {
                insert(minecraft.keyboardHandler.getClipboard());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (queryFocused && !menu.burnPage() && !Character.isISOControl(codePoint)) {
            insert(String.valueOf(codePoint));
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void insert(String value) {
        if (value == null) return;
        query = (query + value.replaceAll("[\\p{Cntrl}]", ""));
        if (query.length() > 64) query = query.substring(0, 64);
        searchCooldown = 20;
    }

    private String trim(String value, int maxWidth) {
        if (value == null) return "";
        String flat = value.replace('\n', ' ');
        if (font.width(flat) <= maxWidth) return flat;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < flat.length(); i++) {
            if (font.width(out.toString() + flat.charAt(i)) > maxWidth - 7) break;
            out.append(flat.charAt(i));
        }
        return out + "…";
    }

    private static boolean inside(int mx, int my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
