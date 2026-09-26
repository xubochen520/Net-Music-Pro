package com.yunpu.yunpumusic.util;

/** 开发期调试：打印各掩码的罚分，便于与参考实现的选择过程对照。 */
public final class DevQrPenalty {
    public static void main(String[] args) throws Exception {
        if ("--flat".equals(args[0])) {
            String flat = java.nio.file.Files.readString(java.nio.file.Path.of(args[1])).trim();
            int[] parts = QrCode.scoreMatrix(flat);
            System.out.printf("java score of the SAME matrix: [r1=%d r2=%d r3=%d r4=%d]%n",
                    parts[0], parts[1], parts[2], parts[3]);
            return;
        }
        String text = args[0];
        int version = Integer.parseInt(args[1]);
        QrCode.Ecc ecc = QrCode.Ecc.valueOf(args[2]);
        for (int mask = 0; mask < 8; mask++) {
            QrCode qr = QrCode.encode(text, ecc, version, mask);
            int[] parts = qr.getPenaltyBreakdown();
            System.out.printf("mask=%d penalty=%d  [r1=%d r2=%d r3=%d r4=%d]%n",
                    mask, qr.getPenalty(), parts[0], parts[1], parts[2], parts[3]);
        }
        System.out.println("chosen=" + QrCode.encode(text, ecc, version).getAppliedMask());
    }
}
