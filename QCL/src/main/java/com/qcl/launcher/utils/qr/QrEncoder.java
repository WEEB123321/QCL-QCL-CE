package com.qcl.launcher.utils.qr;

import java.nio.charset.Charset;

/**
 * 纯 Java 手写二维码（QR Code）编码器 —— 只负责「把一段文本变成黑白点阵」。
 *
 * <p>★★★ 为什么手写，而不是引 zxing：
 * 本工程的依赖全部走 Maven/GitHub 仓库，而构建是**离线**的（依赖已缓存到 Gradle 缓存里）。
 * 新引一个 zxing 会导致本地/CI 都拉不到包而构建失败。手写编码器零依赖、零体积，
 * 也不受离线构建影响 —— 代价是需要自己保证正确性，所以配套有 {@code QrEncoderTest} 的
 * 逐位交叉验证（用独立的第三方解码器反解本编码器的输出）。
 *
 * <p>★★★ 支持范围刻意收窄到「版本 1–10 + 字节模式 + 四档纠错」：
 * 联机邀请码只有十几个字符，版本 10 的 M 档已能装 213 字节，远超实际需要。
 * 不做 Kanji/数字/字母数字模式（省掉一大坨码表），不做版本 11+ 与结构化追加。
 * 收窄范围换来的是一张可以手工核对到底的小码表 —— 对「必须一次就对」的场景更划算。
 *
 * <p>输出约定：{@code boolean[行][列]}，{@code true} = 深色模块。
 * 返回的矩阵**不含**静默区（quiet zone），静默区由 {@link QrRenderer} 在渲染时补。
 */
public final class QrEncoder {

    /** 纠错等级。括号内是写进格式信息的 2 位指示符（注意不是按字母顺序排的）。 */
    public enum Ecc {
        /** 约 7% 容错 */
        L(0x01),
        /** 约 15% 容错，显示场景的通用选择 */
        M(0x00),
        /** 约 25% 容错 */
        Q(0x03),
        /** 约 30% 容错 */
        H(0x02);

        final int formatBits;

        Ecc(int formatBits) {
            this.formatBits = formatBits;
        }
    }

    /** 本编码器支持的版本上限。 */
    public static final int MAX_VERSION = 10;

    /**
     * 各版本的总码字数（数据 + 纠错），下标即版本号。
     * 值来自 ISO/IEC 18004 表 1，可用「矩阵总模块数 − 功能图案模块数」独立复核。
     */
    private static final int[] TOTAL_CODEWORDS = {
            0, 26, 44, 70, 100, 134, 172, 196, 242, 292, 346
    };

    /**
     * 纠错块结构表：{@code [版本][等级序号] -> {每块纠错码字数, 组1块数, 组1数据码字数, 组2块数, 组2数据码字数}}。
     * 等级序号按 {@link Ecc} 的声明顺序：L=0, M=1, Q=2, H=3。
     */
    private static final int[][][] ECC_TABLE = {
            // v0 占位，不用
            null,
            // v1
            {{7, 1, 19, 0, 0}, {10, 1, 16, 0, 0}, {13, 1, 13, 0, 0}, {17, 1, 9, 0, 0}},
            // v2
            {{10, 1, 34, 0, 0}, {16, 1, 28, 0, 0}, {22, 1, 22, 0, 0}, {28, 1, 16, 0, 0}},
            // v3
            {{15, 1, 55, 0, 0}, {26, 1, 44, 0, 0}, {18, 2, 17, 0, 0}, {22, 2, 13, 0, 0}},
            // v4
            {{20, 1, 80, 0, 0}, {18, 2, 32, 0, 0}, {26, 2, 24, 0, 0}, {16, 4, 9, 0, 0}},
            // v5
            {{26, 1, 108, 0, 0}, {24, 2, 43, 0, 0}, {18, 2, 15, 2, 16}, {22, 2, 11, 2, 12}},
            // v6
            {{18, 2, 68, 0, 0}, {16, 4, 27, 0, 0}, {24, 4, 19, 0, 0}, {28, 4, 15, 0, 0}},
            // v7
            {{20, 2, 78, 0, 0}, {18, 4, 31, 0, 0}, {18, 2, 14, 4, 15}, {26, 4, 13, 1, 14}},
            // v8
            {{24, 2, 97, 0, 0}, {22, 2, 38, 2, 39}, {22, 4, 18, 2, 19}, {26, 4, 14, 2, 15}},
            // v9
            {{30, 2, 116, 0, 0}, {22, 3, 36, 2, 37}, {20, 4, 16, 4, 17}, {24, 4, 12, 4, 13}},
            // v10
            {{18, 2, 68, 2, 69}, {26, 4, 43, 1, 44}, {24, 6, 19, 2, 20}, {28, 6, 15, 2, 16}},
    };

    /**
     * 各版本的校正图案中心坐标（ISO/IEC 18004 附录 E）。
     * v1 没有校正图案。
     */
    private static final int[][] ALIGNMENT_CENTERS = {
            null,
            {},                       // v1
            {6, 18},                  // v2
            {6, 22},                  // v3
            {6, 26},                  // v4
            {6, 30},                  // v5
            {6, 34},                  // v6
            {6, 22, 38},              // v7
            {6, 24, 42},              // v8
            {6, 26, 46},              // v9
            {6, 28, 50},              // v10
    };

    /** 格式信息 15 位在矩阵里的位置（第一份），{@code {行, 列}}，顺序为格式串的第 0 位到第 14 位。 */
    private static final int[][] FORMAT_POS = {
            {0, 8}, {1, 8}, {2, 8}, {3, 8}, {4, 8}, {5, 8}, {7, 8}, {8, 8},
            {8, 7}, {8, 5}, {8, 4}, {8, 3}, {8, 2}, {8, 1}, {8, 0},
    };

    private static final int FORMAT_POLY = 0x537;
    private static final int FORMAT_MASK = 0x5412;
    private static final int VERSION_POLY = 0x1F25;

    // ---- GF(256) 对数/反对数表，本原多项式 x^8+x^4+x^3+x^2+1 = 0x11D ----
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

    private QrEncoder() {
    }

    // ==================== 对外入口 ====================

    /** 以纠错等级 M 编码。 */
    public static boolean[][] encode(String text) {
        return encode(text, Ecc.M);
    }

    /**
     * 编码一段文本，返回黑白点阵（不含静默区）。
     *
     * @throws IllegalArgumentException 文本为空或超出本编码器的容量
     */
    public static boolean[][] encode(String text, Ecc ecc) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("待编码文本不得为空");
        }
        if (ecc == null) {
            ecc = Ecc.M;
        }
        byte[] payload = text.getBytes(Charset.forName("UTF-8"));
        int version = chooseVersion(payload.length, ecc);
        return build(payload, version, ecc);
    }

    /** 该内容在指定等级下需要的最小版本（1..{@link #MAX_VERSION}）。 */
    public static int chooseVersion(int byteLength, Ecc ecc) {
        for (int v = 1; v <= MAX_VERSION; v++) {
            if (byteLength <= byteCapacity(v, ecc)) {
                return v;
            }
        }
        throw new IllegalArgumentException(
                "内容过长（" + byteLength + " 字节），超出本编码器上限（版本 " + MAX_VERSION
                        + " / " + ecc + " 档最多 " + byteCapacity(MAX_VERSION, ecc) + " 字节）");
    }

    /** 某版本某等级下、字节模式可容纳的净数据字节数（已扣掉模式与长度指示符）。 */
    public static int byteCapacity(int version, Ecc ecc) {
        int[] s = ECC_TABLE[version][ecc.ordinal()];
        int dataCodewords = s[1] * s[2] + s[3] * s[4];
        int headerBits = 4 + charCountBits(version);
        return (dataCodewords * 8 - headerBits) / 8;
    }

    /** 字节模式的字符计数指示符位宽：版本 1–9 是 8 位，10–26 是 16 位。 */
    private static int charCountBits(int version) {
        return version <= 9 ? 8 : 16;
    }

    // ==================== 矩阵构建 ====================

    /** 功能图案层与「哪些格子是功能模块」的标记层。 */
    static final class Layout {
        final boolean[][] base;
        final boolean[][] func;

        Layout(boolean[][] base, boolean[][] func) {
            this.base = base;
            this.func = func;
        }
    }

    /**
     * 构建功能图案层。
     *
     * <p>★★★ 绘制顺序有讲究：校正图案必须在定时图案之前画。
     * 否则「只往空白格写」的定时图案会把校正图案占据的格子误判为已占用，导致定时图案断线。
     */
    static Layout layout(int version) {
        int size = version * 4 + 17;
        boolean[][] base = new boolean[size][size];
        boolean[][] func = new boolean[size][size];

        drawFinders(base, func, size);
        drawAlignment(base, func, version, size);
        drawTiming(base, func, size);
        base[size - 8][8] = true;          // 固定深色模块
        func[size - 8][8] = true;
        reserveFormatAreas(func, size);
        if (version >= 7) {
            reserveVersionAreas(func, size);
        }
        return new Layout(base, func);
    }

    /** 功能模块标记层。包内可见，供自检复用同一份实现，避免自检与线上代码走岔。 */
    static boolean[][] functionMap(int version) {
        return layout(version).func;
    }

    private static boolean[][] build(byte[] payload, int version, Ecc ecc) {
        int size = version * 4 + 17;
        Layout layout = layout(version);
        boolean[][] base = layout.base;
        boolean[][] func = layout.func;

        int[] codewords = buildCodewords(payload, version, ecc);
        placeData(base, func, codewords, size);

        // ★ 掩码选择：对 8 种掩码各构建一次完整符号（含格式信息），取罚分最低的。
        // 罚分必须算在「含格式信息的完整符号」上，否则选出的掩码可能与标准不一致。
        int bestPenalty = Integer.MAX_VALUE;
        boolean[][] best = null;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] cand = cloneMatrix(base);
            applyMask(cand, func, mask, size);
            writeFormatInfo(cand, ecc, mask, size);
            if (version >= 7) {
                writeVersionInfo(cand, version, size);
            }
            int penalty = penalty(cand, size);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = cand;
            }
        }
        return best;
    }

    private static boolean[][] cloneMatrix(boolean[][] src) {
        boolean[][] dst = new boolean[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i].clone();
        }
        return dst;
    }

    private static void drawFinders(boolean[][] m, boolean[][] f, int size) {
        drawFinder(m, f, 0, 0);
        drawFinder(m, f, size - 7, 0);
        drawFinder(m, f, 0, size - 7);
    }

    /** 画一个 7×7 定位图案（{@code rowStart}/{@code colStart} 为左上角），并连带画出它的分隔带。 */
    private static void drawFinder(boolean[][] m, boolean[][] f, int rowStart, int colStart) {
        for (int r = -1; r <= 7; r++) {
            for (int c = -1; c <= 7; c++) {
                int rr = rowStart + r;
                int cc = colStart + c;
                if (rr < 0 || rr >= m.length || cc < 0 || cc >= m.length) {
                    continue;
                }
                boolean dark;
                if (r < 0 || r > 6 || c < 0 || c > 6) {
                    dark = false;                       // 分隔带：一圈浅色
                } else if (r == 0 || r == 6 || c == 0 || c == 6) {
                    dark = true;                        // 外框
                } else if (r >= 2 && r <= 4 && c >= 2 && c <= 4) {
                    dark = true;                        // 内核 3×3
                } else {
                    dark = false;
                }
                m[rr][cc] = dark;
                f[rr][cc] = true;
            }
        }
    }

    private static void drawAlignment(boolean[][] m, boolean[][] f, int version, int size) {
        int[] centers = ALIGNMENT_CENTERS[version];
        if (centers == null || centers.length == 0) {
            return;
        }
        for (int ci = 0; ci < centers.length; ci++) {
            for (int cj = 0; cj < centers.length; cj++) {
                int cx = centers[ci];
                int cy = centers[cj];
                // 跳过与三个定位图案重叠的三处（5×5 区域会碰到角落 8×8 区域）
                if (overlapsFinder(cx, cy, size)) {
                    continue;
                }
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        boolean dark = Math.abs(dr) == 2 || Math.abs(dc) == 2 || (dr == 0 && dc == 0);
                        m[cy + dr][cx + dc] = dark;
                        f[cy + dr][cx + dc] = true;
                    }
                }
            }
        }
    }

    private static boolean overlapsFinder(int cx, int cy, int size) {
        boolean nearLeft = cx <= 9;
        boolean nearRight = cx >= size - 10;
        boolean nearTop = cy <= 9;
        boolean nearBottom = cy >= size - 10;
        return (nearLeft && nearTop) || (nearRight && nearTop) || (nearLeft && nearBottom);
    }

    private static void drawTiming(boolean[][] m, boolean[][] f, int size) {
        for (int i = 8; i <= size - 9; i++) {
            boolean dark = (i % 2) == 0;
            if (!f[6][i]) {
                m[6][i] = dark;
                f[6][i] = true;
            }
            if (!f[i][6]) {
                m[i][6] = dark;
                f[i][6] = true;
            }
        }
    }

    private static void reserveFormatAreas(boolean[][] f, int size) {
        for (int i = 0; i <= 8; i++) {
            f[8][i] = true;
            f[i][8] = true;
        }
        for (int i = size - 8; i < size; i++) {
            f[8][i] = true;
        }
        for (int i = size - 7; i < size; i++) {
            f[i][8] = true;
        }
    }

    private static void reserveVersionAreas(boolean[][] f, int size) {
        for (int r = 0; r < 6; r++) {
            for (int c = size - 11; c <= size - 9; c++) {
                f[r][c] = true;
                f[c][r] = true;
            }
        }
    }

    // ==================== 码字构造 ====================

    /** 生成最终要按锯齿填入矩阵的码字序列（数据块与纠错块交错排列）。 */
    private static int[] buildCodewords(byte[] payload, int version, Ecc ecc) {
        int[] spec = ECC_TABLE[version][ecc.ordinal()];
        int ecPerBlock = spec[0];
        int g1Blocks = spec[1];
        int g1Data = spec[2];
        int g2Blocks = spec[3];
        int g2Data = spec[4];

        int[] data = buildDataCodewords(payload, version, g1Blocks * g1Data + g2Blocks * g2Data);

        int blockCount = g1Blocks + g2Blocks;
        int[][] dataBlocks = new int[blockCount][];
        int[][] ecBlocks = new int[blockCount][];

        int pos = 0;
        for (int b = 0; b < blockCount; b++) {
            int len = b < g1Blocks ? g1Data : g2Data;
            int[] block = new int[len];
            System.arraycopy(data, pos, block, 0, len);
            pos += len;
            dataBlocks[b] = block;
            ecBlocks[b] = reedSolomon(block, ecPerBlock);
        }

        // 交错：先按列读所有数据块，再按列读所有纠错块
        int maxData = Math.max(g1Data, g2Data);
        int[] out = new int[g1Blocks * g1Data + g2Blocks * g2Data + blockCount * ecPerBlock];
        int w = 0;
        for (int i = 0; i < maxData; i++) {
            for (int b = 0; b < blockCount; b++) {
                if (i < dataBlocks[b].length) {
                    out[w++] = dataBlocks[b][i];
                }
            }
        }
        for (int i = 0; i < ecPerBlock; i++) {
            for (int b = 0; b < blockCount; b++) {
                out[w++] = ecBlocks[b][i];
            }
        }
        return out;
    }

    /** 组装数据码字：模式指示符 + 字符计数 + 正文 + 结束符 + 补位。 */
    private static int[] buildDataCodewords(byte[] payload, int version, int dataCodewords) {
        int capacityBits = dataCodewords * 8;
        int countBits = charCountBits(version);
        int totalBits = 4 + countBits + payload.length * 8;

        int[] bits = new int[capacityBits];
        int idx = 0;
        // 模式指示符：字节模式 = 0100
        idx = appendBits(bits, idx, 0x4, 4);
        idx = appendBits(bits, idx, payload.length, countBits);
        for (byte b : payload) {
            idx = appendBits(bits, idx, b & 0xFF, 8);
        }
        // 结束符：最多 4 个 0，容量不够时截短
        int terminator = Math.min(4, capacityBits - idx);
        idx = appendBits(bits, idx, 0, terminator);
        // 补到字节边界
        while (idx % 8 != 0) {
            idx = appendBits(bits, idx, 0, 1);
        }
        // 交替补 0xEC / 0x11 直到填满
        int[] out = new int[dataCodewords];
        int padByte = 0xEC;
        for (int i = 0; i < idx / 8; i++) {
            out[i] = readBits(bits, i * 8, 8);
        }
        for (int i = idx / 8; i < dataCodewords; i++) {
            out[i] = padByte;
            padByte = padByte == 0xEC ? 0x11 : 0xEC;
        }
        // 若因结束符截短导致 totalBits 超过容量，这里会静默丢弃尾部 —— 上层 chooseVersion 已保证不会发生
        return out;
    }

    private static int appendBits(int[] bits, int idx, int value, int length) {
        for (int i = length - 1; i >= 0; i--) {
            if (idx >= bits.length) {
                return idx;
            }
            bits[idx++] = (value >>> i) & 1;
        }
        return idx;
    }

    private static int readBits(int[] bits, int from, int length) {
        int v = 0;
        for (int i = 0; i < length; i++) {
            v = (v << 1) | bits[from + i];
        }
        return v;
    }

    // ==================== Reed-Solomon ====================

    private static int gfMul(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return EXP[LOG[a] + LOG[b]];
    }

    /** 生成多项式 g(x) = ∏(x − α^i)，返回值 {@code g[0]=1} 且长度为 {@code degree+1}。 */
    private static int[] rsGenerator(int degree) {
        int[] g = {1};
        for (int i = 0; i < degree; i++) {
            int[] next = new int[g.length + 1];
            int root = EXP[i];
            for (int j = 0; j < g.length; j++) {
                next[j] ^= g[j];
                next[j + 1] ^= gfMul(g[j], root);
            }
            g = next;
        }
        return g;
    }

    /** 用 LFSR 做多项式除法，得到纠错码字（即 data·x^ecLen mod g(x) 的系数）。 */
    static int[] reedSolomon(int[] data, int ecLen) {
        int[] gen = rsGenerator(ecLen);
        int[] res = new int[ecLen];
        for (int value : data) {
            int factor = value ^ res[0];
            System.arraycopy(res, 1, res, 0, ecLen - 1);
            res[ecLen - 1] = 0;
            for (int i = 0; i < ecLen; i++) {
                res[i] ^= gfMul(gen[i + 1], factor);
            }
        }
        return res;
    }

    // ==================== 数据填充 ====================

    /**
     * 标准锯齿填充：从右下角起、两列一组、方向上下交替，跳过所有功能模块与第 6 列。
     * 码字放完后剩余的空白格就是「剩余位」，标准规定为 0 —— 矩阵初始化为 false，天然满足。
     */
    private static void placeData(boolean[][] m, boolean[][] f, int[] codewords, int size) {
        int totalBits = codewords.length * 8;
        int bitIndex = 0;
        int direction = -1;
        int row = size - 1;

        for (int col = size - 1; col > 0; col -= 2) {
            if (col == 6) {
                col--;                                  // 第 6 列是竖直定时图案，整列跳过
            }
            while (row >= 0 && row < size) {
                for (int c = 0; c < 2; c++) {
                    int cc = col - c;
                    if (f[row][cc]) {
                        continue;
                    }
                    boolean bit = false;
                    if (bitIndex < totalBits) {
                        bit = ((codewords[bitIndex >> 3] >>> (7 - (bitIndex & 7))) & 1) != 0;
                        bitIndex++;
                    }
                    m[row][cc] = bit;
                }
                row += direction;
            }
            direction = -direction;
            row += direction;
        }
    }

    // ==================== 掩码 ====================

    private static boolean maskBit(int mask, int r, int c) {
        switch (mask) {
            case 0:
                return (r + c) % 2 == 0;
            case 1:
                return r % 2 == 0;
            case 2:
                return c % 3 == 0;
            case 3:
                return (r + c) % 3 == 0;
            case 4:
                return (r / 2 + c / 3) % 2 == 0;
            case 5:
                return (r * c) % 2 + (r * c) % 3 == 0;
            case 6:
                return ((r * c) % 2 + (r * c) % 3) % 2 == 0;
            case 7:
                return ((r + c) % 2 + (r * c) % 3) % 2 == 0;
            default:
                throw new IllegalArgumentException("非法掩码编号：" + mask);
        }
    }

    private static void applyMask(boolean[][] m, boolean[][] f, int mask, int size) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (!f[r][c] && maskBit(mask, r, c)) {
                    m[r][c] = !m[r][c];
                }
            }
        }
    }

    // ==================== 格式信息 / 版本信息 ====================

    /** 15 位格式信息 = (5 位数据 + 10 位 BCH 校验) XOR 0x5412。 */
    static int formatInfo(Ecc ecc, int mask) {
        int data = (ecc.formatBits << 3) | mask;
        int bch = bchRemainder(data, FORMAT_POLY, 10);
        return ((data << 10) | bch) ^ FORMAT_MASK;
    }

    /** 18 位版本信息 = (6 位版本号 + 12 位 BCH 校验)，仅版本 7 及以上需要。 */
    static int versionInfo(int version) {
        return (version << 12) | bchRemainder(version, VERSION_POLY, 12);
    }

    /** 计算 {@code data·x^degree mod poly} 的余数。{@code poly} 的最高位必须正好在 degree 位上。 */
    private static int bchRemainder(int data, int poly, int degree) {
        int v = data << degree;
        for (int i = 31; i >= degree; i--) {
            if (((v >>> i) & 1) != 0) {
                v ^= poly << (i - degree);
            }
        }
        return v;
    }

    private static void writeFormatInfo(boolean[][] m, Ecc ecc, int mask, int size) {
        int fmt = formatInfo(ecc, mask);
        for (int i = 0; i < 15; i++) {
            boolean bit = ((fmt >>> i) & 1) != 0;
            m[FORMAT_POS[i][0]][FORMAT_POS[i][1]] = bit;
            if (i < 8) {
                m[8][size - 1 - i] = bit;
            } else {
                m[size - 7 + (i - 8)][8] = bit;
            }
        }
    }

    /**
     * 写入 18 位版本信息，两处副本。
     *
     * <p>★★★ 位序是 LSB 先写，不是 MSB 先写。这里踩过坑：
     * 最初按 MSB 先写，内部自检（照同一套顺序读回）全绿，但 v7 及以上被真实解码器一律拒绝，
     * 而 v1–v6 全部正常 —— 因为只有 v7+ 才有版本信息块。
     * 与第三方参考编码器逐格对拍后才确认：标准与主流实现都是把**最低位放在第一个落位**。
     */
    private static void writeVersionInfo(boolean[][] m, int version, int size) {
        int info = versionInfo(version);
        int bitIndex = 0;
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 3; j++) {
                boolean bit = ((info >>> bitIndex) & 1) != 0;
                bitIndex++;
                m[size - 11 + j][i] = bit;
                m[i][size - 11 + j] = bit;
            }
        }
    }

    // ==================== 掩码罚分（ISO/IEC 18004 表 11） ====================

    private static int penalty(boolean[][] m, int size) {
        return rule1(m, size) + rule2(m, size) + rule3(m, size) + rule4(m, size);
    }

    /** 规则 1：同色连续 ≥5 个，罚 3 + (长度 − 5)。 */
    private static int rule1(boolean[][] m, int size) {
        int penalty = 0;
        for (int i = 0; i < size; i++) {
            // 行
            int run = 1;
            for (int j = 1; j < size; j++) {
                if (m[i][j] == m[i][j - 1]) {
                    run++;
                } else {
                    if (run >= 5) {
                        penalty += 3 + (run - 5);
                    }
                    run = 1;
                }
            }
            if (run >= 5) {
                penalty += 3 + (run - 5);
            }
            // 列
            run = 1;
            for (int j = 1; j < size; j++) {
                if (m[j][i] == m[j - 1][i]) {
                    run++;
                } else {
                    if (run >= 5) {
                        penalty += 3 + (run - 5);
                    }
                    run = 1;
                }
            }
            if (run >= 5) {
                penalty += 3 + (run - 5);
            }
        }
        return penalty;
    }

    /** 规则 2：每个 2×2 同色块罚 3。 */
    private static int rule2(boolean[][] m, int size) {
        int penalty = 0;
        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                boolean v = m[r][c];
                if (v == m[r][c + 1] && v == m[r + 1][c] && v == m[r + 1][c + 1]) {
                    penalty += 3;
                }
            }
        }
        return penalty;
    }

    /**
     * 规则 3：出现 1:1:3:1:1 图案（{@code 1011101}）且**任一侧**有 4 个浅色模块，每次罚 40。
     *
     * <p>★★★ 注意「任一侧」要按符号边界截断：图案贴着符号边缘时，外侧那 4 格落在静默区里，
     * 静默区本来就是浅色，所以越界部分按浅色算。最初只匹配完整的 11 格窗口，
     * 漏掉了贴边情形 —— 结果是掩码优选与主流实现不一致（符号仍合法，但选不到最优掩码）。
     */
    private static int rule3(boolean[][] m, int size) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j + 6 < size; j++) {
                if (m[i][j] && !m[i][j + 1] && m[i][j + 2] && m[i][j + 3] && m[i][j + 4] && !m[i][j + 5]
                        && m[i][j + 6]
                        && (allLight(m, i, j - 4, j, true) || allLight(m, i, j + 7, j + 11, true))) {
                    count++;
                }
                if (m[j][i] && !m[j + 1][i] && m[j + 2][i] && m[j + 3][i] && m[j + 4][i] && !m[j + 5][i]
                        && m[j + 6][i]
                        && (allLight(m, i, j - 4, j, false) || allLight(m, i, j + 7, j + 11, false))) {
                    count++;
                }
            }
        }
        return count * 40;
    }

    /** 区间 {@code [from, to)} 是否全为浅色。越界部分视为浅色（对应符号外的静默区）。 */
    private static boolean allLight(boolean[][] m, int fixed, int from, int to, boolean horizontal) {
        int lo = Math.max(from, 0);
        int hi = Math.min(to, m.length);
        for (int k = lo; k < hi; k++) {
            boolean v = horizontal ? m[fixed][k] : m[k][fixed];
            if (v) {
                return false;
            }
        }
        return true;
    }

    /** 规则 4：深色占比偏离 50% 每 5% 罚 10。 */
    private static int rule4(boolean[][] m, int size) {
        int dark = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (m[r][c]) {
                    dark++;
                }
            }
        }
        int total = size * size;
        int variance = Math.abs(dark * 2 - total) * 10 / total;
        return variance * 10;
    }

    // ==================== 供自检使用 ====================

    /** 统计功能图案占用的模块数，用于与标准「数据模块数」交叉核对。 */
    static int countFunctionModules(int version) {
        boolean[][] f = functionMap(version);
        int n = 0;
        for (boolean[] row : f) {
            for (boolean cell : row) {
                if (cell) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 功能模块被画错的兜底检查用：标准给出的「数据模块数」。 */
    static int expectedDataModules(int version) {
        return TOTAL_CODEWORDS[version] * 8 + remainderBits(version);
    }

    /** 剩余位数：版本 2–6 为 7，其余为 0。 */
    static int remainderBits(int version) {
        return (version >= 2 && version <= 6) ? 7 : 0;
    }

    static int totalCodewords(int version) {
        return TOTAL_CODEWORDS[version];
    }

    static int[] eccSpec(int version, Ecc ecc) {
        return ECC_TABLE[version][ecc.ordinal()];
    }
}
