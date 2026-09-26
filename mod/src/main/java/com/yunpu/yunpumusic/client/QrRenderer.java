package com.yunpu.yunpumusic.client;

import com.yunpu.yunpumusic.util.QrCode;

/**
 * 把 {@link QrCode} 的模块矩阵渲染到界面上。
 *
 * <p>不生成贴图，直接按模块画方块——既避免了运行时创建纹理的开销，
 * 也保证二维码在任何 GUI 缩放下都是清晰的锐利像素。
 */
public final class QrRenderer {
    private QrRenderer() {
    }

    /**
     * 在指定区域居中绘制二维码。
     *
     * @param qr      已编码的二维码
     * @param x       区域左上角 x
     * @param y       区域左上角 y
     * @param width   区域宽
     * @param height  区域高
     * @param dark    深色模块颜色
     * @param light   浅色模块颜色（作为底板绘制）
     * @param fill    实际画点的回调：{@code (x, y, w, h) -> void}
     * @return 实际绘制的边长（像素），失败返回 0
     */
    public static int draw(QrCode qr, int x, int y, int width, int height,
                           int dark, int light, PixelWriter fill) {
        if (qr == null) {
            return 0;
        }
        int quiet = 2;
        int modules = qr.getSize() + quiet * 2;
        int scale = Math.max(1, Math.min(width, height) / modules);
        int drawn = modules * scale;
        int originX = x + (width - drawn) / 2;
        int originY = y + (height - drawn) / 2;

        // 底板（含静默区）
        fill.write(originX, originY, drawn, drawn, light);

        for (int my = 0; my < qr.getSize(); my++) {
            for (int mx = 0; mx < qr.getSize(); mx++) {
                if (!qr.isDark(mx, my)) {
                    continue;
                }
                int px = originX + (mx + quiet) * scale;
                int py = originY + (my + quiet) * scale;
                fill.write(px, py, scale, scale, dark);
            }
        }
        return drawn;
    }

    /** 绘制矩形的最小回调接口，便于在无渲染环境下测试。 */
    @FunctionalInterface
    public interface PixelWriter {
        void write(int x, int y, int width, int height, int color);
    }
}
