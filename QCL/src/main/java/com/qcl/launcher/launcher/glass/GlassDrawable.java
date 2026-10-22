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
 * <h3>★★ 两层降级（任何一层失效都不会崩，也不会画出假效果）</h3>
 * <pre>
 *   ① AGSL 边缘折射      —— RuntimeShader 可用时（API 33+ 且驱动能编译）
 *   ② 环状放大透镜        —— 着色器不可用：只画边缘一圈，把背景放大 1.25x 填进去
 *   ③ 什么都不画          —— 连背景位图都没有：完全透明，等于没开效果
 * </pre>
 * ★ ② 是**真实的光学放大**（位图矩阵缩放 + 环形裁剪），不是画的假光斑；
 *   它没有剖面曲线和深度感，观感弱一档，但**不会**出现白色描边或色块。
 * ★ ③ 明确选择「什么都不画」而不是「涂个半透明色」—— 涂色会盖住背景，
 *   与「面板内部要透出真实背景」的整个设计相矛盾。
 */
public final class GlassDrawable extends Drawable {

    private static final String TAG = "QCLGlass";

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
    /** 折射带宽度（dp）。v3.2 起 16dp（参考实现同量级）。 */
    private float bandDp = 16f;
    /** 最大偏移（dp）。v3.2 起 32dp —— 参考实现是带宽的 1.5~2 倍，太弱就看不出折射。 */
    private float strengthDp = 32f;
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

    private RuntimeShader shader;

    // ★★ uniform 缓存：位置每帧都变，但尺寸/圆角/带宽不变。
    //    滚动时几百块面板 × 9 个 uniform = 几千次 JNI 调用，缓存能砍掉大部分。
    private float lastBand = Float.NaN;
    private float lastStrength = Float.NaN;
    private float lastRadius = Float.NaN;
    private float lastDepth = Float.NaN;
    private int lastW = -1, lastH = -1;
    private long lastBgVersion = -1;
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
    private final Path ringPath = new Path();
    private final Path outerPath = new Path();
    private final Path innerPath = new Path();

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

    /** 最大偏移（dp）。越大，边缘折射越明显。 */
    public GlassDrawable strength(float dp) {
        this.strengthDp = dp;
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
                drawRing(canvas, w, h, bg);
            } catch (Throwable ignored) {
            }
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
        }
        if (shader == null) {
            return false;
        }

        // ★★★ 原点必须是**绘制内容的屏幕位置**，不是 View 的位置。
        //   背景 Drawable 的 bounds 会带上 View 的 padding（setBounds 传的是
        //   paddingLeft/Top..），所以「内容左上角」= View 屏幕坐标 + bounds.left/top。
        //   早期版本直接用 View 坐标并在 (0,0) 画，控件有 padding 时
        //   畸变圈会整体偏移 padding 那么多，看起来像「玻璃没对准边缘」。
        Rect bnd = getBounds();
        int ox = 0, oy = 0;
        if (owner != null) {
            try {
                owner.getLocationOnScreen(locBuf);
                ox = locBuf[0] + bnd.left;
                oy = locBuf[1] + bnd.top;
            } catch (Throwable ignored) {
            }
        }

        float d = owner != null ? owner.getResources().getDisplayMetrics().density : 3f;
        float dm = owner != null ? owner.getResources().getDisplayMetrics().widthPixels : 1080f;
        float dh = owner != null ? owner.getResources().getDisplayMetrics().heightPixels : 2340f;
        float r = radius >= 0 ? radius : 14f * d;
        // ★★★ v4.0：参数按 ReGlass 的语义重新映射。
        //   · thickness = 原来的「折射带宽度」= **玻璃厚度**（像素）
        //   · amount    = 外缘最大位移，单位是**归一化 UV**（不是像素！）
        //   ReGlass 的剖面在 depth >= thickness 处把位移归零，所以厚度同时也是
        //   「折射影响范围」—— 一个参数表达一件事，和它的实现一致。
        float thickness = Math.max(bandDp * d, 1f);
        // ★★★ 0.08 是 ReGlass 写死的系数（refrOffset = -normal * edgeFactor * 0.08 * 宽高比）。
        //   它的强度不可调，我们的「折射强度」滑杆按 strengthDp/32dp 线性缩放 ——
        //   默认 50%（strengthDp = 32dp）正好落在 ReGlass 的原值上，
        //   调到 0 就是完全没有折射，调到 100 是它的 1.375 倍。
        float amount = 0.08f * (Math.max(strengthDp, 0f) / 32f);
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
            shader.setFloatUniform("uScreenSize", dm, dh);
            lastBgVersion = bgVer;
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
     * <p>做法：用 {@code Path.op(DIFFERENCE)} 裁出「外圆角矩形 − 内圆角矩形」的一圈，
     * 只在这一圈里画放大后的背景。视觉上接近「边缘被撑开」，无色散但也没有假白边。
     *
     * <p>★ 放大倍率 1.12 比 3.0 的 1.08 略高：因为现在只剩一圈，
     * 放大不够就完全看不出来了。
     */
    private void drawRing(Canvas canvas, int w, int h, Bitmap bg) {
        if (bg.isRecycled()) {
            return;
        }
        Rect bnd = getBounds();
        int ox = 0, oy = 0;
        if (owner != null) {
            try {
                owner.getLocationOnScreen(locBuf);
                ox = locBuf[0] + bnd.left;
                oy = locBuf[1] + bnd.top;
            } catch (Throwable ignored) {
            }
        }
        float d = owner != null ? owner.getResources().getDisplayMetrics().density : 3f;
        float r = radius >= 0 ? radius : 14f * d;
        float band = Math.min(bandDp * d, Math.min(w, h) * 0.45f);

        float sx = bg.getWidth() / (float) Math.max(1, sW);
        float sy = bg.getHeight() / (float) Math.max(1, sH);
        if (magShader == null || magShaderBg != bg || magW != w || magH != h) {
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
        }

        outerPath.reset();
        outerPath.addRoundRect(0, 0, w, h, r, r, Path.Direction.CW);
        innerPath.reset();
        float ir = Math.max(0f, r - band);
        innerPath.addRoundRect(band, band, w - band, h - band, ir, ir, Path.Direction.CW);
        ringPath.reset();
        ringPath.set(outerPath);
        if (ringPath.op(innerPath, Path.Op.DIFFERENCE)) {
            int save = canvas.save();
            canvas.translate(bnd.left, bnd.top);   // ★ 同 drawShader：对齐 bounds 原点
            canvas.clipPath(ringPath);
            fallbackPaint.setShader(magShader);
            canvas.drawRect(0, 0, w, h, fallbackPaint);
            fallbackPaint.setShader(null);
            canvas.restoreToCount(save);
        }
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
