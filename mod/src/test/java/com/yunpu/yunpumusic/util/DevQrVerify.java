package com.yunpu.yunpumusic.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发期校验工具（不参与模组运行）：把 {@link QrCode} 生成的矩阵与
 * 主流二维码库生成的参考矩阵做逐位比对。
 *
 * <p>用法：{@code java DevQrVerify <expected.json>}
 */
public final class DevQrVerify {
    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        List<Entry> entries = parse(json);
        int pass = 0;
        int fail = 0;
        int skipped = 0;
        for (Entry e : entries) {
            if (e.error != null) {
                skipped++;
                continue;
            }
            QrCode.Ecc ecc = QrCode.Ecc.valueOf(e.level);
            QrCode qr;
            try {
                qr = QrCode.encode(e.text, ecc);
            } catch (RuntimeException ex) {
                System.out.printf("FAIL %-10s %s : encoder threw %s%n", e.id, e.level, ex);
                fail++;
                continue;
            }
            if (qr.getVersion() != e.version) {
                System.out.printf("FAIL %-10s %s : version %d != expected %d%n", e.id, e.level, qr.getVersion(), e.version);
                fail++;
                continue;
            }
            if (qr.getSize() != e.size) {
                System.out.printf("FAIL %-10s %s : size %d != expected %d%n", e.id, e.level, qr.getSize(), e.size);
                fail++;
                continue;
            }
            int diff = 0;
            int firstX = -1;
            int firstY = -1;
            for (int y = 0; y < e.size; y++) {
                for (int x = 0; x < e.size; x++) {
                    boolean expected = e.rows.get(y).charAt(x) == '1';
                    if (qr.isDark(x, y) != expected) {
                        if (diff == 0) {
                            firstX = x;
                            firstY = y;
                        }
                        diff++;
                    }
                }
            }
            if (diff == 0) {
                pass++;
            } else {
                fail++;
                System.out.printf("FAIL %-10s %s v%d : %d module(s) differ, first at (%d,%d)%n",
                        e.id, e.level, e.version, diff, firstX, firstY);
            }
        }
        System.out.printf("%n== QR verification: %d passed, %d failed, %d skipped ==%n", pass, fail, skipped);
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static final class Entry {
        String id;
        String level;
        String text;
        int version;
        int size;
        String error;
        List<String> rows = new ArrayList<>();
    }

    /** 极小 JSON 解析：只处理本工具生成的固定结构。 */
    private static List<Entry> parse(String json) {
        List<Entry> result = new ArrayList<>();
        int i = 0;
        while (true) {
            int objStart = json.indexOf('{', i);
            if (objStart < 0) {
                break;
            }
            int objEnd = json.indexOf('}', objStart);
            String obj = json.substring(objStart, objEnd + 1);
            Entry e = new Entry();
            e.id = str(obj, "id");
            e.level = str(obj, "level");
            e.text = str(obj, "text");
            String version = num(obj, "version");
            e.version = version == null ? -1 : Integer.parseInt(version);
            String size = num(obj, "size");
            e.size = size == null ? -1 : Integer.parseInt(size);
            e.error = str(obj, "error");

            int rowsStart = obj.indexOf("\"rows\"");
            if (rowsStart >= 0) {
                int arrStart = obj.indexOf('[', rowsStart);
                int arrEnd = obj.lastIndexOf(']');
                String arr = obj.substring(arrStart + 1, arrEnd);
                for (String part : arr.split(",")) {
                    String v = part.trim();
                    if (v.length() >= 2) {
                        e.rows.add(v.substring(1, v.length() - 1));
                    }
                }
            }
            result.add(e);
            i = objEnd + 1;
        }
        return result;
    }

    private static String str(String obj, String key) {
        int k = obj.indexOf("\"" + key + "\"");
        if (k < 0) {
            return null;
        }
        int colon = obj.indexOf(':', k);
        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = q1 + 1; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (c == '\\') {
                char n = obj.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    default -> sb.append(n);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String num(String obj, String key) {
        int k = obj.indexOf("\"" + key + "\"");
        if (k < 0) {
            return null;
        }
        int colon = obj.indexOf(':', k);
        int start = colon + 1;
        while (start < obj.length() && !Character.isDigit(obj.charAt(start)) && obj.charAt(start) != '-') {
            start++;
        }
        int end = start;
        while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) {
            end++;
        }
        return end > start ? obj.substring(start, end) : null;
    }

}
