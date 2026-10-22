package com.qcl.launcher.launcher.glass;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * ★★★ 液态玻璃：当前背景位图的**全局共享容器**。
 *
 * <h3>★ 存两张图：原图 + 模糊图</h3>
 * <ul>
 *   <li>{@link #backdrop()} 原图 —— 降级路径（环状放大透镜）用它；</li>
 *   <li>{@link #blurred()} 模糊图 —— v4.0 的着色器采样它。</li>
 * </ul>
 * <b>为什么 v4.0 必须采样模糊图</b>：ReGlass 的玻璃采样的是它的 {@code Sampler1..5}
 * （多级模糊后的背景），折射偏移作用在模糊图上。只拿原图做位移，边缘搬过来的
 * 还是原图的细节 ⇒ 看起来像「画面错位」而不是「玻璃」。
 * <br>
 * 历史上 v3.1 曾把模糊整个删掉（用户当时的要求是「其他的不要，比如背景模糊之类的」，
 * 他只要边缘那圈畸变）。v4.0 按他的新要求改成 ReGlass 的渲染方式，模糊又变成必需的了。
 *
 * <h3>★ 模糊只在换背景时算一次</h3>
 * 面板只是**采样**这张图，位置由 uniform 决定 ⇒ 整块屏几百个面板共用一张模糊图，
 * 每帧零额外成本。这正是 v3.0 那套「换背景时算一次」的思路，只是当时被删掉了。
 *
 * <h3>⚠️ 为什么仍然需要这个类（而不是每块面板自己去找背景）</h3>
 * <ol>
 *   <li>面板要在 draw 里拿到背景，但 Drawable 没有 Context，也拿不到 Activity 的根布局；</li>
 *   <li>几百块面板如果各自 {@code BitmapDrawable.getBitmap()} 再各建一个 BitmapShader，
 *       会产生几百个 GPU 纹理上传 —— 必须全局共用一张。</li>
 * </ol>
 *
 * <h3>★ 采样原图而不是「把背景画进面板」</h3>
 * 注意区分两件事：
 * <ul>
 *   <li><b>采样背景</b>（本类做的）—— 面板从**真实背景**取色，滚动时取的是
 *       「面板当前位置背后的背景」，物理上正确；</li>
 *   <li><b>把整块背景画进面板并跟随面板移动</b>（3.0 做的）—— 面板里有一份背景副本，
 *       面板一滚动副本跟着走，用户看到的「图片粘在板块上滑动」就是它。已彻底去掉。</li>
 * </ul>
 */
public final class GlassBackdrop {

    private static final String TAG = "QCLGlass";

    /** 当前背景原图。所有面板的 BitmapShader 都绑它。 */
    private static volatile Bitmap sBackdrop;

    /**
     * ★★★ v4.0：背景的**模糊版**（ReGlass 的玻璃采样的就是这个）。
     *
     * <p>为什么必须有它：ReGlass 的 {@code liquid_glass_gui.fsh} 里，玻璃面板采样的是
     * {@code Sampler1..5}（多级模糊后的背景），折射偏移作用在这张模糊图上。
     * 只拿原图做位移，边缘搬过来的还是原图的细节 ⇒ 看起来像「画面错位」而不是「玻璃」。
     *
     * <p>★ 只在换背景时算一次，不在每帧算：面板只是**采样**这张图，位置由 uniform 决定，
     * 所以整块屏几百个面板共用一张模糊图，零额外成本。
     * （这正是 v3.0 被删掉的那个优化 —— 当时删是因为「只要畸变圈，不要模糊」，
     * 现在按用户要求改成 ReGlass 的渲染方式，它又变成必需的了。）
     */
    private static volatile Bitmap sBlurred;

    /** 每次换背景 +1。面板靠它判断「要不要重新绑定 BitmapShader」。 */
    private static volatile long sVersion = 0;

    public static long version() {
        return sVersion;
    }

    /** 当前背景原图（面板着色器的输入）。 */
    public static Bitmap backdrop() {
        return sBackdrop;
    }

    /** ★ v4.0：模糊后的背景（长边约 {@link #BLUR_MAX_DIM} 像素，采样时靠双线性放大）。 */
    public static Bitmap blurred() {
        Bitmap b = sBlurred;
        return (b != null && !b.isRecycled()) ? b : sBackdrop;
    }

    /** 兼容 3.0 的旧调用名。 */
    public static Bitmap sharp() {
        return sBackdrop;
    }

    private GlassBackdrop() {
    }

    /**
     * 设置当前背景位图。
     *
     * <p>★ 会顺带生成模糊版（见 {@link #sBlurred}）。
     */
    public static synchronized void set(Bitmap src) {
        if (src == null || src.isRecycled()) {
            return;
        }
        if (src == sBackdrop) {
            return;
        }
        sBackdrop = src;
        sBlurred = buildBlur(src);
        sVersion++;
        // ★ 必须清掉缓存 —— 否则 BitmapShader 还绑在上一张图上，玻璃边缘取到的是旧背景。
        GlassShader.invalidateBackdropShader();
    }

    /** 清空（切回纯色背景时用）。 */
    public static synchronized void clear() {
        sBackdrop = null;
        sBlurred = null;
        sVersion++;
        GlassShader.invalidateBackdropShader();
    }

    // ───────────────── 模糊 ─────────────────

    /**
     * 模糊图的长边目标（像素）。
     *
     * <p>★★★ 900 这个值是**用预览图对比出来的**，不是拍的：
     * 用一张 40px 网格的高频测试图跑 `preview_glass.py`，对比了
     * 420/4/3、720/2/2、900/2/2、960/2/1 四档 ——
     * <ul>
     *   <li>420/4/3（初版）：内部糊成一片，边缘的压缩带**完全看不出来**，
     *       观感就是「高斯模糊」；</li>
     *   <li>960/2/1：压缩带很抢眼，但内部几乎没糊，读起来像放大镜而不是磨砂玻璃；</li>
     *   <li><b>900/2/2</b>：内部仍是磨砂、边缘的网格能被明显压弯 —— 这才是玻璃。</li>
     * </ul>
     * <p>★ 关键约束：压缩带能不能被看见，取决于「模糊后的背景在带宽尺度上还有没有结构」。
     * 糊得太狠 ⇒ 位移搬过来的还是同一团颜色 ⇒ 折射等于没做。
     * 带宽 48px，所以模糊的等效半径必须明显小于 48px。
     */
    private static final int BLUR_MAX_DIM = 900;

    /** 盒式模糊半径（在缩小后的图上）。2 次盒式 ≈ 一次轻高斯。 */
    private static final int BLUR_RADIUS = 2;

    /** 盒式模糊次数。 */
    private static final int BLUR_PASSES = 2;

    /**
     * 生成模糊背景：先缩小到长边 {@link #BLUR_MAX_DIM}，再做 {@link #BLUR_PASSES} 次盒式模糊。
     *
     * <p>★ 为什么先缩小：盒式模糊的复杂度是 O(w·h)，在 1080×2340 上做 3 次要几十毫秒；
     * 缩到 420 长边之后是 1~2ms，而视觉上**完全等价**（模糊本身就是丢掉高频）。
     *
     * <p>★ 全程 try/catch：这是纯装饰，绝不能因为一张怪图把启动带走。
     */
    private static Bitmap buildBlur(Bitmap src) {
        try {
            int sw = src.getWidth();
            int sh = src.getHeight();
            if (sw <= 0 || sh <= 0) {
                return null;
            }
            float scale = Math.min(1f, BLUR_MAX_DIM / (float) Math.max(sw, sh));
            int w = Math.max(1, Math.round(sw * scale));
            int h = Math.max(1, Math.round(sh * scale));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            if (small == null) {
                return null;
            }
            // ★ 必须可变、必须是 ARGB_8888 —— 用 getPixels/setPixels 做模糊
            Bitmap work = small.isMutable() && small.getConfig() == Bitmap.Config.ARGB_8888
                    ? small
                    : small.copy(Bitmap.Config.ARGB_8888, true);
            int[] px = new int[w * h];
            work.getPixels(px, 0, w, 0, 0, w, h);
            int[] tmp = new int[px.length];
            for (int i = 0; i < BLUR_PASSES; i++) {
                boxBlurH(px, tmp, w, h, BLUR_RADIUS);
                boxBlurV(tmp, px, w, h, BLUR_RADIUS);
            }
            work.setPixels(px, 0, w, 0, 0, w, h);
            return work;
        } catch (Throwable t) {
            Log.w(TAG, "背景模糊失败，退回原图", t);
            return null;
        }
    }

    /** 横向盒式模糊（滑动窗口，O(n)）。 */
    private static void boxBlurH(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int a = 0, rr = 0, gg = 0, bb = 0;
            // 预填窗口（边缘用钳制）
            for (int i = -r; i <= r; i++) {
                int c = in[row + clamp(i, 0, w - 1)];
                a += (c >>> 24) & 0xFF;
                rr += (c >> 16) & 0xFF;
                gg += (c >> 8) & 0xFF;
                bb += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = ((a / div) << 24) | ((rr / div) << 16) | ((gg / div) << 8) | (bb / div);
                int outIdx = in[row + clamp(x - r, 0, w - 1)];
                int inIdx = in[row + clamp(x + r + 1, 0, w - 1)];
                a += ((inIdx >>> 24) & 0xFF) - ((outIdx >>> 24) & 0xFF);
                rr += ((inIdx >> 16) & 0xFF) - ((outIdx >> 16) & 0xFF);
                gg += ((inIdx >> 8) & 0xFF) - ((outIdx >> 8) & 0xFF);
                bb += (inIdx & 0xFF) - (outIdx & 0xFF);
            }
        }
    }

    /** 纵向盒式模糊（滑动窗口，O(n)）。 */
    private static void boxBlurV(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int a = 0, rr = 0, gg = 0, bb = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[clamp(i, 0, h - 1) * w + x];
                a += (c >>> 24) & 0xFF;
                rr += (c >> 16) & 0xFF;
                gg += (c >> 8) & 0xFF;
                bb += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((a / div) << 24) | ((rr / div) << 16) | ((gg / div) << 8) | (bb / div);
                int outIdx = in[clamp(y - r, 0, h - 1) * w + x];
                int inIdx = in[clamp(y + r + 1, 0, h - 1) * w + x];
                a += ((inIdx >>> 24) & 0xFF) - ((outIdx >>> 24) & 0xFF);
                rr += ((inIdx >> 16) & 0xFF) - ((outIdx >> 16) & 0xFF);
                gg += ((inIdx >> 8) & 0xFF) - ((outIdx >> 8) & 0xFF);
                bb += (inIdx & 0xFF) - (outIdx & 0xFF);
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
