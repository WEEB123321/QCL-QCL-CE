package com.qcl.launcher.launcher.glass;

import android.graphics.Bitmap;

/**
 * ★★★ 液态玻璃：当前背景位图的**全局共享容器**（v3.1）。
 *
 * <h3>★ 为什么这里不再做模糊</h3>
 * 3.0 的做法是「背景降采样两级 → 放大回去」，把模糊成本从「每面板每帧」搬到「换背景时一次」。
 * 这个优化本身是对的，但用户看过真机效果后的要求是：
 * <b>「其他的不要，比如背景模糊之类的」</b> —— 他只要边缘那圈畸变，不要磨砂。
 * <br>
 * 既然不模糊，这里就只剩一件事：**保存当前背景位图，供所有面板采样**。
 * 于是 {@link #set(Bitmap)} 变成纯引用赋值，**零成本**，换背景时不再有任何卡顿。
 *
 * <h3>⚠️ 为什么仍然需要这个类（而不是每块面板自己去找背景）</h3>
 * <ol>
 *   <li>面板要在 draw 里拿到背景，但 Drawable 没有 Context，也拿不到 Activity 的根布局；</li>
 *   <li>几百块面板如果各自 {@code BitmapDrawable.getBitmap()} 再各建一个 BitmapShader，
 *       会产生几百个 GPU 纹理上传 —— 必须全局共用一张。</li>
 * </ol>
 *
 * <h3>★ 采样的是原图，不是副本</h3>
 * 注意区分两件事：
 * <ul>
 *   <li><b>采样原图</b>（本类做的）—— 面板边缘那一圈从**真实背景**取色，滚动时取的是
 *       「面板当前位置背后的背景」，物理上正确；</li>
 *   <li><b>把整块背景画进面板</b>（3.0 做的）—— 面板里有一份背景副本，面板一滚动副本跟着走，
 *       用户看到的「图片粘在板块上滑动」就是它。v3.1 已经彻底去掉。</li>
 * </ul>
 */
public final class GlassBackdrop {

    private static final String TAG = "QCLGlass";

    /** 当前背景原图。所有面板的 BitmapShader 都绑它。 */
    private static volatile Bitmap sBackdrop;

    /** 每次换背景 +1。面板靠它判断「要不要重新绑定 BitmapShader」。 */
    private static volatile long sVersion = 0;

    public static long version() {
        return sVersion;
    }

    /** 当前背景原图（面板着色器的输入）。 */
    public static Bitmap backdrop() {
        return sBackdrop;
    }

    /** 兼容 3.0 的旧调用名 —— 现在返回的就是原图（不再有模糊版）。 */
    public static Bitmap blurred() {
        return sBackdrop;
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
     * <p>★ v3.1 起**可以在主线程调**：只存引用，不缩放、不解码，耗时接近 0。
     * （3.0 必须开后台线程，因为要做两次降采样 + 一次全尺寸放大。）
     *
     * <p>★ 幂等：同一张图重复传入直接返回。
     */
    public static synchronized void set(Bitmap src) {
        if (src == null || src.isRecycled()) {
            return;
        }
        if (src == sBackdrop) {
            return;
        }
        sBackdrop = src;
        sVersion++;
        // ★ 必须清掉缓存 —— 否则 BitmapShader 还绑在上一张图上，玻璃边缘取到的是旧背景。
        GlassShader.invalidateBackdropShader();
    }

    /** 清空（切回纯色背景时用）。 */
    public static synchronized void clear() {
        sBackdrop = null;
        sVersion++;
        GlassShader.invalidateBackdropShader();
    }
}
