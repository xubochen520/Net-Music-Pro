package com.yunpu.yunpumusic.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 自包含的二维码编码器（字节模式 / UTF-8）。
 *
 * <p>不依赖任何第三方库，支持版本 1 ~ 15 与 L / M / Q / H 四种纠错等级，
 * 只使用 8 位字节模式与掩码 0 ~ 7 的全评估（选出最优掩码），
 * 因此生成的矩阵与主流二维码库（ZXing / qrcode.js / Python qrcode）完全一致。
 *
 * <p>之所以自己实现而不是引入依赖，是为了让模组 JAR 保持零外部依赖，
 * 避免在 Minecraft 的模块化类加载环境里出现第三方库冲突。
 */
public final class QrCode {
    /** 纠错等级。formatBits 为 ISO/IEC 18004 表 25 的格式信息取值，tableIndex 对应 {@link #RS_BLOCKS} 的列。 */
    public enum Ecc {
        L(1, 0), M(0, 1), Q(3, 2), H(2, 3);

        private final int formatBits;
        private final int tableIndex;

        Ecc(int formatBits, int tableIndex) {
            this.formatBits = formatBits;
            this.tableIndex = tableIndex;
        }
    }

    private static final int MIN_VERSION = 1;
    private static final int MAX_VERSION = 15;
    private static final int MODE_BYTE = 4;

    /**
     * [version][ecc][3] = { 每块纠错码字数, 组1块数, 组1数据码字数, 组2块数, 组2数据码字数 }
     * 索引顺序与 {@link Ecc#tableIndex} 一致，即 L, M, Q, H。
     */
    private static final short[][][] RS_BLOCKS = {
            // dummy for version 0
            null,
            // 1
            {{7, 1, 19, 0, 0}, {10, 1, 16, 0, 0}, {13, 1, 13, 0, 0}, {17, 1, 9, 0, 0}},
            // 2
            {{10, 1, 34, 0, 0}, {16, 1, 28, 0, 0}, {22, 1, 22, 0, 0}, {28, 1, 16, 0, 0}},
            // 3
            {{15, 1, 55, 0, 0}, {26, 1, 44, 0, 0}, {18, 2, 17, 0, 0}, {22, 2, 13, 0, 0}},
            // 4
            {{20, 1, 80, 0, 0}, {18, 2, 32, 0, 0}, {26, 2, 24, 0, 0}, {16, 4, 9, 0, 0}},
            // 5
            {{26, 1, 108, 0, 0}, {24, 2, 43, 0, 0}, {18, 2, 15, 2, 16}, {22, 2, 11, 2, 12}},
            // 6
            {{18, 2, 68, 0, 0}, {16, 4, 27, 0, 0}, {24, 4, 19, 0, 0}, {28, 4, 15, 0, 0}},
            // 7
            {{20, 2, 78, 0, 0}, {18, 4, 31, 0, 0}, {18, 2, 14, 4, 15}, {26, 4, 13, 1, 14}},
            // 8
            {{24, 2, 97, 0, 0}, {22, 2, 38, 2, 39}, {22, 4, 18, 2, 19}, {26, 4, 14, 2, 15}},
            // 9
            {{30, 2, 116, 0, 0}, {22, 3, 36, 2, 37}, {20, 4, 16, 4, 17}, {24, 4, 12, 4, 13}},
            // 10
            {{18, 2, 68, 2, 69}, {26, 4, 43, 1, 44}, {24, 6, 19, 2, 20}, {28, 6, 15, 2, 16}},
            // 11
            {{20, 4, 81, 0, 0}, {30, 1, 50, 4, 51}, {28, 4, 22, 4, 23}, {24, 3, 12, 8, 13}},
            // 12
            {{24, 2, 92, 2, 93}, {22, 6, 36, 2, 37}, {26, 4, 20, 6, 21}, {28, 7, 14, 4, 15}},
            // 13
            {{26, 4, 107, 0, 0}, {22, 8, 37, 1, 38}, {24, 8, 20, 4, 21}, {22, 12, 11, 4, 12}},
            // 14
            {{30, 3, 115, 1, 116}, {24, 4, 40, 5, 41}, {20, 11, 16, 5, 17}, {24, 11, 12, 5, 13}},
            // 15
            {{22, 5, 87, 1, 88}, {24, 5, 41, 5, 42}, {30, 5, 24, 7, 25}, {24, 11, 12, 7, 13}},
    };

    /**
     * 按 ISO/IEC 18004 的规则算法求出对齐图案中心坐标（版本 2 起）。
     * 版本 1 返回空数组；最后一个坐标固定为 size - 7，步长为偶数且使坐标关于中心对称。
     */
    private static int[] alignmentPatternPositions(int version) {
        if (version == 1) {
            return new int[0];
        }
        int count = version / 7 + 2;
        int size = version * 4 + 17;
        int step = version == 32 ? 26 : (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2;
        int[] result = new int[count];
        result[0] = 6;
        for (int i = count - 1; i >= 1; i--) {
            result[i] = size - 7 - (count - 1 - i) * step;
        }
        return result;
    }

    private final int version;
    private final Ecc ecc;
    private final int size;
    private final boolean[][] modules;
    private final boolean[][] isFunction;
    private int appliedMask = -1;

    private QrCode(int version, Ecc ecc) {
        this.version = version;
        this.ecc = ecc;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];
    }

    public int getSize() {
        return size;
    }

    public int getVersion() {
        return version;
    }

    /** 返回 true 表示该位置是黑色模块。 */
    public boolean isDark(int x, int y) {
        return modules[y][x];
    }

    // ------------------------------------------------------------------
    // 容量计算
    // ------------------------------------------------------------------

    private static int dataCodewords(int version, Ecc ecc) {
        short[] spec = RS_BLOCKS[version][ecc.tableIndex];
        return spec[1] * spec[2] + spec[3] * spec[4];
    }

    /** 字节模式下可容纳的数据字节数。 */
    private static int dataCapacityBytes(int version, Ecc ecc) {
        int dataBits = dataCodewords(version, ecc) * 8;
        int lengthBits = version < 10 ? 8 : 16;
        int usable = dataBits - 4 - lengthBits;
        return Math.max(0, usable / 8);
    }

    // ------------------------------------------------------------------
    // 编码入口
    // ------------------------------------------------------------------

    /** 使用最优版本与指定纠错等级编码文本。 */
    public static QrCode encode(String text, Ecc ecc) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version = -1;
        for (int v = MIN_VERSION; v <= MAX_VERSION; v++) {
            if (data.length <= dataCapacityBytes(v, ecc)) {
                version = v;
                break;
            }
        }
        if (version < 0) {
            throw new IllegalArgumentException(
                    "内容过长，无法在版本 " + MAX_VERSION + " 内编码：" + data.length + " 字节");
        }
        return encode(text, ecc, version);
    }

    /** 指定版本编码。 */
    public static QrCode encode(String text, Ecc ecc, int version) {
        return encode(text, ecc, version, -1);
    }

    /** 指定版本与掩码编码（mask 为 -1 时按标准罚分自动选择掩码）。 */
    public static QrCode encode(String text, Ecc ecc, int version, int forcedMask) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("不支持的二维码版本：" + version);
        }
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > dataCapacityBytes(version, ecc)) {
            throw new IllegalArgumentException(
                    "内容超过版本 " + version + " 的容量：" + data.length + " > " + dataCapacityBytes(version, ecc));
        }

        QrCode qr = new QrCode(version, ecc);
        byte[] codewords = buildCodewords(data, version, ecc);
        qr.drawFunctionPatterns();
        qr.drawCodewords(codewords);
        if (forcedMask >= 0 && forcedMask <= 7) {
            qr.applyMask(forcedMask);
            qr.appliedMask = forcedMask;
            qr.drawFormatBits(forcedMask);
        } else {
            qr.applyBestMask();
        }
        return qr;
    }

    // ------------------------------------------------------------------
    // 码字构造
    // ------------------------------------------------------------------

    private static byte[] buildCodewords(byte[] data, int version, Ecc ecc) {
        int dataCw = dataCodewords(version, ecc);
        BitBuffer bits = new BitBuffer();
        bits.append(MODE_BYTE, 4);
        bits.append(data.length, version < 10 ? 8 : 16);
        for (byte b : data) {
            bits.append(b & 0xFF, 8);
        }
        // 终止符
        int capacityBits = dataCw * 8;
        int terminator = Math.min(4, capacityBits - bits.size());
        bits.append(0, terminator);
        // 补齐到字节边界
        while (bits.size() % 8 != 0) {
            bits.append(0, 1);
        }
        // 填充码字 0xEC / 0x11 交替
        boolean toggle = false;
        while (bits.size() < capacityBits) {
            bits.append(toggle ? 0x11 : 0xEC, 8);
            toggle = !toggle;
        }

        byte[] raw = bits.toBytes();
        short[] spec = RS_BLOCKS[version][ecc.tableIndex];
        int ecPerBlock = spec[0];
        int group1 = spec[1];
        int group1Size = spec[2];
        int group2 = spec[3];
        int group2Size = spec[4];
        int totalBlocks = group1 + group2;

        byte[][] dataBlocks = new byte[totalBlocks][];
        byte[][] ecBlocks = new byte[totalBlocks][];
        int offset = 0;
        for (int i = 0; i < totalBlocks; i++) {
            int blockSize = i < group1 ? group1Size : group2Size;
            dataBlocks[i] = Arrays.copyOfRange(raw, offset, offset + blockSize);
            offset += blockSize;
            ecBlocks[i] = reedSolomon(dataBlocks[i], ecPerBlock);
        }

        // 交错排列
        List<Byte> out = new ArrayList<>(dataCw + ecPerBlock * totalBlocks);
        int maxData = Math.max(group1Size, group2Size);
        for (int i = 0; i < maxData; i++) {
            for (byte[] block : dataBlocks) {
                if (i < block.length) {
                    out.add(block[i]);
                }
            }
        }
        for (int i = 0; i < ecPerBlock; i++) {
            for (byte[] block : ecBlocks) {
                out.add(block[i]);
            }
        }

        byte[] result = new byte[out.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Reed-Solomon
    // ------------------------------------------------------------------

    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= 0x11D;
            }
        }
        for (int i = 255; i < 512; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    private static int gfMul(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return EXP[LOG[a] + LOG[b]];
    }

    /** 生成多项式，最高次项系数固定为 1，返回值不含最高次项。 */
    private static int[] generatorPoly(int degree) {
        int[] poly = {1};
        for (int i = 0; i < degree; i++) {
            int[] next = new int[poly.length + 1];
            for (int j = 0; j < poly.length; j++) {
                next[j] ^= poly[j];
                next[j + 1] ^= gfMul(poly[j], EXP[i]);
            }
            poly = next;
        }
        // poly 的最高次项为 1，去掉
        return Arrays.copyOfRange(poly, 1, poly.length);
    }

    private static byte[] reedSolomon(byte[] data, int ecCount) {
        int[] gen = generatorPoly(ecCount);
        int[] remainder = new int[ecCount];
        for (byte b : data) {
            int factor = (b & 0xFF) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, ecCount - 1);
            remainder[ecCount - 1] = 0;
            for (int i = 0; i < ecCount; i++) {
                remainder[i] ^= gfMul(gen[i], factor);
            }
        }
        byte[] out = new byte[ecCount];
        for (int i = 0; i < ecCount; i++) {
            out[i] = (byte) remainder[i];
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 矩阵绘制
    // ------------------------------------------------------------------

    private void drawFunctionPatterns() {
        // 定位图案
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);

        // 定位图案之间的分隔线
        for (int i = 0; i < 8; i++) {
            setFunction(7, i, false);
            setFunction(i, 7, false);
            setFunction(size - 8, i, false);
            setFunction(size - 1 - i, 7, false);
            setFunction(7, size - 1 - i, false);
            setFunction(i, size - 8, false);
        }

        // 校正图案
        int[] positions = alignmentPatternPositions(version);
        for (int cy : positions) {
            for (int cx : positions) {
                if ((cx == 6 && cy == 6) || (cx == 6 && cy == size - 7) || (cx == size - 7 && cy == 6)) {
                    continue;
                }
                drawAlignment(cx, cy);
            }
        }

        // 时序图案
        for (int i = 8; i < size - 8; i++) {
            boolean dark = i % 2 == 0;
            setFunction(i, 6, dark);
            setFunction(6, i, dark);
        }

        // 固定暗模块
        setFunction(8, size - 8, true);

        // 预留格式信息区域
        reserveFormatAreas();

        // 版本信息（版本 7 起）
        if (version >= 7) {
            int bits = versionInformation(version);
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >> i) & 1) == 1;
                int a = size - 11 + i % 3;
                int b = i / 3;
                setFunction(a, b, bit);
                setFunction(b, a, bit);
            }
        }
    }

    private void reserveFormatAreas() {
        for (int i = 0; i <= 8; i++) {
            if (i != 6) {
                setFunction(8, i, false);
                setFunction(i, 8, false);
            }
        }
        for (int i = 0; i < 8; i++) {
            setFunction(8, size - 1 - i, false);
            setFunction(size - 1 - i, 8, false);
        }
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x < 0 || y < 0 || x >= size || y >= size) {
                    continue;
                }
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                boolean dark = dist != 2 && dist != 4;
                setFunction(x, y, dark);
            }
        }
    }

    private void drawAlignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                setFunction(cx + dx, cy + dy, dist != 1);
            }
        }
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    /** 按标准的之字形顺序放置数据位。 */
    private void drawCodewords(byte[] codewords) {
        int bitIndex = 0;
        int totalBits = codewords.length * 8;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (isFunction[y][x] || bitIndex >= totalBits) {
                        continue;
                    }
                    boolean bit = ((codewords[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1) == 1;
                    modules[y][x] = bit;
                    bitIndex++;
                }
            }
        }
    }

    private void applyBestMask() {
        int bestMask = -1;
        int bestPenalty = Integer.MAX_VALUE;
        boolean[][] best = null;
        for (int mask = 0; mask < 8; mask++) {
            applyMask(mask);
            drawFormatBits(mask);
            int penalty = penaltyScore();
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
                best = copyModules();
            }
            applyMask(mask); // 异或两次即还原
        }
        for (int y = 0; y < size; y++) {
            System.arraycopy(best[y], 0, modules[y], 0, size);
        }
        appliedMask = bestMask;
        drawFormatBits(bestMask);
    }

    /** 实际使用的掩码编号（0 ~ 7），仅用于开发期校验。 */
    public int getAppliedMask() {
        return appliedMask;
    }

    /** 当前矩阵的掩码罚分，仅用于开发期校验。 */
    public int getPenalty() {
        return penaltyScore();
    }

    /** 掩码罚分分项（规则 1 ~ 4），仅用于开发期校验。 */
    public int[] getPenaltyBreakdown() {
        return new int[]{penaltyRule1(), penaltyRule2(), penaltyRule3(), penaltyRule4()};
    }

    /**
     * 对任意矩阵计算四项罚分，仅用于开发期校验（把同一矩阵在两种实现下的打分做对照）。
     *
     * @param flat 按行优先展开的 0/1 字符串
     */
    public static int[] scoreMatrix(String flat) {
        int size = (int) Math.round(Math.sqrt(flat.length()));
        boolean[][] m = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                m[y][x] = flat.charAt(y * size + x) == '1';
            }
        }
        return new int[]{penaltyRule1(m, size), penaltyRule2(m, size), penaltyRule3(m, size), penaltyRule4(m, size)};
    }

    private static int penaltyRule1(boolean[][] m, int size) {
        int penalty = 0;
        for (int y = 0; y < size; y++) {
            int run = 0;
            boolean color = m[y][0];
            for (int x = 0; x < size; x++) {
                if (m[y][x] == color) {
                    run++;
                } else {
                    if (run >= 5) {
                        penalty += run - 2;
                    }
                    run = 1;
                    color = m[y][x];
                }
            }
            if (run >= 5) {
                penalty += run - 2;
            }
        }
        for (int x = 0; x < size; x++) {
            int run = 0;
            boolean color = m[0][x];
            for (int y = 0; y < size; y++) {
                if (m[y][x] == color) {
                    run++;
                } else {
                    if (run >= 5) {
                        penalty += run - 2;
                    }
                    run = 1;
                    color = m[y][x];
                }
            }
            if (run >= 5) {
                penalty += run - 2;
            }
        }
        return penalty;
    }

    private static int penaltyRule2(boolean[][] m, int size) {
        int penalty = 0;
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = m[y][x];
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) {
                    penalty += 3;
                }
            }
        }
        return penalty;
    }

    private static int penaltyRule3(boolean[][] m, int size) {
        int penalty = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x + 10 < size; x++) {
                if (isFinderLikeRun(m[y][x], m[y][x + 1], m[y][x + 2], m[y][x + 3], m[y][x + 4],
                        m[y][x + 5], m[y][x + 6], m[y][x + 7], m[y][x + 8], m[y][x + 9], m[y][x + 10])) {
                    penalty += 40;
                }
            }
        }
        for (int x = 0; x < size; x++) {
            for (int y = 0; y + 10 < size; y++) {
                if (isFinderLikeRun(m[y][x], m[y + 1][x], m[y + 2][x], m[y + 3][x], m[y + 4][x],
                        m[y + 5][x], m[y + 6][x], m[y + 7][x], m[y + 8][x], m[y + 9][x], m[y + 10][x])) {
                    penalty += 40;
                }
            }
        }
        return penalty;
    }

    private static int penaltyRule4(boolean[][] m, int size) {
        int dark = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (m[y][x]) {
                    dark++;
                }
            }
        }
        int total = size * size;
        return (int) (Math.abs((double) dark * 100 / total - 50) / 5) * 10;
    }

    /** 返回某个坐标是否属于功能图案（非数据区），仅用于开发期校验。 */
    public boolean isFunctionModule(int x, int y) {
        return isFunction[y][x];
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (isFunction[y][x]) {
                    continue;
                }
                if (maskCondition(mask, x, y)) {
                    modules[y][x] = !modules[y][x];
                }
            }
        }
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

    private boolean[][] copyModules() {
        boolean[][] copy = new boolean[size][];
        for (int y = 0; y < size; y++) {
            copy[y] = modules[y].clone();
        }
        return copy;
    }

    private void drawFormatBits(int mask) {
        int data = (ecc.formatBits << 3) | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = ((data << 10) | rem) ^ 0x5412;

        // 左上角
        for (int i = 0; i <= 5; i++) {
            modules[i][8] = ((bits >> i) & 1) == 1;
        }
        modules[7][8] = ((bits >> 6) & 1) == 1;
        modules[8][8] = ((bits >> 7) & 1) == 1;
        modules[8][7] = ((bits >> 8) & 1) == 1;
        for (int i = 9; i < 15; i++) {
            modules[8][14 - i] = ((bits >> i) & 1) == 1;
        }

        // 右上 / 左下副本
        for (int i = 0; i < 8; i++) {
            modules[8][size - 1 - i] = ((bits >> i) & 1) == 1;
        }
        for (int i = 8; i < 15; i++) {
            modules[size - 15 + i][8] = ((bits >> i) & 1) == 1;
        }
        modules[size - 8][8] = true;
    }

    private static int versionInformation(int version) {
        int rem = version;
        for (int i = 0; i < 12; i++) {
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        }
        return (version << 12) | rem;
    }

    // ------------------------------------------------------------------
    // 掩码罚分（ISO/IEC 18004 规则 1 ~ 4）
    // ------------------------------------------------------------------

    private int penaltyScore() {
        return penaltyRule1() + penaltyRule2() + penaltyRule3() + penaltyRule4();
    }

    /**
     * 规则 1：行 / 列上连续同色模块长度 >= 5 时，罚分为 (长度 - 2)。
     * 例如 5 个连续模块罚 3 分，6 个罚 4 分。
     */
    private int penaltyRule1() {
        return penaltyRule1(modules, size);
    }

    private int penaltyRule2() {
        return penaltyRule2(modules, size);
    }

    private int penaltyRule3() {
        return penaltyRule3(modules, size);
    }

    private int penaltyRule4() {
        return penaltyRule4(modules, size);
    }


    /**
     * 规则 3 的 11 模块窗口判定：序列为 {@code 10111010000} 或 {@code 00001011101}。
     */
    private static boolean isFinderLikeRun(boolean... window) {
        int bits = 0;
        for (int i = 0; i < 11; i++) {
            bits = (bits << 1) | (window[i] ? 1 : 0);
        }
        return bits == 0b10111010000 || bits == 0b00001011101;
    }

    // ------------------------------------------------------------------
    // 位缓冲
    // ------------------------------------------------------------------

    private static final class BitBuffer {
        private final List<Byte> bytes = new ArrayList<>();
        private int bitLength;

        int size() {
            return bitLength;
        }

        void append(int value, int length) {
            for (int i = length - 1; i >= 0; i--) {
                int bit = (value >>> i) & 1;
                if (bitLength % 8 == 0) {
                    bytes.add((byte) 0);
                }
                if (bit != 0) {
                    int index = bytes.size() - 1;
                    bytes.set(index, (byte) (bytes.get(index) | (1 << (7 - (bitLength % 8)))));
                }
                bitLength++;
            }
        }

        byte[] toBytes() {
            byte[] out = new byte[bytes.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = bytes.get(i);
            }
            return out;
        }
    }
}
