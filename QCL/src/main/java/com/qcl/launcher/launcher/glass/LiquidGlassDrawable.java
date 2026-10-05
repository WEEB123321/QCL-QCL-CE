package com.qcl.launcher.launcher.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * ★★★ 社区版新增：把 AGSL 液态玻璃着色器包成一个**普通 Drawable**，好用在 View 架构里。
 *
 * <p>用法：把它设成某个 View 的背景（{@code view.setBackground(new LiquidGlassDrawable(view))}）。
 *
 * <h3>坐标系</h3>
 * {@code Drawable.draw(canvas)} 拿到的 canvas **已经平移到本 View 的左上角**，
 * 所以 {@code fragCoord ∈ [0,w] × [0,h]} 正好是面板本体 ——
 * 这与着色器「{@code fragCoord} 是画布局部坐标」的约定天然吻合，不需要额外换算。
 * 唯一要从外面补进来的是**面板在屏幕上的位置**（{@code uPanelOrigin}），
 * 用来把背景采样坐标对齐到真正显示的那张背景图上。
 *
 * <h3>★ 降级</h3>
 * 着色器不可用（系统 &lt; Android 13 / 驱动编译失败）时，退化成
 * 「半透明白 + 一圈更亮的边」—— <b>没有折射也没有色散</b>，只是不难看而已。
 * 走哪条路径写在 {@link LiquidGlass#pathDescription()} 里，可在诊断页看到。
 */
public final class LiquidGlassDrawable extends Drawable {

    /** 全局背景来源：由主界面在轮播切图时更新。 */
    private static Bitmap sBackdrop;
    private static int sScreenW = 1080;
    private static int sScreenH = 2340;
    /** 背景图在屏幕上的绘制矩形（因为背景是 fitXY/centerCrop，和屏幕不一定等大）。 */
    private static final Rect sBackdropRect = new Rect();

    public static void setBackdrop(Bitmap bmp, int screenW, int screenH, Rect drawRect) {
        sBackdrop = bmp;
        sScreenW = Math.max(1, screenW);
        sScreenH = Math.max(1, screenH);
        if (drawRect != null) {
            sBackdropRect.set(drawRect);
        } else {
            sBackdropRect.set(0, 0, sScreenW, sScreenH);
        }
    }

    public static Bitmap backdrop() {
        return sBackdrop;
    }

    // ------------------------------------------------------------------ 实例

    private final View owner;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RuntimeShader shader;
    private BitmapShader bgShader;
    private Bitmap lastBackdrop;
    /**
     * 复用的位置数组。
     * ★ 不要每次 draw 里 new int[2] —— draw 是每帧调的，每次分配一个小数组
     * 会持续制造垃圾，滚动/轮播切换时能看到掉帧。
     */
    private final int[] locBuf = new int[2];

    // ★ 降级路径「放大背景」的缓存。
    //   draw() 是每帧调的 —— 在里面 new BitmapShader / Matrix / RectF 会持续制造垃圾，
    //   滚动和轮播切换时能看到掉帧。这里按「背景位图 + 面板位置尺寸」缓存，
    //   只有真正变了才重建。
    private BitmapShader magShader;
    private Bitmap magShaderBg;
    private int magShaderW = -1, magShaderH = -1, magShaderOx = Integer.MIN_VALUE, magShaderOy = Integer.MIN_VALUE;

    private float radius = 16f;
    private float thickness = 20f;
    private float refFactor = 1.4f;
    private float refDisp = 7f;
    /** 折射位移整体倍率。0.08 是参考实现的原值，太小看不出来；这里默认 0.22。 */
    private float refrScale = 0.22f;
    private float blur = 8f;
    private float fresRange = 30f;
    private float fresHard = 0.2f;
    private float fresFac = 0.2f;
    private float glareRange = 30f;
    private float glareHard = 0.2f;
    private int tintColor = Color.WHITE;
    private float tintAlpha = 0.10f;
    private float shadowExpand = 30f;
    private float shadowFactor = 0.25f;
    private float shadowOffX = 0f;
    private float shadowOffY = 2f;
    private int shadowColor = Color.BLACK;
    private float shadowAlpha = 0.35f;

    public LiquidGlassDrawable(View owner) {
        this.owner = owner;
        this.fallbackPaint.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ 参数

    public LiquidGlassDrawable radius(float dp) {
        this.radius = dp;
        return this;
    }

    public LiquidGlassDrawable tint(int color, float alpha) {
        this.tintColor = color;
        this.tintAlpha = alpha;
        return this;
    }

    /** 厚度越大边缘偏折范围越宽、观感越「厚」。20 是参考实现默认值。 */
    public LiquidGlassDrawable thickness(float v) {
        this.thickness = v;
        return this;
    }

    /** 色散强度。越大边缘彩虹分离越明显。7 是默认值。 */
    public LiquidGlassDrawable dispersion(float v) {
        this.refDisp = v;
        return this;
    }

    /** 折射位移倍率。越大边缘畸变越强（「透镜感」越明显）。 */
    public LiquidGlassDrawable refraction(float v) {
        this.refrScale = v;
        return this;
    }

    public LiquidGlassDrawable blur(float v) {
        this.blur = v;
        return this;
    }

    public LiquidGlassDrawable shadow(float expand, float factor) {
        this.shadowExpand = expand;
        this.shadowFactor = factor;
        return this;
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        int w = b.width();
        int h = b.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        Bitmap bg = sBackdrop;
        if (bg == null || bg.isRecycled()) {
            drawFallback(canvas, w, h);
            return;
        }
        try {
            if (shader == null) {
                shader = LiquidGlass.newPanelShader();
            }
            if (shader == null) {
                drawFallback(canvas, w, h);
                return;
            }
            if (bgShader == null || lastBackdrop != bg) {
                bgShader = LiquidGlass.backdropShader(bg);
                lastBackdrop = bg;
            }
            if (bgShader == null) {
                drawFallback(canvas, w, h);
                return;
            }

            // 面板在屏幕上的位置（用来对齐背景采样）
            int ox = 0, oy = 0;
            if (owner != null) {
                try {
                    owner.getLocationOnScreen(locBuf);   // ★ 复用，不每次 new
                    ox = locBuf[0];
                    oy = locBuf[1];
                } catch (Throwable ignored) {
                }
            }

            shader.setInputShader("uBg", bgShader);
            shader.setFloatUniform("uBgTexSize", bg.getWidth(), bg.getHeight());
            shader.setFloatUniform("uPanelSize", w, h);
            shader.setFloatUniform("uPanelOrigin", ox, oy);
            shader.setFloatUniform("uScreenSize", sScreenW, sScreenH);
            shader.setFloatUniform("uRadius", radius);
            shader.setFloatUniform("uThickness", thickness);
            shader.setFloatUniform("uRefFactor", refFactor);
            shader.setFloatUniform("uRefDisp", refDisp);
            shader.setFloatUniform("uRefrScale", refrScale);
            // 模糊半径按「背景纹理像素」换算：面板像素 → 背景纹理像素
            shader.setFloatUniform("uBlur", blur * bg.getWidth() / Math.max(1f, sScreenW));
            shader.setFloatUniform("uFresRange", fresRange);
            shader.setFloatUniform("uFresHard", fresHard);
            shader.setFloatUniform("uFresFac", fresFac);
            shader.setFloatUniform("uGlareRange", glareRange);
            shader.setFloatUniform("uGlareHard", glareHard);
            shader.setColorUniform("uTint", tintColor);
            shader.setFloatUniform("uShadowExpand", shadowExpand);
            shader.setFloatUniform("uShadowFactor", shadowFactor);
            shader.setFloatUniform("uShadowOffset", shadowOffX, shadowOffY);
            shader.setColorUniform("uShadowColor", shadowColor);
            // 上面 setColorUniform 传的是 int（不透明），alpha 单独用不了；
            // 这里直接把 alpha 打进颜色里，避免多一个 uniform。
            shader.setColorUniform("uShadowColor",
                    Color.argb((int) (255 * shadowAlpha), Color.red(shadowColor),
                            Color.green(shadowColor), Color.blue(shadowColor)));
            shader.setColorUniform("uTint",
                    Color.argb((int) (255 * tintAlpha), Color.red(tintColor),
                            Color.green(tintColor), Color.blue(tintColor)));

            paint.setShader(shader);
            // ★ 向外扩 drawPadding，让投影有地方画；内容本体仍落在 [0,w]×[0,h]
            float pad = shadowExpand * 2f + Math.max(Math.abs(shadowOffX), Math.abs(shadowOffY)) + 8f;
            canvas.drawRect(-pad, -pad, w + pad, h + pad, paint);
            return;
        } catch (Throwable t) {
            // 任何一步失败都退回降级，绝不让界面崩
            drawFallback(canvas, w, h);
        }
    }

    /**
     * ★★★ 降级路径：**做成「透镜」的样子**，而不是平涂一层半透明白。
     *
     * <p>用户的原话是「液态玻璃实在不行就你自己做（透镜效果就好）」——
     * 所以降级也要能一眼看出「这是一块玻璃」，而不是「一块灰板子」。
     *
     * <p>用三层堆出透镜观感（**没有折射、没有色散**，是近似，不冒充等价）：
     * <ol>
     *   <li><b>纵向渐变底</b>：上缘略亮、中部最亮、下缘回落 —— 模拟光线穿过玻璃的厚度变化；</li>
     *   <li><b>亮边</b>：1.5dp 白色描边，模拟玻璃边缘的反射（这是「玻璃感」最关键的一笔）；</li>
     *   <li><b>顶部内高光</b>：一条更亮的细弧，模拟上边缘的镜面反射。</li>
     * </ol>
     */
    /**
     * ★★★ 降级路径的「真透镜」：把背景里对应这一块**放大 {@link #FALLBACK_MAGNIFY} 倍**画进来。
     *
     * <p><b>为什么必须做这个</b>：AGSL 那条路依赖设备驱动 —— 有些机器 `RuntimeShader` 类在、
     * 驱动却编译不过，只能走降级。而原来的降级只有「渐变底 + 亮边」，**没有任何畸变**，
     * 用户一眼就看出「这不是透镜」（他的原话：有反光，但边缘没有畸变）。
     *
     * <p>这里用**位图矩阵缩放**做出真实的光学放大 —— 完全不依赖 AGSL，
     * 所以**任何机器上都有透镜效果**。
     *
     * <p>做法：面板屏幕位置 → 换算成背景位图的对应区域 → 把该区域向中心收缩 1/倍率
     * （等效于放大）→ {@code Matrix.setRectToRect} 映射到面板矩形 → 圆角裁剪后画上去。
     *
     * @return true = 画成功；false = 拿不到背景位图，调用方退到纯色
     */
    private boolean drawMagnifiedBackdrop(Canvas canvas, int w, int h) {
        Bitmap bg = sBackdrop;
        if (bg == null || bg.isRecycled() || w <= 0 || h <= 0) {
            return false;
        }
        try {
            int ox = 0, oy = 0;
            if (owner != null) {
                owner.getLocationOnScreen(locBuf);
                ox = locBuf[0];
                oy = locBuf[1];
            }
            // 屏幕坐标 -> 位图坐标
            float kx = bg.getWidth() / (float) Math.max(1, sScreenW);
            float ky = bg.getHeight() / (float) Math.max(1, sScreenH);

            float cx = (ox + w * 0.5f) * kx;
            float cy = (oy + h * 0.5f) * ky;
            // 放大 N 倍 = 源区域缩到 1/N
            float halfW = w * kx / (2f * FALLBACK_MAGNIFY);
            float halfH = h * ky / (2f * FALLBACK_MAGNIFY);

            // ★ 缓存命中就直接用，别每帧重建（见字段注释）
            if (magShader == null || magShaderBg != bg
                    || magShaderW != w || magShaderH != h
                    || magShaderOx != ox || magShaderOy != oy) {
                android.graphics.RectF src = new android.graphics.RectF(
                        cx - halfW, cy - halfH, cx + halfW, cy + halfH);
                android.graphics.RectF dst = new android.graphics.RectF(0f, 0f, w, h);

                android.graphics.BitmapShader bs = new android.graphics.BitmapShader(
                        bg, android.graphics.Shader.TileMode.CLAMP,
                        android.graphics.Shader.TileMode.CLAMP);
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.setRectToRect(src, dst, android.graphics.Matrix.ScaleToFit.FILL);
                bs.setLocalMatrix(m);

                magShader = bs;
                magShaderBg = bg;
                magShaderW = w;
                magShaderH = h;
                magShaderOx = ox;
                magShaderOy = oy;
            }

            fallbackPaint.setStyle(Paint.Style.FILL);
            fallbackPaint.setShader(magShader);
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, fallbackPaint);
            fallbackPaint.setShader(null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 降级路径的放大倍率。1.12 ≈ 一眼能看出「透过玻璃看东西变大了」。 */
    private static final float FALLBACK_MAGNIFY = 1.12f;

    private void drawFallback(Canvas canvas, int w, int h) {
        try {
            float a = Math.max(0.10f, tintAlpha);

            // ⓪ ★★★ 真透镜：把背景里对应这一块**放大 1.12 倍**画进去。
            //   这是**真实的光学放大**（不是模拟）—— 用位图矩阵缩放就能做，
            //   完全不依赖 AGSL，所以「着色器跑不起来」的机器上也有透镜效果。
            //   用户的说法是「实在不行就你自己做（透镜效果就好）」，这就是那一步。
            if (!drawMagnifiedBackdrop(canvas, w, h)) {
                // 拿不到背景位图才退到纯色
                fallbackPaint.setShader(null);
                fallbackPaint.setColor(Color.argb((int) (255 * (a + 0.18f)), 255, 255, 255));
                canvas.drawRoundRect(0, 0, w, h, radius, radius, fallbackPaint);
            }

            // ① 纵向渐变底（压在放大后的背景之上，做出「玻璃有厚度」的层次）
            fallbackPaint.setStyle(Paint.Style.FILL);
            fallbackPaint.setShader(new android.graphics.LinearGradient(
                    0f, 0f, 0f, h,
                    new int[]{
                            Color.argb((int) (255 * (a * 0.55f)), 255, 255, 255),
                            Color.argb((int) (255 * (a * 1.00f)), 255, 255, 255),
                            Color.argb((int) (255 * (a * 0.30f)), 255, 255, 255)
                    },
                    new float[]{0f, 0.45f, 1f},
                    android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0, 0, w, h, radius, radius, fallbackPaint);
            fallbackPaint.setShader(null);

            // ② 亮边 —— 玻璃感的关键
            fallbackPaint.setStyle(Paint.Style.STROKE);
            fallbackPaint.setStrokeWidth(Math.max(1.5f, radius * 0.10f));
            fallbackPaint.setColor(Color.argb(150, 255, 255, 255));
            float inset = fallbackPaint.getStrokeWidth() / 2f;
            canvas.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, fallbackPaint);

            // ③ 顶部内高光（一条细弧）
            fallbackPaint.setStrokeWidth(Math.max(1f, radius * 0.06f));
            fallbackPaint.setColor(Color.argb(110, 255, 255, 255));
            float y = h * 0.12f;
            canvas.drawLine(radius, y, w - radius, y, fallbackPaint);
        } catch (Throwable ignored) {
        } finally {
            fallbackPaint.setStyle(Paint.Style.FILL);
        }
    }

    /** 菲涅尔（边缘提亮）参数。范围越大，边缘那圈光带越宽。 */
    public LiquidGlassDrawable fresnel(float range, float hardness, float factor) {
        this.fresRange = range;
        this.fresHard = hardness;
        this.fresFac = factor;
        return this;
    }

    /** 高光参数。 */
    public LiquidGlassDrawable glare(float range, float hardness) {
        this.glareRange = range;
        this.glareHard = hardness;
        return this;
    }

    @Override
    public void setAlpha(int alpha) {
        fallbackPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fallbackPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
