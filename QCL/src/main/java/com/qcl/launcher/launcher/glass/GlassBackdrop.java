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
     * <p>★ 会顺带生成模糊版（见 {@link #sBlurred}），所以**不是零成本**：
     * 900 长边下大约几毫秒到十几毫秒，具体看设备（超一帧会落日志，见 {@link #buildBlur}）。
     * 三个调用方都在主线程 —— 但它们只在「换背景」时调，不是每帧，
     * 而且同一个 Bitmap 重复传进来会被下面的引用判等直接挡掉。
     */
    public static synchronized void set(Bitmap src) {
        if (src == null || src.isRecycled()) {
            return;
        }
        // ★ 引用判等：轮播/重入会拿同一张图反复调 set()，没必要重算模糊
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
     *   <li><b>900 长边</b>：内部仍是磨砂、边缘的网格能被明显压弯 —— 这才是玻璃。</li>
     * </ul>
     * <p>（上表的「半径/次数」后来换成了等效的 3/1，见 {@link #BLUR_RADIUS}。）
     * <p>★ 关键约束：压缩带能不能被看见，取决于「模糊后的背景在带宽尺度上还有没有结构」。
     * 糊得太狠 ⇒ 位移搬过来的还是同一团颜色 ⇒ 折射等于没做。
     * 带宽 48px，所以模糊的等效半径必须明显小于 48px。
     */
    private static final int BLUR_MAX_DIM = 900;

    /**
     * 盒式模糊半径（在缩小后的图上）。
     *
     * <p>★★★ 半径 3 + 单次（见 {@link #BLUR_PASSES}）与「半径 2 + 两次」**视觉等价**：
     * 盒式模糊两次的等效 σ = √(2·r(r+1)/3)，r=2 时 σ≈2；r=3 单次 σ≈2 —— 同一个数。
     * 而遍历次数从 4 次（H,V,H,V）降到 2 次（H,V），**成本减半**。
     * <p>验据：`preview_glass.py` 用高频网格图渲两档对比，
     * 平均逐像素差 1.69/255（0.66%）、p99 = 8、最大 17 —— 肉眼不可分辨。
     */
    private static final int BLUR_RADIUS = 3;

    /**
     * 盒式模糊次数。
     *
     * <p>★ 单次就够：单次 H+V 的核已经是二维盒式（正方形），
     * 盒式核本身就近似高斯（中心极限定理），两次只是在同一形状上再卷积一次。
     * 真正决定「糊不糊」的是半径，不是次数 —— 见 {@link #BLUR_RADIUS}。
     */
    private static final int BLUR_PASSES = 1;

    /** 单次重建超过这个耗时就记一条日志（16ms = 60fps 的一帧）。 */
    private static final long BLUR_WARN_MS = 16L;

    /**
     * 生成模糊背景：先缩小到长边 {@link #BLUR_MAX_DIM}，再做 {@link #BLUR_PASSES} 次盒式模糊。
     *
     * <p>★★★ 这个方法是**在主线程上跑的**（{@code DynamicBackground.apply()} 换背景时直接调），
     * 所以成本必须盯着：900 长边约 45 万像素，光 {@code getPixels} 就要 2~5ms。
     * 因此这里做了三件事把开销压下去：
     * <ol>
     *   <li>先缩小 —— 盒式模糊是 O(w·h)，在原图（1080×2340）上做要几十毫秒，
     *       缩到 900 长边后是 1/6 的像素量，而视觉上**完全等价**（模糊本身就是丢掉高频）；</li>
     *   <li>单次 H+V 而不是两次 —— 见 {@link #BLUR_RADIUS} 的等效 σ 推导；</li>
     *   <li>不 {@code setPixels} 回原 bitmap，直接 {@code createBitmap} 一步成型 ——
     *       省掉一次全图写入和一次 copy（原图不可变时那个 copy 是白拷的）。</li>
     * </ol>
     * <p>★ 超过 {@link #BLUR_WARN_MS} 会落一条日志：这是**观测**，不是预防。
     * 真机上到底花了多久只有日志说得清，别靠估。
     *
     * <p>★ 全程 try/catch：这是纯装饰，绝不能因为一张怪图把启动带走。
     */
    private static Bitmap buildBlur(Bitmap src) {
        try {
            long t0 = System.nanoTime();
            int sw = src.getWidth();
            int sh = src.getHeight();
            if (sw <= 0 || sh <= 0) {
                return null;
            }
            float scale = Math.min(1f, BLUR_MAX_DIM / (float) Math.max(sw, sh));
            int w = Math.max(1, Math.round(sw * scale));
            int h = Math.max(1, Math.round(sh * scale));
            // ★ 长边本来就 ≤ 上限时不要 createScaledBitmap —— 它会白拷一份全尺寸位图
            Bitmap small = (scale >= 1f) ? src : Bitmap.createScaledBitmap(src, w, h, true);
            if (small == null) {
                return null;
            }
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            int[] tmp = new int[px.length];
            for (int i = 0; i < BLUR_PASSES; i++) {
                boxBlurH(px, tmp, w, h, BLUR_RADIUS);
                boxBlurV(tmp, px, w, h, BLUR_RADIUS);
            }
            // ★ createBitmap(int[], ...) 内部会拷一份，所以 px 可以直接丢
            Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            long ms = (System.nanoTime() - t0) / 1000000L;
            if (ms >= BLUR_WARN_MS) {
                Log.w(TAG, "背景模糊耗时 " + ms + "ms（" + sw + "x" + sh + " → " + w + "x" + h
                        + "），主线程上超过一帧，切背景时可能掉帧");
            }
            return out;
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
