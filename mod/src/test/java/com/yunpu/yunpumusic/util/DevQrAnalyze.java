package com.yunpu.yunpumusic.util;

/**
 * 开发期调试：对给定文本，找出参考矩阵实际使用的纠错等级与掩码，
 * 并对比未加掩码的数据模块，从而定位差异来源。
 */
public final class DevQrAnalyze {
    public static void main(String[] args) throws Exception {
        String text = args[0];
        String refRowsFile = args[1];
        java.util.List<String> rows = java.nio.file.Files.readAllLines(java.nio.file.Path.of(refRowsFile));
        int size = rows.size();
        System.out.println("reference size=" + size + " version=" + ((size - 17) / 4));

        for (QrCode.Ecc ecc : QrCode.Ecc.values()) {
            QrCode qr = QrCode.encode(text, ecc);
            if (qr.getSize() != size) {
                System.out.printf("  ecc=%s -> our version %d (size %d)%n", ecc, qr.getVersion(), qr.getSize());
                continue;
            }
            int mismatch = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    boolean ref = rows.get(y).charAt(x) == '1';
                    if (qr.isDark(x, y) != ref) {
                        mismatch++;
                    }
                }
            }
            System.out.printf("  ecc=%s version=%d mismatches=%d%n", ecc, qr.getVersion(), mismatch);
        }

        System.out.println("reference format bits (row0 cols0-8):");
        StringBuilder row0 = new StringBuilder();
        for (int x = 0; x <= 8; x++) {
            row0.append(rows.get(0).charAt(x) == '1' ? '1' : '0');
        }
        System.out.println("  row0[0..8]   = " + row0);
        StringBuilder col8 = new StringBuilder();
        for (int y = 0; y <= 8; y++) {
            col8.append(rows.get(y).charAt(8) == '1' ? '1' : '0');
        }
        System.out.println("  col8[0..8]   = " + col8);

        int observed = 0;
        for (int y = 0; y <= 5; y++) {
            if (rows.get(y).charAt(8) == '1') {
                observed |= 1 << y;
            }
        }
        if (rows.get(7).charAt(8) == '1') {
            observed |= 1 << 6;
        }
        if (rows.get(8).charAt(8) == '1') {
            observed |= 1 << 7;
        }
        if (rows.get(8).charAt(7) == '1') {
            observed |= 1 << 8;
        }
        for (int i = 9; i < 15; i++) {
            if (rows.get(8).charAt(14 - i) == '1') {
                observed |= 1 << i;
            }
        }
        System.out.printf("observed format value (after XOR 0x5412) = 0x%04X%n", observed ^ 0x5412);
        for (int data = 0; data < 32; data++) {
            int rem = data;
            for (int i = 0; i < 10; i++) {
                rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
            }
            int bits = ((data << 10) | rem) ^ 0x5412;
            if (bits == observed) {
                int ecBits = data >>> 3;
                String ecName = switch (ecBits) {
                    case 1 -> "L";
                    case 0 -> "M";
                    case 3 -> "Q";
                    case 2 -> "H";
                    default -> "?";
                };
                System.out.printf("=> reference uses ecc=%s mask=%d%n", ecName, data & 7);
                verifyUnmasked(text, rows, size, QrCode.Ecc.valueOf(ecName), data & 7);
                verifyForcedMask(text, rows, size, QrCode.Ecc.valueOf(ecName), data & 7);
            }
        }
    }

    /** 用参考实现的掩码强制编码，做逐位比对。 */
    private static void verifyForcedMask(String text, java.util.List<String> rows, int size, QrCode.Ecc ecc, int refMask) {
        QrCode qr = QrCode.encode(text, ecc, (size - 17) / 4, refMask);
        int diff = 0;
        int firstX = -1;
        int firstY = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean ref = rows.get(y).charAt(x) == '1';
                if (qr.isDark(x, y) != ref) {
                    if (diff == 0) {
                        firstX = x;
                        firstY = y;
                    }
                    diff++;
                }
            }
        }
        System.out.printf("   forced mask=%d -> %d differing module(s) (first %d,%d)%n", refMask, diff, firstX, firstY);
        if (diff > 0) {
            System.out.println("     ref  row0 = " + rows.get(0));
            System.out.println("     mine row0 = " + rowToString(qr, 0));
            System.out.println("     ref  row8 = " + rows.get(8));
            System.out.println("     mine row8 = " + rowToString(qr, 8));
        }
    }

    private static String rowToString(QrCode qr, int y) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < qr.getSize(); x++) {
            sb.append(qr.isDark(x, y) ? '1' : '0');
        }
        return sb.toString();
    }

    /** 把两边都去掉掩码后比对，用来判断差异是「数据不一致」还是「掩码选择不同」。 */
    private static void verifyUnmasked(String text, java.util.List<String> rows, int size, QrCode.Ecc ecc, int refMask) {
        QrCode qr = QrCode.encode(text, ecc, (size - 17) / 4);
        java.util.List<String> mineUnmasked = DevQrDebug.unmask(qr, qr.getAppliedMask());
        java.util.List<String> refUnmasked = DevQrDebug.unmaskText(rows, size, refMask);
        int diff = 0;
        int firstX = -1;
        int firstY = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (mineUnmasked.get(y).charAt(x) != refUnmasked.get(y).charAt(x)) {
                    if (diff == 0) {
                        firstX = x;
                        firstY = y;
                    }
                    diff++;
                }
            }
        }
        System.out.printf("   our mask=%d, ref mask=%d -> unmasked diff = %d module(s) (first %d,%d)%n",
                qr.getAppliedMask(), refMask, diff, firstX, firstY);
        if (diff > 0) {
            System.out.println("   mine(unmasked) row8: " + mineUnmasked.get(8));
            System.out.println("   ref (unmasked) row8: " + refUnmasked.get(8));
            System.out.println("   mine           row8: " + rows.get(8));
        }
    }
}
