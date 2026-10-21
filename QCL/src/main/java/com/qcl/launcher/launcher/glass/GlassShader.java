package com.qcl.launcher.launcher.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * ★★★ 液态玻璃 · **边缘折射**着色器（v3.2）。
 *
 * <h3>v3.2 的折射数学来自 kyant0 的 Backdrop 库（Apache-2.0）</h3>
 * 参考实现：{@code D:\A\AndroidLiquidGlass-2.0.1\AndroidLiquidGlass-2.0.1}
 * （{@code backdrop/src/commonMain/.../internal/Shaders.kt} 的
 * {@code RoundedRectRefractionShaderString}）。
 *
 * <p><b>为什么不能直接依赖那个库</b>：它是 Compose Multiplatform 工程
 * （{@code io.github.kyant0:backdrop}），依赖 Compose Runtime / Skiko；
 * QCL 是传统 View 体系，没有任何 Compose。引入 Compose 只为一个背景效果，
 * 会让包体、启动时间和构建复杂度都上一个台阶，收益不成比例。
 * ⇒ 只移植**着色器数学**（纯 AGSL 字符串，无依赖），适配成 View 背景 Drawable 能用的形式。
 *
 * <h3>v3.1 → v3.2 到底改了什么（都是「看起来假」的根因）</h3>
 * <table border="1">
 *   <tr><th></th><th>v3.1（我自己写的）</th><th>v3.2（照 Backdrop 移植）</th></tr>
 *   <tr><td>剖面曲线</td><td>{@code k = t²} 二次曲线</td>
 *       <td>{@code circleMap(x) = 1 - sqrt(1 - x²)} <b>四分之一圆</b></td></tr>
 *   <tr><td>法线</td><td>用面板自身圆角算</td>
 *       <td>用 {@code radius × 1.5} 的<b>放大圆角</b>算（转角处法线更平滑，不出折线）</td></tr>
 *   <tr><td>深度感</td><td>无</td><td>{@code depthEffect}：法线叠加<b>指向圆心的径向分量</b></td></tr>
 *   <tr><td>参数</td><td>band 14dp / 偏移 9dp</td><td>高度 16dp / 偏移 <b>32dp</b>（参考实现是高度的 1.5~2 倍）</td></tr>
 * </table>
 *
 * <p>★ 二次曲线 vs 四分之一圆：二次曲线在边缘处斜率有限，看起来像「渐变的模糊」；
 * 四分之一圆在边缘处斜率趋于无穷 —— 这正是真实玻璃倒角的样子，边缘会有一道
 * **锐利的光学压缩带**，这是「像玻璃」和「像贴纸」的分界线。
 *
 * <h3>★ 与参考实现的一处刻意不同</h3>
 * 参考实现在面板内部返回 {@code content.eval(coord)}（即未折射的背景本身，
 * 因为它的 content 就是画在下面的背景层，所以视觉上等于透明）。
 * 本实现直接 {@code return half4(0.0)} —— 用户明确要求「只要一层畸变圈」，
 * 面板内部必须透出**真实背景**而不是再采样一份副本：
 * <ul>
 *   <li>省掉内部区域的采样（填充率只花在边缘一圈）；</li>
 *   <li>彻底杜绝「图片像粘在板块上滑动」—— 采样副本一旦跟随面板坐标就会粘。</li>
 * </ul>
 *
 * <h3>★ 未移植的部分</h3>
 * 参考实现还有一个带 RGB 色散的版本（{@code RoundedRectRefractionWithDispersionShaderString}），
 * 每像素采样 <b>7 次</b>。用户明确说「不要边边的白色」且抱怨过性能，
 * 色散在深色背景上会呈现彩色描边，正是他不想要的那种「花边」⇒ 不移植。
 *
 * <h3>AGSL 要点</h3>
 * <ul>
 *   <li>原点在**左上角、y 轴向下**，与 GLSL 相反，**不要翻转 y**。</li>
 *   <li>{@code fragCoord} 是画布局部坐标，必须先把 canvas translate 到面板原点再画，
 *       否则 SDF 的圆心对不上（v3.1 踩过：控件有 padding 时圈会整体画歪）。</li>
 *   <li>没有循环（AGSL 要求循环可编译期展开）。</li>
 * </ul>
 */
public final class GlassShader {

    private static final String TAG = "QCLGlass";

    private static Boolean sUsable = null;
    private static String sPath = "未检测";

    private GlassShader() {
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
            sPath = "透镜近似（系统低于 Android 13，无 RuntimeShader）";
            return;
        }
        try {
            RuntimeShader sh = new RuntimeShader(SRC);
            // ★ 真的设一次 uniform 才算数 —— 部分设备类在、驱动却编译不过
            sh.setFloatUniform("uRadius", 12f);
            sUsable = true;
            sPath = "AGSL 边缘折射（四分之一圆剖面 + 深度感）";
            Log.i(TAG, "玻璃着色器可用（v3.2 边缘折射）");
        } catch (Throwable t) {
            sUsable = false;
            sPath = "透镜近似（AGSL 编译失败：" + t.getClass().getSimpleName() + "）";
            Log.w(TAG, "玻璃着色器编译失败，已降级", t);
        }
    }

    /** 新建一个着色器实例；不可用返回 null。 */
    public static RuntimeShader newInstance() {
        ensureProbed();
        if (!Boolean.TRUE.equals(sUsable)) {
            return null;
        }
        try {
            return new RuntimeShader(SRC);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 全局共享的 BitmapShader（所有面板复用同一个）。
     *
     * <p>★ 为什么不每块面板各建一个：BitmapShader 会持有位图引用，
     * 几百块面板 = 几百个对象 + 几百次本地矩阵计算。全局一份，
     * AGSL 的 shader 输入是**整张图**，坐标由 uniform 控制，连矩阵都不用换。
     */
    private static WeakReference<Bitmap> sShaderBmp = new WeakReference<>(null);
    private static BitmapShader sBgShader;

    public static BitmapShader sharedBackdropShader() {
        Bitmap b = GlassBackdrop.backdrop();
        if (b == null || b.isRecycled()) {
            return null;
        }
        if (sBgShader == null || sShaderBmp.get() != b) {
            try {
                sBgShader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                sShaderBmp = new WeakReference<>(b);
            } catch (Throwable t) {
                return null;
            }
        }
        return sBgShader;
    }

    /** 换背景时清掉缓存的 BitmapShader（否则还绑在旧图上）。 */
    static void invalidateBackdropShader() {
        sBgShader = null;
        sShaderBmp = new WeakReference<>(null);
    }

    /**
     * 边缘折射着色器源码（AGSL，像素空间）。
     *
     * <h3>结构</h3>
     * <pre>
     *   SDF 求到圆角矩形边缘的有符号距离 sd（内部为负）
     *     ├ sd &gt;= 0                → 面板外，全透明
     *     ├ -sd &gt;= 折射高度        → 面板内部，全透明（露出真实背景）  ★ 用户要求
     *     └ 边缘带内                → 沿法线偏移采样，四分之一圆剖面
     * </pre>
     *
     * <p>★ 偏移量：{@code d = circleMap(1 - (-sd)/h) * amount}。
     * 令 {@code u = -sd/h}（0 在最外缘、1 在带宽内边界）：
     * {@code u=0 → circleMap(1)=1} 偏移最大；{@code u=1 → circleMap(0)=0} 偏移归零。
     * 内侧平滑收敛到 0，不会出现「带子边界处突然错位」的硬缝。
     *
     * <p>★ {@code amount} 由 Java 侧传**负值**（与参考实现一致）：
     * 法线朝外，负偏移 = 向内采样 = 放大镜观感。
     */
    static final String SRC = ""
            // ---- 输入（9 个 uniform）----
            + "uniform shader uBg;\n"
            + "uniform vec2  uBgSize;\n"          // 背景位图尺寸（像素）
            + "uniform vec2  uPanelSize;\n"       // 面板尺寸（像素）
            + "uniform vec2  uPanelOrigin;\n"     // 面板在屏幕上的左上角（含 bounds 偏移）
            + "uniform vec2  uScreenSize;\n"      // 屏幕尺寸（像素）
            + "uniform float uRadius;\n"          // 圆角（像素）
            + "uniform float uRefractionHeight;\n"// 折射带宽度（像素）
            + "uniform float uRefractionAmount;\n"// 最大偏移（像素，传负值）
            + "uniform float uDepthEffect;\n"     // 0 = 无深度感，1 = 叠加径向分量
            + "\n"
            // ── 圆角矩形 SDF（照 Backdrop 的 RoundedRectSDF）──
            + "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "    float outside = length(max(cornerCoord, 0.0)) - radius;\n"
            + "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n"
            + "    return outside + inside;\n"
            + "}\n"
            + "\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n"
            + "        float2 q = max(cornerCoord, 0.0);\n"
            + "        float ql = length(q);\n"
            + "        // ★ 防 NaN：正好落在直边上时 q 可能是 0，normalize(0) 是 NaN，\n"
            + "        //   NaN 会顺着 grad 传染成整块面板全黑（在 v3.1 上踩过一次类似问题）。\n"
            + "        float2 n = (ql > 1e-5) ? (q / ql) : float2(0.0, 1.0);\n"
            + "        return sign(coord) * n;\n"
            + "    } else {\n"
            + "        float gradX = step(cornerCoord.y, cornerCoord.x);\n"
            + "        return sign(coord) * float2(gradX, 1.0 - gradX);\n"
            + "    }\n"
            + "}\n"
            + "\n"
            // 四分之一圆剖面：x=0 → 0，x=1 → 1，边缘处斜率无穷
            + "float circleMap(float x) {\n"
            + "    return 1.0 - sqrt(max(0.0, 1.0 - x * x));\n"
            + "}\n"
            + "\n"
            + "half4 main(float2 fragCoord) {\n"
            + "    float2 halfSize = uPanelSize * 0.5;\n"
            + "    float2 centeredCoord = fragCoord - halfSize;\n"
            + "    float radius = min(uRadius, min(halfSize.x, halfSize.y));\n"
            + "\n"
            + "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n"
            + "\n"
            + "    // ---- 面板外：不画 ----\n"
            + "    if (sd >= 0.0) { return half4(0.0); }\n"
            + "\n"
            + "    // ---- ★★★ 内部：一律透明，让真实背景原样透出 ----\n"
            + "    //   这是「图片不再粘在板块上」和「性能」两件事的共同解。\n"
            + "    float height = max(uRefractionHeight, 1e-3);\n"
            + "    float depth = -sd;\n"
            + "    if (depth >= height) { return half4(0.0); }\n"
            + "\n"
            + "    // ---- 边缘带：折射位移采样 ----\n"
            + "    float d = circleMap(1.0 - depth / height) * uRefractionAmount;\n"
            + "    // ★ 用放大的圆角算梯度：直接用面板圆角时，转角处法线会突变，\n"
            + "    //   表现为四角出现一道折线。参考实现取 radius * 1.5。\n"
            + "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);\n"
            + "    if (uDepthEffect > 0.5) {\n"
            + "        float cl = length(centeredCoord);\n"
            + "        if (cl > 1e-5) { grad += centeredCoord / cl; }\n"
            + "    }\n"
            + "    float gl = length(grad);\n"
            + "    if (gl > 1e-5) { grad = grad / gl; }\n"
            + "\n"
            + "    float2 refracted = fragCoord + d * grad;\n"
            + "    vec2 uv = clamp((uPanelOrigin + refracted) / max(uScreenSize, vec2(1.0)),\n"
            + "                    vec2(0.0), vec2(1.0));\n"
            + "    vec3 c = uBg.eval(uv * uBgSize).rgb;\n"
            + "    return half4(half3(c), 1.0);\n"
            + "}\n";
}
