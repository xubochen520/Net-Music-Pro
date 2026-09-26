package com.yunpu.yunpumusic.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 开发期校验工具：把 {@link QrCode} 生成的矩阵渲染成 PNG，
 * 交给独立的二维码解码器（jsQR）验证能否正确解出原文。
 *
 * <p>用法：{@code java DevQrRender <outDir>}，会把 {@link #CASES} 中每条内容
 * 用四种纠错等级各生成一张 PNG。
 */
public final class DevQrRender {
    /** 测试用例：{id, text}。 */
    static final String[][] CASES = {
            {"short", "HELLO"},
            {"netease", "https://music.163.com/login?codekey=9f2a1c4d5e6b7a8c9d0e1f2a3b4c5d6e7f8091a2b3c4d5e6"},
            {"kugou", "https://login-user.kugou.com/qrcode?qrcode=abcdef0123456789abcdef0123456789"},
            {"qq", "https://ssl.ptlogin2.qq.com/ptqrshow?appid=716027609&e=2&l=M&s=3&d=72&v=4&t=0.123456789&daid=383&pt_3rd_aid=100497308"},
            {"digits", "1234567890"},
            {"mixed", "云谱刻录台 yunpu - Net Music Pro / QR 测试"},
            {"url120", "https://music.163.com/login?codekey=" + "a".repeat(120)},
            {"url240", "https://example.com/very/long/path?token=" + "Zx9".repeat(80)},
            {"big400", "X".repeat(400)},
    };

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of(args[0]);
        Files.createDirectories(outDir);
        StringBuilder manifest = new StringBuilder("[\n");
        boolean first = true;
        for (String[] testCase : CASES) {
            for (QrCode.Ecc ecc : QrCode.Ecc.values()) {
                QrCode qr;
                try {
                    qr = QrCode.encode(testCase[1], ecc);
                } catch (RuntimeException e) {
                    System.out.println("skip " + testCase[0] + " " + ecc + ": " + e.getMessage());
                    continue;
                }
                String name = testCase[0] + "_" + ecc.name() + ".png";
                Files.write(outDir.resolve(name), render(qr, 4, 4));
                if (!first) {
                    manifest.append(",\n");
                }
                first = false;
                manifest.append("  {\"file\":\"").append(name)
                        .append("\",\"text\":\"").append(escape(testCase[1]))
                        .append("\",\"version\":").append(qr.getVersion())
                        .append(",\"ecc\":\"").append(ecc.name()).append("\"}");
            }
        }
        manifest.append("\n]\n");
        Files.writeString(outDir.resolve("manifest.json"), manifest.toString(), StandardCharsets.UTF_8);
        System.out.println("rendered " + (CASES.length * 4) + " PNG(s) into " + outDir);
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 渲染为 1 位灰度 PNG（黑=0，白=1），带 quietZone 个模块的静默区。 */
    static byte[] render(QrCode qr, int scale, int quietZone) throws IOException {
        int modules = qr.getSize() + quietZone * 2;
        int pixels = modules * scale;

        // 每行以 0 作为 filter type，随后是 1bpp 的像素数据
        byte[] raw = new byte[pixels * (1 + (pixels + 7) / 8)];
        int stride = (pixels + 7) / 8;
        for (int y = 0; y < pixels; y++) {
            int rowStart = y * (1 + stride);
            raw[rowStart] = 0;
            for (int x = 0; x < pixels; x++) {
                int mx = x / scale - quietZone;
                int my = y / scale - quietZone;
                boolean dark = mx >= 0 && my >= 0 && mx < qr.getSize() && my < qr.getSize() && qr.isDark(mx, my);
                if (!dark) {
                    raw[rowStart + 1 + (x >> 3)] |= (byte) (0x80 >> (x & 7));
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, pixels);
        writeInt(ihdr, pixels);
        ihdr.write(1);   // bit depth
        ihdr.write(0);   // color type: grayscale
        ihdr.write(0);   // compression
        ihdr.write(0);   // filter
        ihdr.write(0);   // interlace
        writeChunk(out, "IHDR", ihdr.toByteArray());
        writeChunk(out, "PLTE", new byte[]{0, 0, 0, (byte) 255, (byte) 255, (byte) 255});
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            compressed.write(buffer, 0, count);
        }
        deflater.end();
        writeChunk(out, "IDAT", compressed.toByteArray());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }
}
