package com.yunpu.yunpumusic.util;

import java.util.ArrayList;
import java.util.List;

/** 开发期调试：按标准之字形顺序从矩阵中取回码字，用于与参考实现比对。 */
public final class DevQrCodewords {
    public static void main(String[] args) {
        String text = args[0];
        int refMask = Integer.parseInt(args[1]);
        QrCode.Ecc ecc = QrCode.Ecc.valueOf(args[2]);
        java.util.List<String> rows = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            rows.add(args[i]);
        }
        int size = rows.size();

        QrCode qr = QrCode.encode(text, ecc, (size - 17) / 4);
        List<String> mineUnmasked = DevQrDebug.unmask(qr, qr.getAppliedMask());
        List<String> refUnmasked = DevQrDebug.unmaskText(rows, size, refMask);

        byte[] mine = extract(mineUnmasked, qr, size);
        byte[] ref = extract(refUnmasked, qr, size);
        System.out.println("size=" + size + " codewords=" + mine.length + " refLen=" + ref.length);
        int limit = Math.min(mine.length, ref.length);
        int diff = 0;
        for (int i = 0; i < limit; i++) {
            if (mine[i] != ref[i]) {
                if (diff < 24) {
                    System.out.printf("  cw[%d] mine=%02X ref=%02X%n", i, mine[i] & 0xFF, ref[i] & 0xFF);
                }
                diff++;
            }
        }
        System.out.println("total differing codewords: " + diff + " / " + limit);
        System.out.println("first 24 codewords mine: " + hex(mine, 24));
        System.out.println("first 24 codewords ref : " + hex(ref, 24));
    }

    private static String hex(byte[] data, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(count, data.length); i++) {
            sb.append(String.format("%02X ", data[i] & 0xFF));
        }
        return sb.toString().trim();
    }

    private static byte[] extract(List<String> rows, QrCode geom, int size) {
        List<Integer> bits = new ArrayList<>();
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (geom.isFunctionModule(x, y)) {
                        continue;
                    }
                    bits.add(rows.get(y).charAt(x) == '1' ? 1 : 0);
                }
            }
        }
        byte[] out = new byte[bits.size() / 8];
        for (int i = 0; i < out.length; i++) {
            int value = 0;
            for (int b = 0; b < 8; b++) {
                value = (value << 1) | bits.get(i * 8 + b);
            }
            out[i] = (byte) value;
        }
        return out;
    }
}
