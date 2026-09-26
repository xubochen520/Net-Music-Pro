package com.yunpu.yunpumusic.util;

/** 开发期调试：打印指定文本的二维码矩阵，用于与参考实现做逐位对照。 */
public final class DevQrDump {
    public static void main(String[] args) {
        String text = args.length > 0 ? args[0] : "HELLO";
        QrCode.Ecc ecc = QrCode.Ecc.valueOf(args.length > 1 ? args[1] : "L");
        QrCode qr = QrCode.encode(text, ecc);
        System.out.println("version=" + qr.getVersion() + " size=" + qr.getSize() + " ecc=" + ecc + " text=" + text);
        for (int y = 0; y < qr.getSize(); y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < qr.getSize(); x++) {
                sb.append(qr.isDark(x, y) ? '1' : '0');
            }
            System.out.println(sb);
        }
    }
}
