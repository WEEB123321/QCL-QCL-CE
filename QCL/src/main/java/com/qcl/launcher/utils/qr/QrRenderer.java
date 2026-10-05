package com.qcl.launcher.utils.qr;

import android.graphics.Bitmap;

/**
 * 把 {@link QrEncoder} 产出的点阵渲染成可供扫码的位图。
 *
 * <p>★★★ 必须**整数倍**放大模块，不能靠 ImageView 拉伸：
 * 非整数倍缩放会让模块边缘被插值成灰色，破坏「深/浅」二值性，扫码成功率会明显下降。
 * 所以这里先算出整数倍率，再让位图尺寸等于「倍率 ×（点阵边长 + 两侧静默区）」，
 * 由调用方按这个尺寸摆放，而不是反过来。
 */
public final class QrRenderer {

    /** 静默区宽度（模块数）。标准要求 4，低于这个值部分扫码器会拒绝识别。 */
    public static final int QUIET_ZONE = 4;

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private QrRenderer() {
    }

    /** 以纠错等级 M 编码并渲染。 */
    public static Bitmap render(String text, int targetSizePx) {
        return render(text, targetSizePx, QrEncoder.Ecc.M);
    }

    /**
     * 编码并渲染。
     *
     * @param targetSizePx 期望边长（像素）。实际尺寸会向下取整到整数倍模块，可能略小于它。
     */
    public static Bitmap render(String text, int targetSizePx, QrEncoder.Ecc ecc) {
        return renderMatrix(QrEncoder.encode(text, ecc), targetSizePx, QUIET_ZONE);
    }

    /**
     * 把点阵渲染成位图。
     *
     * @param matrix      点阵，{@code true} 为深色
     * @param targetSizePx 期望边长（像素）
     * @param quietZone   静默区宽度（模块数）
     */
    public static Bitmap renderMatrix(boolean[][] matrix, int targetSizePx, int quietZone) {
        int modules = matrix.length + quietZone * 2;
        int scale = Math.max(1, targetSizePx / modules);
        int size = scale * modules;

        int[] pixels = new int[size * size];
        java.util.Arrays.fill(pixels, WHITE);

        for (int r = 0; r < matrix.length; r++) {
            int y0 = (r + quietZone) * scale;
            for (int c = 0; c < matrix.length; c++) {
                if (!matrix[r][c]) {
                    continue;
                }
                int x0 = (c + quietZone) * scale;
                for (int dy = 0; dy < scale; dy++) {
                    int rowStart = (y0 + dy) * size + x0;
                    for (int dx = 0; dx < scale; dx++) {
                        pixels[rowStart + dx] = BLACK;
                    }
                }
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        return bitmap;
    }

    /** 该内容在渲染后的实际边长（像素），供布局预留空间。 */
    public static int renderedSize(String text, int targetSizePx, QrEncoder.Ecc ecc) {
        int modules = QrEncoder.encode(text, ecc).length + QUIET_ZONE * 2;
        return Math.max(1, targetSizePx / modules) * modules;
    }
}
