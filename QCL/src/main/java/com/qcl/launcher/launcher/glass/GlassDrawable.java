package com.qcl.launcher.launcher.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * ★★★ 液态玻璃 · **边缘折射**面板背景（v3.3）。
 *
 * <p>把它设成任意 View 的背景，那个 View 的**边缘一圈**就会折射背后的背景；
 * 面板内部完全透明，露出真实背景。
 *
 * <h3>v3.3 相对 v3.2（只有两处，都是真机上能看见/能感觉到的）</h3>
 * <ol>
 *   <li><b>只画四条边带</b> —— v3.2 是整块 {@code drawRect}，着色器内部虽然对
 *       {@code depth >= band} 的像素直接 return，但那个像素**已经被光栅化、着色器已经跑过**了。
 *       一块 1080×2000 的全屏面板 = 216 万次片元调用，而 16dp 边带只需 ~29.5 万次
 *       ⇒ 现在少跑 <b>86%</b>。用户抱怨的「性能很差」主要就来自这里（面板越大越亏）。</li>
 *   <li><b>外缘 1px 抗锯齿</b> —— 面板原来的背景已被玻璃替换，这一圈就是面板**可见的边**；
 *       v3.2 的 {@code if (sd >= 0) return} 是硬切，圆角上直接看到锯齿。</li>
 * </ol>
 *
 * <h3>★ v3.2 的折射数学移植自 kyant0 的 Backdrop 库（Apache-2.0）</h3>
 * 参考工程：{@code D:\A\AndroidLiquidGlass-2.0.1\AndroidLiquidGlass-2.0.1}。
 * 那个库是 Compose Multiplatform 的，不能直接依赖（QCL 没有 Compose）；
 * 只移植 AGSL 数学，详见 {@link GlassShader} 的类注释。
 *
 * <h3>★ 与 3.0 的差别（用户看过真机后的要求）</h3>
 * <table border="1">
 *   <tr><th></th><th>3.0</th><th>v3.2 本版</th></tr>
 *   <tr><td>面板内部</td><td>画一份背景副本（模糊 + 色调）</td><td><b>完全透明</b>，露出真实背景</td></tr>
 *   <tr><td>边缘</td><td>菲涅尔白边 + 高光 + 投影</td><td><b>只有折射</b>，无白边</td></tr>
 *   <tr><td>模糊</td><td>背景预模糊</td><td><b>不模糊</b></td></tr>
 *   <tr><td>每像素采样</td><td>3 次（RGB 色散）</td><td><b>1 次</b></td></tr>
 *   <tr><td>输出像素</td><td>整块面板</td><td><b>只有边缘一圈</b></td></tr>
 * </table>
 *
 * <p>「面板内部透明」不只是省性能 —— 它同时修掉了用户报的
 * <b>「图片还保留在一个板块上面滑动」</b>：3.0 在面板里画了背景副本，
 * 面板一滚动副本跟着走，看起来就像图片被粘在板块上。
 *
 * <h3>★★ 降级链（任何一层失效都不会崩，也不会画出假效果）</h3>
 * <pre>
 *   ① AGSL 边缘折射    —— RuntimeShader 可用时（API 33+ 且驱动能编译）
 *   ② 原背景 + 放大环   —— 着色器不可用：保留面板底色，只在边缘画 1.25x 透镜
 *   ③ 直接 return      —— 连原图都没有（{@code backdrop()==null}）：什么都不画
 * </pre>
 * 降级路径不把模糊壁纸整块填进面板，避免低版本设备首屏出现重复、错位的背景副本。
 */
public final class GlassDrawable extends Drawable {

    private static final String TAG = "QCLGlass";
    private static boolean sLoggedDrawPath;

    // ───────────────── 静态共享资源 ─────────────────

    /** 全局共享的着色器实例池 —— RuntimeShader 每次都 new 会有编译开销。 */
    private static final java.util.ArrayDeque<RuntimeShader> sPool = new java.util.ArrayDeque<>();
    private static final int POOL_MAX = 32;

    private static RuntimeShader acquireShader() {
        synchronized (sPool) {
            RuntimeShader s = sPool.pollFirst();
            if (s != null) {
                return s;
            }
        }
        return GlassShader.newInstance();
    }

    private static void releaseShader(RuntimeShader s) {
        if (s == null) {
            return;
        }
        synchronized (sPool) {
            if (sPool.size() < POOL_MAX) {
                sPool.addFirst(s);
            }
        }
    }

    // ───────────────── 实例状态 ─────────────────

    private final View owner;

    /** 圆角（像素）。 */
    private float radius = -1f;
    /** 折射带宽度（dp）。实际像素值还会被面板短边夹住，见 {@link #drawShader}。 */
    private float bandDp = 22f;

    /** 默认折射增益。{@link GlassApplier} 直接用它，不再按带宽二次换算。 */
    public static final float REFR_GAIN_DEFAULT = 2.2f;

    /** 增益的允许区间 —— 越界就是「搬不动」或「折叠成条纹」，不允许调出去。 */
    private static final float REFR_GAIN_MIN = 1.2f;
    private static final float REFR_GAIN_MAX = 2.6f;

    /**
     * ★★★ v4.2：外缘最大位移 = 带宽 × 这个倍数（**无量纲**，不是 dp）。
     *
     * <p>为什么从「面板高度的比例」改成「带宽的倍数」：v4.1 用的是面板高度，
     * 结果是同一台手机上强弱差 15 倍 —— 40dp 的列表项位移 36px（带宽 66px，搬不动），
     * 整屏面板位移 708px（10.7 倍，边缘折叠成条纹）。
     * 用户报的「一坨屎」「有些手机不行」就是这一条：**同一个效果，面板一大就烂、一小就看不见**。
     *
     * <p>改成带宽的倍数后，位移/带宽 恒等于 {@code 0.983 × refrGain}，
     * 与屏幕尺寸、密度、朝向、面板大小全部无关。
     *
     * <p>区间 [1.2, 2.6]：低于 1.5 搬不动（v3.2 的结论），高于 2.5 边缘内容会折叠。
     * 2.2 ⇒ 实际约 2.16 倍，中间偏强，对应「背景扭曲加大」的要求。
     */
    private float refrGain = REFR_GAIN_DEFAULT;
    /** 是否叠加「指向圆心的径向分量」—— 这一项是「玻璃有厚度」的观感来源。 */
    private boolean depthEffect = true;

    /**
     * ★★★ v3.6：面板**原来的背景**。玻璃只画边缘那一圈，里面要把原背景画回去。
     *
     * <h3>为什么必须画回去（这是「液态玻璃没有」的根因）</h3>
     * {@link GlassApplier} 把面板原背景换成了这个 Drawable，而本类的内部区域是
     * <b>完全透明</b>的（用户要求「只要一层畸变圈」）。于是：
     * <ul>
     *   <li>{@code launcher_button_parent} 本来是 {@code #24FFFFFF} 的磨砂白底 ——
     *       一换就变成<b>彻底透明</b>，设置页每一行、每一张卡片的底色全部消失；</li>
     *   <li>{@code qcl_button_gray} / {@code qcl_gray_translucent_bar} 同理。</li>
     * </ul>
     * 用户看到的就是「开了玻璃，界面上的板块全没了」⇒ 报上来写成「液态玻璃没有」。
     *
     * <h3>★ 为什么这不违反「不要背景副本」</h3>
     * 3.0 被否掉的做法是在面板里画**壁纸的副本** —— 面板一滚动副本跟着走，
     * 就是用户说的「图片粘在板块上滑动」。这里画的是面板**自己的** drawable
     * （一片纯色 / 渐变 / 磨砂），它不随滚动变化，也不含壁纸内容，
     * 所以不会重现那个问题，同时把面板的可见性保住了。
     *
     * <p>★ 附带收益：原背景多是带 {@code state_pressed} 的 selector / ripple，
     * 状态转发（见 {@link #onStateChange}）之后，**按压反馈也一起回来了**。
     */
    private Drawable originalBackground;

    /**
     * ★★★ v3.7：原背景画回来时的**不透明度**（0~1）。
     *
     * <h3>为什么从「原样画回」改成「按比例画回」</h3>
     * v3.6 是 1:1 画回，解决了「板块凭空消失」，但把面板原本的**磨砂白底**
     * （{@code qcl_bottom_nav} 是 {@code #D9FFFFFF} 即 85% 白）整块搬了回来 ⇒
     * 用户看到的是一块**白灰色卡片**，加上边缘采样的是被放大 4.9 倍的软壁纸，
     * 合起来就是「液态玻璃变成了高斯模糊还是白灰色的」。
     *
     * <p>真正的液态玻璃：**内部几乎透明**（壁纸清晰透出），面板的「存在感」
     * 由边缘那一圈折射 + 倒角明暗提供，而不是靠一层不透明底色。
     * 所以这里取 0.16 —— 原背景 85% 白 ⇒ 实际约 14% 白，是一层极淡的玻璃色调；
     * 原背景 14% 白 ⇒ 约 2%，几乎完全透明，正是一块玻璃该有的样子。
     */
    private float originalAlpha = 0.16f;

    /**
     * ★★★ v3.5：当前是否被按住。
     *
     * <p><b>为什么必须自己做这件事</b>：{@link GlassApplier} 把面板原来的背景
     * （很多是带 {@code state_pressed} 的 selector / ripple）换成了这个 Drawable。
     * 原来「按下去变亮一点」的反馈就**跟着原背景一起消失了** —— 用户点设置页的每一行，
     * 手指按下去到页面切换之间没有任何视觉变化，体感就是「点不动」。
     *
     * <p>★ 不打算用「涂一层半透明色」来补：用户明确要求面板内部透明、不要白边，
     * 涂色会把设计推翻。这里用**光学响应**代替：按住时折射偏移略微加大，
     * 边缘那圈「被撑开」得更明显 —— 像手指压在一块软玻璃上。
     */
    private boolean pressed = false;

    /**
     * 按住时折射偏移的放大倍数。
     *
     * <p>★ 只放大**偏移**、不动带宽：带宽一变，边缘带的宽度就跳一下，
     * 看起来像面板尺寸变了。1.4 是「能感觉到但不夸张」的量级。
     */
    private static final float PRESS_GAIN = 1.4f;

    /**
     * ★★★ v3.7：按住程度的**平滑值**（0 松开 → 1 按到底），驱动 {@code uPressT}。
     *
     * <p>为什么不用布尔量直接切：折射偏移瞬间放大 45% 是「跳变」，
     * 看起来像面板抖了一下。用 120ms 缓动走过去，才是「玻璃被手指压下去」的观感。
     */
    private float pressT = 0f;

    /** 按住 / 松开的过渡时长（ms）。按下要快（跟手），回弹要慢一点（有阻尼感）。 */
    private static final int PRESS_IN_MS = 110;
    private static final int PRESS_OUT_MS = 240;

    /** 正在跑的按压动画（同一时刻只会有一个）。 */
    private android.animation.ValueAnimator pressAnim;

    /**
     * ★★★ v3.7：入场淡入的起点时刻。
     *
     * <p>为什么用时间戳而不是 ValueAnimator：一次页面切换可能有十几块面板同时挂上玻璃，
     * 十几个 Animator 各自 invalidate，不如「各自读一次时钟、自己算进度」便宜。
     * 每块面板只需要在 220ms 窗口内 {@code invalidateSelf()}。
     */
    private long createdAt = 0L;
    private static final int APPEAR_MS = 220;

    /** 入场是否已经走完（走完就不再 invalidate，避免无谓重绘）。 */
    private boolean appearDone = false;

    /** 入场进度 0~1（驱动 {@code uAppear}）。 */
    private float appearT = 1f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] locBuf = new int[2];

    /** 面板内容左上角的屏幕坐标（含动画位移），由 {@link #panelOrigin} 填写。 */
    private final int[] originBuf = new int[2];
    /** 换算祖先局部坐标用的窗口坐标缓冲。 */
    private final int[] winBuf = new int[2];

    /** ★★★ v4.4：读「正在跑的 View Animation」用到的三个复用对象（每帧都会用，不能 new）。 */
    private final android.view.animation.Transformation animTf =
            new android.view.animation.Transformation();
    private final Matrix animMtx = new Matrix();
    private final float[] animPts = new float[2];

    /**
     * ★★★ v4.4：着色器绘制矩形被裁到**圆角路径**上用的路径（见 {@link #drawShader}）。
     *
     * <p>为什么不每帧 new：滚动时几百块面板每帧都要用一次。
     */
    private final Path shaderClipPath = new Path();

    private RuntimeShader shader;

    // ★★ uniform 缓存：位置每帧都变，但尺寸/圆角/带宽不变。
    //    滚动时几百块面板 × 9 个 uniform = 几千次 JNI 调用，缓存能砍掉大部分。
    private float lastBand = Float.NaN;
    private float lastStrength = Float.NaN;
    private float lastRadius = Float.NaN;
    private float lastDepth = Float.NaN;
    private int lastW = -1, lastH = -1;
    private long lastBgVersion = -1;
    /**
     * ★★★ v4.2：屏幕尺寸单独缓存。
     *
     * <p>原来 {@code uScreenSize} 跟「背景版本」一起设 ⇒ 横竖屏切换时背景没换、
     * 版本号不变，这个 uniform 就一直停在旧值。而位移是「像素 ÷ 屏幕尺寸」换算的，
     * 分母错了整块面板的折射方向/幅度就全错 —— 旋转后要么不动、要么乱扭。
     * 这是「有些方向不行」的成因，和面板尺寸那条是两个独立的 bug。
     */
    private float lastScreenW = Float.NaN, lastScreenH = Float.NaN;
    /** v3.7：两个动画量的缓存，避免每帧都走 JNI。 */
    private float lastPressT = Float.NaN;
    private float lastAppear = Float.NaN;

    /**
     * v4.0：光学常量是否已经设过。
     *
     * <p>这些值（IOR / 色散 / 菲涅尔 / 眩光）对所有面板都一样，一个着色器实例只需要设一次。
     * 着色器是从池子里取的，取到「别人用过的」也没关系 —— 值相同，重设无害。
     */
    private boolean opticsSet = false;

    /** v4.0：上一次用的深浅色（-1 = 还没设过）。切主题时要重设色调。 */
    private int lastNight = -1;

    /** v4.0：当前是不是夜间模式 —— 决定玻璃色调（深色玻璃配浅色字 / 浅色玻璃配深色字）。 */
    private boolean isNightMode() {
        try {
            if (owner != null) {
                int mode = owner.getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // 降级②的缓存
    private BitmapShader magShader;
    private Bitmap magShaderBg;
    private int magW = -1, magH = -1;
    /**
     * ★★★ v4.4：放大环的缓存还必须带上**面板当时的屏幕位置**。
     *
     * <p>漏了它的后果：缓存键只看「图 / 宽 / 高」，于是面板一移动（滚动、切页动画），
     * 放大环的采样窗口还停在第一次绘制时那个位置 —— 表现为「边框那一圈的内容不跟着走」。
     * 这是「切换界面时框不动」的成因之一。
     */
    private int magOx = Integer.MIN_VALUE, magOy = Integer.MIN_VALUE;
    private final Path outerPath = new Path();
    private final Path innerPath = new Path();

    /** {@link #sampleSpace} 的复用缓冲：{x, y, w, h}。避免每帧分配。 */
    private final int[] spaceBuf = new int[4];

    /**
     * ★★★ v3.0.6：背景图占据的**屏幕矩形** —— 取样换算的唯一基准。
     *
     * <h3>为什么不能直接用屏幕尺寸（「被拉长」的根因）</h3>
     * 三条绘制路径都做同一件事：把「面板在屏幕上的坐标」换算成「背景位图的像素坐标」。
     * 这个换算只有在「位图铺满的区域」和「换算用的矩形」完全一致时才正确。
     *
     * <p>而位图是 {@code launcher_layout} 的背景，{@code BitmapDrawable} 默认
     * {@code FILL}（fitXY）⇒ 它铺满的是 <b>host 的 bounds</b>。host 虽然
     * {@code match_parent}，但挂在 Activity 内容区里，有状态栏/导航栏时
     * 高度比 {@code displayMetrics.heightPixels} 小一截，左上角也不在 (0,0)。
     *
     * <p>以前用 {@code displayMetrics} 当分母、原点当 (0,0)：分母偏大 ⇒ 纵向内容被压缩
     * ⇒「拉长」；原点没减状态栏 ⇒ 整体下移。两者叠加就是用户报的
     * 「背景不是在那里的也被粘上去、还被拉长」。
     *
     * <p>★ host 还没量过（{@code hostValid()==false}）时退回屏幕尺寸，
     * 保证任何调用顺序下都不会除零、也不会突然全黑。
     *
     * @param out 长度 ≥ 4 的复用数组，写入 {x, y, w, h}
     */
    private void sampleSpace(int[] out) {
        if (GlassBackdrop.hostValid()) {
            out[0] = GlassBackdrop.hostX();
            out[1] = GlassBackdrop.hostY();
            out[2] = Math.max(1, GlassBackdrop.hostWidth());
            out[3] = Math.max(1, GlassBackdrop.hostHeight());
        } else {
            out[0] = 0;
            out[1] = 0;
            out[2] = Math.max(1, sW);
            out[3] = Math.max(1, sH);
        }
    }

    public GlassDrawable(View owner) {
        this.owner = owner;
    }

    // ───────────────── 链式配置 ─────────────────

    public GlassDrawable radiusPx(float r) {
        this.radius = r;
        return this;
    }

    public GlassDrawable radiusDp(float dp) {
        float d = owner != null ? owner.getResources().getDisplayMetrics().density : 3f;
        this.radius = dp * d;
        return this;
    }

    /** 折射带宽度（dp）。越大，被扭曲的边越宽。 */
    public GlassDrawable band(float dp) {
        this.bandDp = dp;
        return this;
    }

    /**
     * 折射增益（带宽的倍数）。默认 {@link #REFR_GAIN_DEFAULT}。
     *
     * <p>★ 参数是**无量纲倍数**，不是 dp。v4.2 之前这里是 {@code strength(dp)}，
     * 再除以 32 换算成「占面板高度的比例」—— 那个量纲错误正是「面板一大就烂」的来源。
     * 现在直接给倍数，调用方不可能再传错单位。
     */
    public GlassDrawable refrGain(float gain) {
        this.refrGain = Math.max(REFR_GAIN_MIN, Math.min(REFR_GAIN_MAX, gain));
        return this;
    }

    /** 深度感：法线叠加指向圆心的径向分量，让玻璃看起来有厚度。 */
    public GlassDrawable depthEffect(boolean on) {
        this.depthEffect = on;
        return this;
    }

    /**
     * ★★★ v3.7：设置面板原背景，并按 {@code alpha} 压成半透明画回来。
     *
     * <p>★ 为什么用 {@code mutate().setAlpha()} 而不是每帧 {@code saveLayerAlpha}：
     * {@code saveLayerAlpha} 每次绘制都要分配一块离屏缓冲，全屏面板就是一次
     * 1080×2340 的纹理分配 —— 几百块面板滚动时会直接把显存打爆。
     * 而 {@code setAlpha} 只是改 Drawable 的一个字段，零成本。
     *
     * <p>⚠️ 副作用：原 Drawable 的 alpha 被**改掉**了。关掉玻璃时必须复原
     * （见 {@link #restoreOriginal()}），否则面板会永久停在半透明。
     */
    public GlassDrawable originalBackground(Drawable d, float alpha) {
        this.originalAlpha = Math.max(0f, Math.min(1f, alpha));
        if (d != null) {
            try {
                d = d.mutate();
                d.setAlpha(Math.round(255f * this.originalAlpha));
            } catch (Throwable ignored) {
                // mutate / setAlpha 不支持就退回「不压透明度」，至少不会崩
            }
        }
        this.originalBackground = d;
        return this;
    }

    /** 兼容旧签名：按默认 16% 画回。 */
    public GlassDrawable originalBackground(Drawable d) {
        return originalBackground(d, 0.16f);
    }

    /**
     * ★ 归还前把原背景的透明度复原。
     *
     * <p>被 {@link GlassApplier#detachTree} 调用 —— 关掉玻璃时面板要回到
     * 「原来的样子」，包括原来的不透明度。
     */
    void restoreOriginal() {
        if (originalBackground != null) {
            try {
                originalBackground.setAlpha(255);
            } catch (Throwable ignored) {
            }
        }
    }

    // ───────────────── 绘制 ─────────────────

    /**
     * ★★★ v3.0.6：上一次「位置自检」看到的屏幕坐标。
     */
    private int lastSyncX = Integer.MIN_VALUE, lastSyncY = Integer.MIN_VALUE;

    /**
     * ★★★ v3.0.6：这块面板这一帧**需不需要重画**（位置变了 / 祖先上有动画在跑）。
     *
     * <h3>为什么必须有这个 —— 「背景被粘到板块上」的根因</h3>
     * 玻璃内部是「按屏幕坐标去背景图上取一块」，取样基准是 {@code uPanelOrigin}
     * （面板当前的屏幕位置），而这个 uniform 是在 {@link #drawShader} 里设的
     * ⇒ **只有本 Drawable 被重新绘制时才会更新**。
     *
     * <p>而 View 只是被**移动**（滚动、位移动画）时，Android 会复用它的显示列表，
     * <b>根本不会重新绘制背景</b>。具体到 {@code RecyclerView} 滚动：
     * 它走 {@code offsetChildrenVertical → offsetTopAndBottom}，改的是子 View 的
     * {@code mTop} —— 既不是 {@code scrollTo}（所以 {@code OnScrollChangedListener} 不触发），
     * 也不触发 layout（所以 {@code OnLayoutChangeListener} 不触发）。
     *
     * <p>于是面板在屏幕上滑过去了，{@code uPanelOrigin} 却停在原地，
     * 取到的永远是同一块壁纸 ⇒ 视觉上就是**壁纸被贴在板块上跟着一起滑**。
     * 用户报的「就算背景不是在那里的也会被粘到板块上」就是这一条。
     *
     * <p>★ 为什么是「位置变了才 invalidate」而不是每帧无条件 invalidate：
     * 静态界面不产生绘制帧，{@link GlassApplier} 的 OnPreDraw 钩子只在真有帧时跑；
     * 这里再筛一道，位置没变就不打扰 —— 滚动停下就自动归零，不常驻耗电。
     *
     * @return true = 调用方应该 {@code view.invalidate()}
     */
    boolean needsRedraw() {
        if (owner == null) {
            return false;
        }
        int x, y;
        try {
            owner.getLocationOnScreen(locBuf);
            x = locBuf[0];
            y = locBuf[1];
        } catch (Throwable t) {
            return false;
        }
        if (x != lastSyncX || y != lastSyncY) {
            lastSyncX = x;
            lastSyncY = y;
            return true;
        }
        // 位置没变，但**祖先上可能正在跑 View Animation**（切页）。
        // 那种情况下布局位置全程不变、视觉位置每帧都在变，只能靠动画状态判断。
        return hasRunningAncestorAnimation();
    }

    /** 自己或任意祖先上有没有还在跑的 View Animation。 */
    private boolean hasRunningAncestorAnimation() {
        try {
            View cur = owner;
            int depth = 0;
            while (cur != null && depth < 32) {
                android.view.animation.Animation a = cur.getAnimation();
                if (a != null && !a.hasEnded()) {
                    return true;
                }
                android.view.ViewParent p = cur.getParent();
                cur = (p instanceof View) ? (View) p : null;
                depth++;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * ★★★ v4.4：面板内容左上角的**屏幕坐标** —— 含正在跑的 View Animation 位移。
     *
     * <h3>为什么不能直接用 {@code getLocationOnScreen()}</h3>
     * 页面切换用的是 {@code view.setAnimation(AnimationUtils.makeInAnimation(...))}，
     * 那是**老的 View Animation**：它在绘制时把变换矩阵塞进 RenderNode 的
     * {@code animationMatrix}（或软件渲染时 concat 到 canvas），
     * <b>完全不改 {@code mLeft/mTop}</b>。
     *
     * <p>于是动画期间 {@code getLocationOnScreen()} 返回的是**布局位置（终点位置）**，
     * 而面板实际画在**滑动中的位置**。玻璃按布局位置去背景上取样 ⇒
     * 面板外形在滑、里面的画面却钉在终点不动。用户报的
     * <b>「液态玻璃界面在动但是框不动」</b>就是这一条。
     *
     * <h3>为什么用 {@code Animation.getTransformation} 而不是 {@code canvas.getMatrix()}</h3>
     * 硬件加速下每块面板有自己的 RenderNode，传给 {@code Drawable.draw} 的 canvas
     * 矩阵是**单位矩阵**，动画矩阵在 RenderNode 上（{@code setAnimationMatrix}），
     * 从 canvas 里根本读不到。而 {@code getAnimation()} 拿到的是同一个 Animation 对象，
     * 用同一个时钟求值，结果与 View 自己算的那一份**逐位相同**。
     *
     * <p>★ 只做「加法补偿」：动画为空 / 已结束时增量为 0，
     * 静止时的取值与改之前**完全一致** —— 不会把原本正常的界面改坏。
     *
     * @param bndLeft bounds 左边界（Drawable 坐标，含 View 的 padding）
     * @param bndTop  bounds 上边界
     * @param out     输出 [x, y]（屏幕坐标）
     */
    private void panelOrigin(int bndLeft, int bndTop, int[] out) {
        int scrX = 0, scrY = 0;
        if (owner != null) {
            try {
                owner.getLocationOnScreen(locBuf);
                scrX = locBuf[0];
                scrY = locBuf[1];
            } catch (Throwable ignored) {
            }
        }
        out[0] = scrX + bndLeft;
        out[1] = scrY + bndTop;
        if (owner == null) {
            return;
        }
        // ★★★ 动画增量。注意：**动画挂在页面根视图上**（CustomAnimationUtils 传的是
        //   page root），面板自己是 getAnimation()==null 的 —— 所以必须沿祖先链向上找，
        //   只查 owner 一层等于没做。
        try {
            owner.getLocationInWindow(winBuf);
            final float px = winBuf[0] + bndLeft;
            final float py = winBuf[1] + bndTop;
            float dx = 0f, dy = 0f;
            View cur = owner;
            int depth = 0;
            while (cur != null && depth < 32) {
                android.view.animation.Animation a = cur.getAnimation();
                if (a != null && !a.hasEnded()) {
                    animTf.clear();
                    long now = android.view.animation.AnimationUtils.currentAnimationTimeMillis();
                    // 返回 false = 这一帧动画已经走完（终态），终态增量本来就是 0
                    if (a.getTransformation(now, animTf)) {
                        animMtx.set(animTf.getMatrix());
                        // 把「面板内容左上角」换算到**这一层祖先**的局部坐标，
                        // 过一遍它的动画矩阵，差值就是这一层贡献的位移。
                        // ★ 页面切换用的是纯 TranslateAnimation，对任意点差值都相同；
                        //   写成映射而不是直接取平移分量，是为了缩放类动画也算得对。
                        cur.getLocationInWindow(winBuf);
                        animPts[0] = px - winBuf[0];
                        animPts[1] = py - winBuf[1];
                        float lx = animPts[0];
                        float ly = animPts[1];
                        animMtx.mapPoints(animPts);
                        dx += animPts[0] - lx;
                        dy += animPts[1] - ly;
                    }
                }
                android.view.ViewParent p = cur.getParent();
                cur = (p instanceof View) ? (View) p : null;
                depth++;
            }
            out[0] = Math.round(out[0] + dx);
            out[1] = Math.round(out[1] + dy);
        } catch (Throwable ignored) {
            // 读不到动画就按布局位置画 —— 退化成改之前的行为，不会更差
        }
    }

    /**
     * ★★★ v4.4：清空 uniform 的「已设置」缓存。
     *
     * <h3>为什么必须清（这是「没有圆角的框」的根因）</h3>
     * {@code shader} 是从**全局池**里取的（见 {@link #acquireShader()}），
     * {@link #release()} 会把它还回去。还回去时本对象的
     * {@code lastW / lastH / lastRadius / lastBand / lastBgVersion ...} <b>并没有清</b>。
     *
     * <p>于是「释放过、又拿到另一块面板用过的实例」时，下面这段判断
     * <pre>
     *   if (lastW != w || lastH != h) { shader.setFloatUniform("uPanelSize", w, h); }
     * </pre>
     * 认为「尺寸没变」而**跳过设置** ⇒ 着色器里的 {@code uPanelSize / uRadius /
     * uRefThickness / uTint / uBg} 全是**上一块面板的值**。
     *
     * <p>后果：SDF 与真正绘制的那个 {@code w×h} 矩形对不上。若池里那块面板更大，
     * 整个矩形都落在 {@code sd < 0} 里 ⇒ {@code aa = clamp(-sd,0,1) = 1}
     * ⇒ <b>整块矩形被涂满、四个角是直角</b>（因为 {@code drawShader} 画的是
     * {@code drawRect}，而形状态全靠 SDF 自己裁）。这正是用户描述的
     * <b>「有一个没有圆角的框」</b>。
     *
     * <p>{@code releaseTree()} 在 {@code onChildViewRemoved} 上被调用 ——
     * 切页时视图的增删、列表回收都会走到它，所以这个 bug 表现为「一切换界面就出现」。
     */
    private void resetUniformCache() {
        lastBand = Float.NaN;
        lastStrength = Float.NaN;
        lastRadius = Float.NaN;
        lastDepth = Float.NaN;
        lastW = -1;
        lastH = -1;
        lastBgVersion = -1;
        lastScreenW = Float.NaN;
        lastScreenH = Float.NaN;
        lastPressT = Float.NaN;
        lastAppear = Float.NaN;
        lastNight = -1;
        opticsSet = false;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bnd = getBounds();
        int w = bnd.width();
        int h = bnd.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        Bitmap bg = GlassBackdrop.backdrop();
        if (bg == null || bg.isRecycled()) {
            // ③ 没有背景 → 什么都不画（不要涂色块，那会盖住背景）
            return;
        }
        // ★★★ v3.7：入场淡入进度。
        //   第一次 draw 才算「挂上」，之后 220ms 内线性从 0 走到 1。
        //   没走完就自己再 invalidate 一次 —— 不依赖任何外部定时器。
        if (createdAt == 0L) {
            createdAt = android.os.SystemClock.uptimeMillis();
        }
        if (!appearDone) {
            long dt = android.os.SystemClock.uptimeMillis() - createdAt;
            appearT = Math.min(1f, dt / (float) APPEAR_MS);
            if (appearT >= 1f) {
                appearDone = true;
            } else {
                invalidateSelf();
            }
        }
        // ★★★ v4.0：面板底色**不再画回来了**。
        //   ReGlass 的渲染方式是「整块面板 = 模糊后的背景 + 透镜边缘」，
        //   面板自己是透明的 —— 原来那层 16% 白底画在下面，只会把玻璃糊成一块灰白卡片
        //   （用户两次报「白灰色」的根因之一，另一次是壁纸被放大 4.9 倍）。
        //   ★ 只有着色器不可用时才画回原背景，否则「玻璃失效 + 面板消失」会同时发生。
        boolean drawn = false;
        // ★★★ 绘制失败过就不再重试：否则每帧构造一次异常、每帧降级一次，
        //   既烧 CPU 又把线索埋掉（详见 GlassShader#markDrawFailed 的注释）。
        if (!GlassShader.drawFailed()
                && GlassShader.usable() && GlassShader.sharedBackdropShader() != null) {
            try {
                drawn = drawShader(canvas, w, h, bg);
            } catch (Throwable t) {
                GlassShader.markDrawFailed(t);
            }
        }
        if (!drawn) {
            if (originalBackground != null) {
                try {
                    originalBackground.setBounds(bnd.left, bnd.top, bnd.right, bnd.bottom);
                    originalBackground.draw(canvas);
                } catch (Throwable ignored) {
                }
            }
            try {
                Bitmap ringSrc = GlassBackdrop.blurred();
                drawRing(canvas, w, h,
                        (ringSrc != null && !ringSrc.isRecycled()) ? ringSrc : bg);
            } catch (Throwable ignored) {
            }
        }
        if (!sLoggedDrawPath) {
            sLoggedDrawPath = true;
            android.util.Log.i(TAG, "玻璃首帧实际路径=" + (drawn ? "AGSL" : "原背景+边缘透镜")
                    + "，SDK=" + android.os.Build.VERSION.SDK_INT
                    + "，检测=" + GlassShader.pathDescription());
        }
    }

    /** ① AGSL 边缘畸变。返回 false = 这条路径不可用。 */
    private boolean drawShader(Canvas canvas, int w, int h, Bitmap bg) {
        if (!GlassShader.usable()) {
            return false;
        }
        BitmapShader bgShader = GlassShader.sharedBackdropShader();
        if (bgShader == null) {
            return false;
        }
        if (shader == null) {
            shader = acquireShader();
            // ★★★ v4.4：从池里取到的是**别人用过的**实例，uniform 还停在上一块面板的值。
            //   而下面所有 uniform 都靠 lastXxx 缓存决定「要不要设」—— 那些缓存是
            //   本对象的、与 shader 归属无关，不清就会出现「尺寸/圆角沿用别的面板」
            //   ⇒ SDF 与绘制矩形不一致 ⇒ 整块画成方角不透明矩形（见 resetUniformCache）。
            resetUniformCache();
        }
        if (shader == null) {
            return false;
        }

        // ★★★ 原点必须是**绘制内容的屏幕位置**，不是 View 的位置。
        //   背景 Drawable 的 bounds 会带上 View 的 padding（setBounds 传的是
        //   paddingLeft/Top..），所以「内容左上角」= View 屏幕坐标 + bounds.left/top。
        //   早期版本直接用 View 坐标并在 (0,0) 画，控件有 padding 时
        //   畸变圈会整体偏移 padding 那么多，看起来像「玻璃没对准边缘」。
        //   ★ v4.4：改走 panelOrigin，额外叠加切页动画的位移（见该方法注释）。
        Rect bnd = getBounds();
        panelOrigin(bnd.left, bnd.top, originBuf);
        // ★★★ v3.0.6：uPanelOrigin 必须是**背景图坐标系**里的原点，
        //   uScreenSize 必须是背景图实际铺满的尺寸 —— 两者一起换，
        //   否则「拉长 / 整体偏移」照旧（见 sampleSpace 的注释）。
        sampleSpace(spaceBuf);
        int ox = originBuf[0] - spaceBuf[0];
        int oy = originBuf[1] - spaceBuf[1];

        float d = owner != null ? owner.getResources().getDisplayMetrics().density : 3f;
        float dm = spaceBuf[2];
        float dh = spaceBuf[3];
        float r = radius >= 0 ? radius : 24f * d;
        // ★★★ v4.0：参数按 ReGlass 的语义重新映射。
        //   · thickness = 原来的「折射带宽度」= **玻璃厚度**（像素）
        //   · refrGain  = 外缘最大位移，单位是**带宽的倍数**
        //   ReGlass 的剖面在 depth >= thickness 处把位移归零，所以厚度同时也是
        //   「折射影响范围」—— 一个参数表达一件事，和它的实现一致。
        //
        // ★★★ v4.2：带宽必须按**面板短边**夹住，否则同一套参数在不同面板上是两种东西：
        //   · 22dp=66px 的带套在 30dp(90px) 高的横条上 ⇒ 整块都是带，内部不存在；
        //   · 套在整屏面板上只占 3% ⇒ 看不出是「一圈」，像整块被轻微抹了一下。
        //   两个上限：
        //     · 短边 35% —— 再宽就不是「边缘」而是「整块」；
        //     · 短边/2 − 圆角 —— 两条对边的带不能在圆角处相交。
        int minSide = Math.min(w, h);
        float bandCap = Math.max(2f, Math.min(minSide * 0.35f, minSide * 0.5f - r));
        float thickness = Math.max(2f, Math.min(bandDp * d, bandCap));
        // ★★★ v4.2：位移量 = 带宽 × 增益。
        //   v4.1 是 0.22 × (strengthDp/32) 再乘面板高度 —— 面板高度一变大就爆炸。
        //   现在这个式子与面板尺寸、屏幕尺寸、密度、朝向全部无关。
        float amount = refrGain;
        // ★ 深浅色：面板现在整块是玻璃，不再有原来的白底托着，
        //   所以色调必须跟着主题走 —— 否则夜间模式的浅色文字会落在浅色玻璃上。
        //   （用户报的「左侧 UI 文字颜色有问题」就是这一条：夜间 colorPureBlack 是
        //    #FFEDE7DE，而导航栏底色还是 85% 白。）
        boolean night = isNightMode();
        float tintA = night ? 0.30f : 0.12f;
        float tintC = night ? 0f : 1f;

        // ★ 只设变化的那几个 —— 见字段注释
        long bgVer = GlassBackdrop.version();
        if (lastBgVersion != bgVer) {
            shader.setInputShader("uBg", bgShader);
            // ★ v4.0：尺寸要用**模糊图**的尺寸 —— uBg.eval 的坐标空间是位图像素。
            Bitmap blur = GlassBackdrop.blurred();
            Bitmap forSize = (blur != null && !blur.isRecycled()) ? blur : bg;
            shader.setFloatUniform("uBgSize", forSize.getWidth(), forSize.getHeight());
            lastBgVersion = bgVer;
        }
        // ★★★ v4.2：屏幕尺寸独立判断（原因见 lastScreenW 的注释）
        if (lastScreenW != dm || lastScreenH != dh) {
            shader.setFloatUniform("uScreenSize", dm, dh);
            lastScreenW = dm;
            lastScreenH = dh;
        }
        // 光学常量：一个着色器实例一辈子不变，设一次就够。
        // ★ 数值全部照 ReGlassConfig 的默认值，不要凭感觉改 ——
        //   这组值就是用户给的那张效果图所用的值。
        if (!opticsSet) {
            shader.setFloatUniform("uRefFactor", 1.4f);    // IOR
            shader.setFloatUniform("uRefDisp", 7f);        // 色散强度
            shader.setFloatUniform("uFresRange", 30f);
            shader.setFloatUniform("uFresHard", 0.2f);     // 20/100
            shader.setFloatUniform("uFresFac", 0.2f);      // 20/100
            shader.setFloatUniform("uGlareRange", 30f);
            shader.setFloatUniform("uGlareHard", 0.2f);    // 20/100
            opticsSet = true;
        }
        if (lastW != w || lastH != h) {
            shader.setFloatUniform("uPanelSize", w, h);
            lastW = w;
            lastH = h;
        }
        if (lastRadius != r) {
            shader.setFloatUniform("uRadius", r);
            lastRadius = r;
        }
        if (lastBand != thickness) {
            shader.setFloatUniform("uRefThickness", thickness);
            lastBand = thickness;
        }
        if (lastStrength != amount) {
            shader.setFloatUniform("uRefrAmount", amount);
            lastStrength = amount;
        }
        if (lastNight != (night ? 1 : 0)) {
            shader.setFloatUniform("uTint", tintC, tintC, tintC);
            shader.setFloatUniform("uTintA", tintA);
            lastNight = night ? 1 : 0;
        }
        // 位置每帧都可能变（滚动 / 动画），必须无条件更新
        shader.setFloatUniform("uPanelOrigin", ox, oy);
        // ★ v3.7：两个动画量 —— 值没变就不传（滚动时每帧都会走这里，省 JNI）
        if (lastPressT != pressT) {
            shader.setFloatUniform("uPressT", pressT);
            lastPressT = pressT;
        }
        if (lastAppear != appearT) {
            shader.setFloatUniform("uAppear", appearT);
            lastAppear = appearT;
        }

        paint.setShader(shader);
        // ★★ 必须先 translate 到 bounds 左上角：着色器里的 fragCoord 是画布局部坐标，
        //    在 (0,0) 画才能保证 fragCoord 落在 [0,w]×[0,h]，SDF 的圆心才对得上。
        int save = canvas.save();
        canvas.translate(bnd.left, bnd.top);
        // ★★★ v4.4：把绘制矩形**裁到圆角路径**上（以前完全靠 SDF 自己裁）。
        //
        //   为什么以前是隐患：形状正确性 100% 依赖 uPanelSize / uRadius / uPanelOrigin
        //   三个 uniform 与这个 w×h 矩形严格一致。一旦哪个 uniform 是错的
        //   （池里取到别人的实例、尺寸刚变、动画中途），SDF 会把整块矩形判成「内部」
        //   ⇒ alpha 恒为 1 ⇒ 画出来就是**一块直角矩形**。
        //   这正是用户报的「有一个没有圆角的框」—— 玻璃面板里冒出一个方角色块。
        //
        //   加裁切后：uniform 再错，最多是**内容**错，形状永远是这个圆角矩形。
        //   ★ 不改观感：着色器的抗锯齿斜坡在 sd∈(-1,0) 即形状**内部**，
        //     而这里裁的是 sd=0 的边界 ⇒ 斜坡一个像素都不被切掉。
        shaderClipPath.reset();
        // ★ 与着色器里 `radius = min(uRadius, min(halfSize.x, halfSize.y))` 同一条夹取，
        //   否则半径超过短边一半时 Path 自己会被钳成别的东西，裁切边界就和 SDF 错开。
        shaderClipPath.addRoundRect(0f, 0f, w, h,
                Math.min(r, Math.min(w, h) * 0.5f), Math.min(r, Math.min(w, h) * 0.5f),
                Path.Direction.CW);
        canvas.clipPath(shaderClipPath);
        // ★★★ v4.0：整块面板都要画。
        //   v3.3~v3.7 只画四条边带，是为了省片元（当时「内部全透明」，
        //   中间那些像素画了也是白画）。但 ReGlass 的渲染方式里**内部就是模糊背景**，
        //   整块都是有效内容 ⇒ 不能再只画边带，否则面板中间是空的。
        //   ★ 代价与取舍：面板越大越费。所以 GlassApplier 那边保留了「覆盖范围」
        //     （轻量 ≥200dp / 标准 ≥110dp）—— 小控件不挂玻璃，大面板才值得。
        canvas.drawRect(0f, 0f, w, h, paint);
        canvas.restoreToCount(save);
        paint.setShader(null);
        return true;
    }

    /**
     * ② 降级：环状放大透镜。
     *
     * <p>做法：先在外圆角矩形内画放大背景，再用内圆角矩形把中心恢复原背景。
     * 不依赖 {@code Path.op}，避免部分 ROM 把中空环异常裁成整面板。
     *
     * <p>★ 放大倍率 1.12 比 3.0 的 1.08 略高：因为现在只剩一圈，
     * 放大不够就完全看不出来了。
     */
    private void drawRing(Canvas canvas, int w, int h, Bitmap bg) {
        if (bg.isRecycled() || originalBackground == null) {
            return;
        }
        Rect bnd = getBounds();
        // ★★★ v4.4：同 drawShader —— 必须叠加切页动画位移，否则面板在滑、
        //   放大环里的画面却钉在终点不动。
        panelOrigin(bnd.left, bnd.top, originBuf);
        // ★★★ v3.0.6：同 drawShader —— 换算到背景图坐标系（见 sampleSpace）。
        sampleSpace(spaceBuf);
        int ox = originBuf[0] - spaceBuf[0];
        int oy = originBuf[1] - spaceBuf[1];
        float d = owner != null ? owner.getResources().getDisplayMetrics().density : 3f;
        float r = radius >= 0 ? radius : 24f * d;
        // ★★★ v4.2：与 drawShader 用**同一条**夹取规则 —— 降级路径和主路径
        //   的带宽必须一致，否则着色器编译失败前后会看到两种不同宽度的带。
        int minSide = Math.min(w, h);
        float band = Math.max(2f, Math.min(bandDp * d,
                Math.min(minSide * 0.35f, minSide * 0.5f - r)));

        float sx = bg.getWidth() / (float) spaceBuf[2];
        float sy = bg.getHeight() / (float) spaceBuf[3];
        // ★★★ v4.4：缓存键必须带**屏幕位置** —— 只看「图/宽/高」的话，
        //   面板一移动（滚动、切页动画）放大环仍按第一次那个位置采样，
        //   表现为「边缘那一圈的内容不跟着面板走」。
        if (magShader == null || magShaderBg != bg || magW != w || magH != h
                || magOx != ox || magOy != oy) {
            float cx = (ox + w * 0.5f) * sx;
            float cy = (oy + h * 0.5f) * sy;
            float halfW = w * sx / (2f * RING_MAGNIFY);
            float halfH = h * sy / (2f * RING_MAGNIFY);
            RectF src = new RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
            RectF dst = new RectF(0f, 0f, w, h);
            BitmapShader bs = new BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setRectToRect(src, dst, Matrix.ScaleToFit.FILL);
            bs.setLocalMatrix(m);
            magShader = bs;
            magShaderBg = bg;
            magW = w;
            magH = h;
            magOx = ox;
            magOy = oy;
        }

        outerPath.reset();
        outerPath.addRoundRect(0, 0, w, h, r, r, Path.Direction.CW);
        innerPath.reset();
        float ir = Math.max(0f, r - band);
        innerPath.addRoundRect(band, band, w - band, h - band, ir, ir, Path.Direction.CW);
        int save = canvas.save();
        canvas.translate(bnd.left, bnd.top);
        canvas.clipPath(outerPath);
        fallbackPaint.setShader(magShader);
        canvas.drawRect(0, 0, w, h, fallbackPaint);
        fallbackPaint.setShader(null);
        // 中心恢复原背景，不再用 Path.op 裁中空环，避免 ROM 差异导致整面板覆盖。
        canvas.clipPath(innerPath);
        originalBackground.setBounds(0, 0, w, h);
        originalBackground.draw(canvas);
        canvas.restoreToCount(save);
    }

    private static int sW = 1080, sH = 2340;

    public static void setScreen(int w, int h) {
        sW = Math.max(1, w);
        sH = Math.max(1, h);
    }

    /**
     * 降级环的放大倍率。
     *
     * <p>★ v3.2 从 1.12 提到 1.25：折射带宽从 14dp 提到 16dp、最大偏移从 9dp 提到 32dp，
     * 真·着色器路径的观感强了很多，降级路径若还停在 1.12 就会明显「不是一个东西」。
     * 降级路径没有剖面曲线和深度感，观感仍弱一档，但至少量级对得上。
     */
    private static final float RING_MAGNIFY = 1.25f;

    /** 归还着色器实例（由 GlassApplier 在 View 分离时调）。 */
    void release() {
        RuntimeShader s = shader;
        shader = null;
        releaseShader(s);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        magW = -1;
        magH = -1;
        // ★ v4.4：尺寸变了，放大环的采样窗口也得重算（缓存键里带了 ox/oy，
        //   这里一起清掉，避免「尺寸回到旧值 + 位置刚好相同」时取到过期缓存）
        magOx = Integer.MIN_VALUE;
        magOy = Integer.MIN_VALUE;
    }

    /**
     * ★★★ v3.5：必须是 {@code true}，否则 {@code View.drawableStateChanged()}
     * 根本不会把按下状态转给我们（它只在 {@code bg.isStateful()} 时才
     * {@code bg.setState(...)}）。
     */
    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean p = false;
        if (stateSet != null) {
            for (int s : stateSet) {
                if (s == android.R.attr.state_pressed) {
                    p = true;
                    break;
                }
            }
        }
        // ★ v3.6：状态也转发给原背景 —— 它多半是带 state_pressed 的 selector / ripple，
        //   转发了按压反馈才是真的回来（而不只是折射偏移变大）。
        if (originalBackground != null && originalBackground.isStateful()) {
            try {
                originalBackground.setState(stateSet);
            } catch (Throwable ignored) {
            }
        }
        if (p != pressed) {
            pressed = p;
            animatePress(p ? 1f : 0f);
            return true;
        }
        return false;
    }

    /**
     * ★★★ v3.7：把 {@code pressT} 平滑推到目标值。
     *
     * <p>按下 110ms、松开 240ms —— 按下要跟手，松开要有阻尼。
     * 每帧 {@code invalidateSelf()} 让面板重绘边缘那一圈（只有一圈，成本可控）。
     *
     * <p>★ 用 {@code ValueAnimator} 而不是 {@code View.animate()}：后者作用在 View 的
     * 属性上，而这里要动的是 Drawable 内部的着色器 uniform，跟 View 的变换无关。
     */
    private void animatePress(final float target) {
        try {
            if (pressAnim != null) {
                pressAnim.cancel();
            }
            final float from = pressT;
            if (Math.abs(target - from) < 0.001f) {
                return;
            }
            android.animation.ValueAnimator a =
                    android.animation.ValueAnimator.ofFloat(from, target);
            a.setDuration(target > from ? PRESS_IN_MS : PRESS_OUT_MS);
            // 按下用 decelerate（快速跟上）、松开用 accelerate-decelerate（有回弹感）
            a.setInterpolator(target > from
                    ? new android.view.animation.DecelerateInterpolator(1.8f)
                    : new android.view.animation.AccelerateDecelerateInterpolator());
            a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator anim) {
                    Object v = anim.getAnimatedValue();
                    pressT = v instanceof Float ? (Float) v : target;
                    invalidateSelf();
                }
            });
            pressAnim = a;
            a.start();
        } catch (Throwable t) {
            // 动画起不来也不能影响按压本身
            pressT = target;
            invalidateSelf();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        fallbackPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        fallbackPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
