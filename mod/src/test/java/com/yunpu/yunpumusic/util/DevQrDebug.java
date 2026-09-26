package com.yunpu.yunpumusic.util;

import java.util.ArrayList;
import java.util.List;

/** 开发期调试辅助：把矩阵恢复成「去掉掩码」的状态，便于与参考实现逐位比对。 */
public final class DevQrDebug {
    private DevQrDebug() {
    }

    public static int ourMask(String text, QrCode.Ecc ecc) {
        return QrCode.encode(text, ecc).getAppliedMask();
    }

    public static int penalty(QrCode qr) {
        return qr.getPenalty();
    }

    public static List<String> unmask(QrCode qr, int mask) {
        List<String> out = new ArrayList<>();
        for (int y = 0; y < qr.getSize(); y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < qr.getSize(); x++) {
                boolean dark = qr.isDark(x, y);
                if (!qr.isFunctionModule(x, y) && maskCondition(mask, x, y)) {
                    dark = !dark;
                }
                sb.append(dark ? '1' : '0');
            }
            out.add(sb.toString());
        }
        return out;
    }

    public static List<String> unmaskText(List<String> rows, int size, int mask) {
        // 需要功能图案信息，用一个同尺寸的空白 QR 取不到，这里用掩码公式直接判断：
        // 功能图案区域保持原样（用参考实现的功能图案位置近似：外框 + 时序 + 定位 + 校正 + 格式位）
        boolean[][] fn = functionMap(size);
        List<String> out = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < size; x++) {
                boolean dark = rows.get(y).charAt(x) == '1';
                if (!fn[y][x] && maskCondition(mask, x, y)) {
                    dark = !dark;
                }
                sb.append(dark ? '1' : '0');
            }
            out.add(sb.toString());
        }
        return out;
    }

    /** 用「以同版本空数据编码」的方式生成功能图案位置表。 */
    private static boolean[][] functionMap(int size) {
        int version = (size - 17) / 4;
        QrCode probe = QrCode.encode("", QrCode.Ecc.L, version);
        boolean[][] fn = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                fn[y][x] = probe.isFunctionModule(x, y);
            }
        }
        return fn;
    }

    private static boolean maskCondition(int mask, int x, int y) {
        return switch (mask) {
            case 0 -> (x + y) % 2 == 0;
            case 1 -> y % 2 == 0;
            case 2 -> x % 3 == 0;
            case 3 -> (x + y) % 3 == 0;
            case 4 -> (x / 3 + y / 2) % 2 == 0;
            case 5 -> x * y % 2 + x * y % 3 == 0;
            case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
            case 7 -> ((x + y) % 2 + x * y % 3) % 2 == 0;
            default -> false;
        };
    }
}
