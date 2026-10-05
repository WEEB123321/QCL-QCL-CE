package com.qcl.launcher.launcher.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;

/**
 * ★★★ 社区版新增：**真·液态玻璃**（AGSL RuntimeShader）。
 *
 * <h3>为什么之前那版不算液态玻璃</h3>
 * 上一版只是「半透明白 + 大圆角」—— 那是**磨砂玻璃**，没有折射、没有色散，
 * 边缘不会把背景扭一下，一眼就能看出是「一块半透明的板子」。
 * 真正的液态玻璃必须**逐像素采样背景并做折射位移**，这只能写着色器。
 *
 * <h3>移植来源</h3>
 * 光学部分**逐行照搬**用户自己那个 OCL 项目的
 * {@code ui/glass/GlassShaders.kt}（它又是从参考实现 ReGlass 的
 * {@code liquid_glass_gui.fsh} 移植的）。核心公式：
 * <pre>
 *   xR      = 1 - 内部深度 / 厚度
 *   thetaI  = asin(xR²)                 // 入射角
 *   thetaT  = asin(sin(thetaI) / n)     // Snell 定律
 *   edge    = -tan(thetaT - thetaI)     // 边缘偏折量
 *   offset  = -normal * edge * 0.08
 * </pre>
 * 色散是 R/G/B 用三个不同折射率（0.985 / 1.000 / 1.015）分开采样 ——
 * 所以玻璃边缘会出现**真实的彩虹分离**，而不是叠一层彩色描边。
 *
 * <h3>与 OCL 的差异（都是刻意的）</h3>
 * <ol>
 *   <li><b>Compose → View</b>：OCL 用 {@code Modifier.drawWithContent} + 快照；
 *       这里做成 {@link LiquidGlassDrawable}（一个普通 Drawable），
 *       因为 QCL 是 View 架构。</li>
 *   <li><b>背景不用截屏</b>：QCL 的背景就是轮播的那张位图，直接拿来当输入着色器 ——
 *       省掉「截屏 → 降采样 → 包 BitmapShader」整条链路，
 *       也顺带避开了「截屏时把玻璃自己截进去」的递归问题。</li>
 *   <li><b>砍掉 hover / focus 两个 uniform 分支</b>：那是桌面端鼠标悬停和键盘焦点用的，
 *       手机上没有 hover。保留它们只是让着色器更长、编译更容易失败。</li>
 * </ol>
 *
 * <h3>★ 必须降级</h3>
 * {@code RuntimeShader} 要 **API 33+**；而且「类存在」不等于「驱动能编译」。
 * 所以这里除了版本判断，还会**真的编译一次**并捕获异常 —— 失败就整体走
 * 半透明分层近似，并且**在日志里明确写出来**，否则用户只会看到「效果没了」。
 */
public final class LiquidGlass {

    private static final String TAG = "QCLGlass";

    /** 本机是否真的能用 AGSL。启动时算一次。 */
    private static Boolean sUsable = null;
    /** 记录「走的哪条路径」，供诊断页展示。 */
    private static String sPath = "未检测";

    private LiquidGlass() {
    }

    public static String pathDescription() {
        ensureProbed();
        return sPath;
    }

    public static boolean usable() {
        ensureProbed();
        return Boolean.TRUE.equals(sUsable);
    }

    private static synchronized void ensureProbed() {
        if (sUsable != null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            sUsable = false;
            sPath = "分层近似（系统低于 Android 13，无 RuntimeShader）";
            return;
        }
        try {
            RuntimeShader sh = new RuntimeShader(SRC);
            // ★ 真的编译一次才算数 —— 部分设备 RuntimeShader 类在、驱动却编译不过
            sh.setFloatUniform("uRadius", 12f);
            sUsable = true;
            sPath = "AGSL 着色器（真折射 + 色散）";
            Log.i(TAG, "液态玻璃：AGSL 路径可用");
        } catch (Throwable t) {
            sUsable = false;
            sPath = "分层近似（AGSL 编译失败：" + t.getClass().getSimpleName() + "）";
            Log.w(TAG, "液态玻璃：AGSL 编译失败，已降级", t);
        }
    }

    /** 新建一个面板着色器；不可用或失败返回 {@code null}（调用方必须降级）。 */
    public static RuntimeShader newPanelShader() {
        ensureProbed();
        if (!Boolean.TRUE.equals(sUsable)) {
            return null;
        }
        try {
            return new RuntimeShader(SRC);
        } catch (Throwable t) {
            Log.w(TAG, "新建面板着色器失败，本块走降级", t);
            return null;
        }
    }

    /**
     * 把一个 View 的背景换成液态玻璃。返回 true = 走的是**真着色器**路径。
     *
     * <p>★ 参数用 dp，内部按屏幕密度换算成 px —— 着色器里所有距离都以像素为单位，
     * 传 dp 进去会让不同密度的机器观感差一大截。
     */
    public static boolean applyTo(android.view.View v, float radiusDp, float tintAlpha, float thicknessDp) {
        if (v == null) {
            return false;
        }
        try {
            float d = v.getResources().getDisplayMetrics().density;
            LiquidGlassDrawable bg = new LiquidGlassDrawable(v)
                    .radius(radiusDp * d)
                    .tint(android.graphics.Color.WHITE, tintAlpha)
                    // ★★★ 厚度是「折射带有多宽」——原值 22dp 只在紧贴边缘的一圈起作用，
                    //   在手机上根本看不出来。提到 3 倍后整块玻璃都在偏折，效果才明确。
                    .thickness(thicknessDp * d * 3f)
                    // ★★★ 色散 7 → 18：边缘的彩虹分离要看得见才算「液态玻璃」，
                    //   原来的量级只在很厚的玻璃上才勉强可见。
                    .dispersion(18f)
                    // ★ 折射倍率 0.08 → 0.22：边缘畸变要一眼能看出来
                    .refraction(0.22f)
                    .blur(10f)
                    // ★★★ 菲涅尔 / 高光的范围从 30 提到 70 —— 边缘那圈亮光要够宽才像玻璃，
                    //   30 只在最边上几个像素，视觉上等于没有。
                    .fresnel(70f, 0.25f, 0.55f)
                    .glare(70f, 0.25f)
                    .shadow(30f, 0.25f);
            v.setBackground(bg);
            return usable();
        } catch (Throwable t) {
            Log.w(TAG, "给 View 应用液态玻璃失败（保留原背景）", t);
            return false;
        }
    }

    /**
     * 把背景位图包成输入着色器。
     * ★ 必须 Clamp —— 折射会把采样坐标推到面板外，不 Clamp 会出现黑边或重复图案。
     */
    public static BitmapShader backdropShader(Bitmap bmp) {
        try {
            return new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 面板着色器源码（AGSL）。
     *
     * <p>★ 与 GLSL 的关键差异（写错整个画面会错位）：
     * AGSL 原点在**左上角、y 轴向下**（GLSL 是左下角），所以**不要翻转 y**；
     * 且 {@code fragCoord} 是**画布局部坐标**，与 {@code drawRect(left, top, ...)} 同一套。
     */
    static final String SRC = ""
            + "uniform shader uBg;\n"
            + "uniform vec2  uBgTexSize;\n"
            + "uniform vec2  uPanelSize;\n"
            + "uniform vec2  uPanelOrigin;\n"
            + "uniform vec2  uScreenSize;\n"
            + "uniform float uRadius;\n"
            + "uniform float uThickness;\n"
            + "uniform float uRefFactor;\n"
            + "uniform float uRefDisp;\n"
            + "// 折射位移的整体倍率 —— 越大，边缘的「透镜畸变」越明显\n"
            + "uniform float uRefrScale;\n"
            + "uniform float uBlur;\n"
            + "uniform float uFresRange;\n"
            + "uniform float uFresHard;\n"
            + "uniform float uFresFac;\n"
            + "uniform float uGlareRange;\n"
            + "uniform float uGlareHard;\n"
            + "uniform vec4  uTint;\n"
            + "uniform float uShadowExpand;\n"
            + "uniform float uShadowFactor;\n"
            + "uniform vec2  uShadowOffset;\n"
            + "uniform vec4  uShadowColor;\n"
            + "\n"
            + "vec3 oclBg(vec2 uv) {\n"
            + "    return uBg.eval(uv * uBgTexSize).rgb;\n"
            + "}\n"
            + "\n"
            + "// 磨砂模糊：十字核。\n"
            + "// AGSL 的 for 循环必须能在编译期展开，这里手写展开，不依赖编译器。\n"
            + "vec3 oclBgBlur(vec2 uv) {\n"
            + "    if (uBlur <= 0.001) { return oclBg(uv); }\n"
            + "    vec2 t = uBlur / max(uBgTexSize, vec2(1.0));\n"
            + "    vec3 c = oclBg(uv) * 0.2;\n"
            + "    c += oclBg(uv + vec2(t.x, 0.0)) * 0.2;\n"
            + "    c += oclBg(uv - vec2(t.x, 0.0)) * 0.2;\n"
            + "    c += oclBg(uv + vec2(0.0, t.y)) * 0.2;\n"
            + "    c += oclBg(uv - vec2(0.0, t.y)) * 0.2;\n"
            + "    return c;\n"
            + "}\n"
            + "\n"
            + "// 圆角矩形 SDF，返回 (距离, 法线xy)。距离 < 0 在内部。\n"
            + "vec3 oclSdgBox(vec2 p, vec2 b, float r) {\n"
            + "    vec2 w = abs(p) - (b - r);\n"
            + "    vec2 s = vec2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);\n"
            + "    float g = max(w.x, w.y);\n"
            + "    vec2 q = max(w, vec2(0.0));\n"
            + "    float l = length(q);\n"
            + "    float dist = (g > 0.0) ? l - r : g - r;\n"
            + "    vec2 n = (g > 0.0) ? (q / max(l, 1e-6))\n"
            + "                       : ((w.x > w.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0));\n"
            + "    return vec3(dist, s * n);\n"
            + "}\n"
            + "\n"
            + "half4 main(float2 fragCoord) {\n"
            + "    vec2 H = max(uScreenSize, vec2(1.0));\n"
            + "    vec2 p = (fragCoord - 0.5 * uPanelSize) / H.y;\n"
            + "    vec2 b = 0.5 * uPanelSize / H.y;\n"
            + "    float r = uRadius / H.y;\n"
            + "\n"
            + "    vec3 g = oclSdgBox(p, b, r);\n"
            + "    float merged = g.x;\n"
            + "    vec2 normal = g.yz;\n"
            + "    float nlen = length(normal);\n"
            + "    if (nlen > 1e-6) { normal = normal / nlen; }\n"
            + "\n"
            + "    vec2 screenPix = uPanelOrigin + fragCoord;\n"
            + "    vec2 uv = screenPix / H;\n"
            + "    float mergedPx = merged * H.y;\n"
            + "\n"
            + "    // ---- 投影 ----\n"
            + "    vec2 pShadow = (fragCoord - 0.5 * uPanelSize + uShadowOffset) / H.y;\n"
            + "    vec3 gs = oclSdgBox(pShadow, b, r);\n"
            + "    float sh = exp(-abs(gs.x) * H.y / max(uShadowExpand, 1e-4)) * 0.6 * uShadowFactor;\n"
            + "    float sa = clamp(uShadowColor.a * sh, 0.0, 1.0);\n"
            + "\n"
            + "    // 投影范围之外直接全透明 —— 折射那一大段完全不用跑。\n"
            + "    if (mergedPx > max(uShadowExpand, 1e-4) * 6.0) { return half4(0.0); }\n"
            + "\n"
            + "    if (merged >= 0.0) {\n"
            + "        // 面板外：只输出投影（预乘 alpha），让真正的背景层透出来。\n"
            + "        return half4(half3(uShadowColor.rgb * sa), half(sa));\n"
            + "    }\n"
            + "\n"
            + "    // ---- 面板内：真折射 ----\n"
            + "    float nmerged = -mergedPx;\n"
            + "    float xR = max(1.0 - nmerged / max(uThickness, 1e-6), 0.0);\n"
            + "    float thetaI = asin(clamp(pow(xR, 2.0), 0.0, 1.0));\n"
            + "    float thetaT = asin(clamp(sin(thetaI) / max(uRefFactor, 1e-6), -1.0, 1.0));\n"
            + "    float edgeFactor = -tan(thetaT - thetaI);\n"
            + "    if (nmerged >= uThickness) { edgeFactor = 0.0; }\n"
            + "    vec2 refrOffset = -normal * edgeFactor * uRefrScale * vec2(H.y / H.x, 1.0);\n"
            + "\n"
            + "    // 色散：三个折射率分开采样 → 边缘真实的彩虹分离\n"
            + "    vec3 disp;\n"
            + "    disp.r = oclBgBlur(uv + refrOffset * (1.0 - (0.985 - 1.0) * uRefDisp)).r;\n"
            + "    disp.g = oclBgBlur(uv + refrOffset * (1.0 - (1.000 - 1.0) * uRefDisp)).g;\n"
            + "    disp.b = oclBgBlur(uv + refrOffset * (1.0 - (1.015 - 1.0) * uRefDisp)).b;\n"
            + "    vec3 outColor = mix(disp, uTint.rgb, uTint.a * 0.8);\n"
            + "\n"
            + "    // ---- 菲涅尔：边缘提亮 ----\n"
            + "    float fresnel = clamp(pow(1.0 + mergedPx / 1500.0\n"
            + "            * pow(500.0 / max(uFresRange, 1e-6), 2.0) + uFresHard, 5.0), 0.0, 1.0);\n"
            + "    vec3 fresTint = mix(vec3(1.0), uTint.rgb, uTint.a * 0.5);\n"
            + "    outColor = mix(outColor, fresTint, fresnel * uFresFac * 0.7 * nlen);\n"
            + "\n"
            + "    // ---- 高光 ----\n"
            + "    float glare = clamp(pow(1.0 + mergedPx / 1500.0\n"
            + "            * pow(500.0 / max(uGlareRange, 1e-6), 2.0) + uGlareHard, 5.0), 0.0, 1.0);\n"
            + "    vec3 glareMix = mix(oclBg(uv), uTint.rgb, uTint.a * 0.5);\n"
            + "    outColor = mix(outColor, glareMix, 0.25 * glare * nlen);\n"
            + "\n"
            + "    // 面板边界处与投影平滑衔接\n"
            + "    vec3 shadowed = mix(outColor, uShadowColor.rgb, sa);\n"
            + "    return half4(half3(shadowed), 1.0);\n"
            + "}\n";
}
