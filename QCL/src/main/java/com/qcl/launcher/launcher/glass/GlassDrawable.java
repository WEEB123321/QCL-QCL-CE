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
 * ★★★ 液态玻璃 · **边缘折射**面板背景（v3.2）。
 *
 * <p>把它设成任意 View 的背景，那个 View 的**边缘一圈**就会折射背后的背景；
 * 面板内部完全透明，露出真实背景。
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
        try {
            if (!drawShader(canvas, w, h, bg)) {
                drawRing(canvas, w, h, bg);
            }
        } catch (Throwable t) {
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
        float band = bandDp * d;
        // ★ v3.2：传负值（与参考实现一致）。法线朝外，负偏移 = 向内采样 = 放大镜观感。
        float strength = -strengthDp * d;
        float depth = depthEffect ? 1f : 0f;

        // ★ 只设变化的那几个 —— 见字段注释
        long bgVer = GlassBackdrop.version();
        if (lastBgVersion != bgVer) {
            shader.setInputShader("uBg", bgShader);
            shader.setFloatUniform("uBgSize", bg.getWidth(), bg.getHeight());
            shader.setFloatUniform("uScreenSize", dm, dh);
            lastBgVersion = bgVer;
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
        if (lastBand != band) {
            shader.setFloatUniform("uRefractionHeight", band);
            lastBand = band;
        }
        if (lastStrength != strength) {
            shader.setFloatUniform("uRefractionAmount", strength);
            lastStrength = strength;
        }
        if (lastDepth != depth) {
            shader.setFloatUniform("uDepthEffect", depth);
            lastDepth = depth;
        }
        // 位置每帧都可能变（滚动 / 动画），必须无条件更新
        shader.setFloatUniform("uPanelOrigin", ox, oy);

        paint.setShader(shader);
        // ★ 只画面板本体，不外扩 —— 没有投影，外扩纯属浪费填充率。
        // ★★ 必须先 translate 到 bounds 左上角：着色器里的 fragCoord 是画布局部坐标，
        //    在 (0,0) 画才能保证 fragCoord 落在 [0,w]×[0,h]，SDF 的圆心才对得上。
        int save = canvas.save();
        canvas.translate(bnd.left, bnd.top);
        canvas.drawRect(0, 0, w, h, paint);
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
