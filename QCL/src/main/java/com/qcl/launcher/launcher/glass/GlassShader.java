package com.qcl.launcher.launcher.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * ★★★ 液态玻璃 · 折射着色器（v4.0，照 ReGlass 模组移植）。
 *
 * <h3>参考实现</h3>
 * {@code D:\QCL-CE资源包\reglass-26.3-2.1.1.jar} 里的
 * {@code assets/reglass/shaders/program/liquid_glass_gui.fsh}。
 * Java 侧默认参数见同包的 {@code ReGlassConfig} / {@code WidgetStyle}。
 *
 * <p><b>为什么不能直接依赖别人的库</b>：ReGlass 是 Minecraft 模组（GLSL，跑在桌面 GPU 上），
 * kyant0 的 Backdrop 是 Compose Multiplatform 工程（依赖 Compose Runtime / Skiko）。
 * QCL 是传统 View 体系，没有任何 Compose。引入 Compose 只为一个背景效果，
 * 会让包体、启动时间和构建复杂度都上一个台阶，收益不成比例。
 * ⇒ 只移植**着色器数学**（纯 AGSL 字符串，无依赖），适配成 View 背景 Drawable 能用的形式。
 *
 * <h3>v3.x → v4.0 到底改了什么</h3>
 * <table border="1">
 *   <tr><th></th><th>v3.x</th><th>v4.0（照 ReGlass 移植）</th></tr>
 *   <tr><td>剖面曲线</td><td>{@code circleMap(x) = 1 - sqrt(1 - x²)} 四分之一圆</td>
 *       <td>{@code asin(xR²)} → 斯涅尔 → {@code -tan(thT-thI)}</td></tr>
 *   <tr><td>色散</td><td>无</td><td>RGB 三通道不同位移（0.985 / 1.0 / 1.015 × 强度）</td></tr>
 *   <tr><td>菲涅尔 / 眩光</td><td>无</td><td>同一条 5 次方多项式（range 30 / hardness 0.2）</td></tr>
 *   <tr><td>采样对象</td><td>原图</td><td><b>模糊图</b>（ReGlass 的 Sampler1..5）</td></tr>
 *   <tr><td>面板内部</td><td>全透明（只画边带）</td><td><b>模糊背景 + 透镜边缘</b>（整块画）</td></tr>
 * </table>
 *
 * <p>★ 四分之一圆在边缘处斜率也趋于无穷，但它的位移摊满整条带宽 ⇒ 观感是「边缘发虚」；
 * ReGlass 的剖面把位移压在最外缘 20% ⇒ 是一道**锐利的压缩带**。这是「像玻璃」和「像贴纸」的分界。
 *
 * <h3>★★★ 两条最容易搬错的地方（都踩过，且都编译能过）</h3>
 * <ol>
 *   <li><b>位移量是归一化 UV，不是像素</b>。ReGlass 的原式是
 *       {@code -normal * edgeFactor * 0.08 * vec2(inSize.y/inSize.x, 1.0)}，
 *       0.08 是「屏幕高度的比例」。第一版把像素偏移直接加到 0~1 的 uv 上，
 *       96px 被当成 96 个屏幕宽度 ⇒ clamp 到边界 ⇒ 整条边带采样同一个角落像素。</li>
 *   <li><b>菲涅尔的深度项是「减」不是「加」</b>。ReGlass 写的是 {@code merged * inSize.y}，
 *       而 {@code merged} 是**负的**有符号距离 ⇒ 等价于 {@code -depth}。
 *       写成 {@code +depth} 时，外缘 {@code (1+0.2)^5} 已经 >1 被 clamp 成 1.0
 *       ⇒ 整块面板恒定叠一层 14% 白，正是用户反复抱怨的「白灰」。</li>
 * </ol>
 *
 * <h3>★ 与参考实现的三处刻意不同</h3>
 * <ul>
 *   <li><b>内部快速路径</b>：折射只在 {@code depth < 厚度} 那圈存在，之外三通道采的是同一点
 *       ⇒ 内部只采一次，省 75% 纹理读取（面板内部占面积 90%+）。</li>
 *   <li><b>去掉 hover / focus 分支</b>：那是桌面端鼠标悬停和键盘焦点用的，手机没有。</li>
 *   <li><b>参数从 uniform 来</b>，不是从纹理图集读：我们没有 {@code Optics0} 那种批量面板。</li>
 * </ul>
 *
 * <h3>AGSL 要点</h3>
 * <ul>
 *   <li>类型是 {@code float2 / float3 / half4}，不是 GLSL 的 {@code vec2 / vec3}。</li>
 *   <li>{@code uniform shader uBg} 用 {@code uBg.eval(pixelCoord)} 采样，**不是归一化 uv**
 *       —— 所以传的是 {@code uv * uBgSize}。</li>
 *   <li>原点在**左上角、y 轴向下**，与 GLSL 相反，**不要翻转 y**。</li>
 *   <li>{@code fragCoord} 是画布局部坐标，必须先把 canvas translate 到面板原点再画，
 *       否则 SDF 的圆心对不上（v3.1 踩过：控件有 padding 时圈会整体画歪）。</li>
 *   <li>没有循环（AGSL 要求循环可编译期展开）。</li>
 *   <li>{@code pow} 的底数必须先 clamp 再取幂：底数会变负，{@code pow(负, 5)} 在 SkSL 里是 NaN。</li>
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
            sPath = "AGSL 折射（ReGlass 光学模型：IOR 1.4 + 色散 + 菲涅尔）";
            Log.i(TAG, "玻璃着色器可用：" + sPath);
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
        // ★★★ v4.0：绑的是**模糊版**。ReGlass 的玻璃采样的是模糊后的背景
        //   （它的 Sampler1..5 就是多级模糊），折射偏移作用在模糊图上才像玻璃；
        //   拿原图做位移只会得到「画面错位」。
        Bitmap b = GlassBackdrop.blurred();
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
            // ── 输入 ──
            + "uniform shader uBg;\n"              // 模糊后的背景
            + "uniform float2 uBgSize;\n"
            + "uniform float2 uPanelSize;\n"
            + "uniform float2 uPanelOrigin;\n"
            + "uniform float2 uScreenSize;\n"
            + "uniform float uRadius;\n"
            // ── 光学参数（默认值全部照 ReGlassConfig，见 GlassOptics）──
            + "uniform float uRefThickness;\n"     // 玻璃厚度（像素）
            + "uniform float uRefFactor;\n"        // 折射率 IOR
            + "uniform float uRefDisp;\n"          // 色散强度
            + "uniform float uRefrAmount;\n"       // 边缘最大位移（像素）
            + "uniform float uFresRange;\n"
            + "uniform float uFresHard;\n"
            + "uniform float uFresFac;\n"
            + "uniform float uGlareRange;\n"
            + "uniform float uGlareHard;\n"
            + "uniform float3 uTint;\n"
            + "uniform float uTintA;\n"
            + "uniform float uPressT;\n"
            + "uniform float uAppear;\n"
            + "\n"
            + "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "    float2 q = abs(coord) - (halfSize - float2(radius));\n"
            + "    float outside = length(max(q, 0.0)) - radius;\n"
            + "    float inside = min(max(q.x, q.y), 0.0);\n"
            + "    return outside + inside;\n"
            + "}\n"
            + "\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "    float2 q = abs(coord) - (halfSize - float2(radius));\n"
            + "    if (q.x >= 0.0 || q.y >= 0.0) {\n"
            + "        float2 v = max(q, 0.0);\n"
            + "        float l = length(v);\n"
            + "        float2 n = (l > 1e-5) ? (v / l) : float2(0.0, 1.0);\n"
            + "        return sign(coord) * n;\n"
            + "    }\n"
            + "    float gx = step(q.y, q.x);\n"
            + "    return sign(coord) * float2(gx, 1.0 - gx);\n"
            + "}\n"
            + "\n"
            + "half4 main(float2 fragCoord) {\n"
            + "    float2 halfSize = uPanelSize * 0.5;\n"
            + "    float2 c = fragCoord - halfSize;\n"
            + "    float radius = min(uRadius, min(halfSize.x, halfSize.y));\n"
            + "    float sd = sdRoundedRect(c, halfSize, radius);\n"
            + "    if (sd >= 1.0) { return half4(0.0); }\n"
            + "\n"
            + "    float depth = max(-sd, 0.0);\n"
            // ★★★ 法线用**放大 1.5 倍的圆角**算（照 ReGlass / Backdrop）。
            //   直接用面板圆角时，直边与圆角的交界处法线会突变，四角出现一道折线。
            + "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "    float2 nrm = gradSdRoundedRect(c, halfSize, max(gradRadius, 1e-3));\n"
            + "    float nlen = length(nrm);\n"
            + "    nrm = (nlen > 1e-6) ? (nrm / nlen) : float2(0.0, 1.0);\n"
            + "\n"
            // ── ReGlass 的透镜剖面（liquid_glass_gui.fsh 第 283~291 行）──
            //   xR   = 1 - 距边缘的深度 / 玻璃厚度  （外缘 1 → 厚度处 0）
            //   thI  = asin(xR²)        入射角：外缘 90°，到厚度处 0°
            //   thT  = asin(sin(thI)/n) 折射角（斯涅尔定律）
            //   edge = -tan(thT - thI)  位移量：外缘最大、厚度处归零
            //   ★ 这个剖面在**外缘处斜率最大**，所以边缘会有一道被"压缩"的锐利带，
            //     这是玻璃和贴纸的分界线（v3.x 用的四分之一圆没这个效果）。
            + "    float thick = max(uRefThickness, 1e-3);\n"
            + "    float xR = 1.0 - depth / thick;\n"
            + "    float thI = asin(pow(clamp(xR, 0.0, 1.0), 2.0));\n"
            + "    float thT = asin(clamp((1.0 / max(uRefFactor, 1e-6)) * sin(thI), -1.0, 1.0));\n"
            + "    float edgeF = -tan(thT - thI);\n"
            + "    if (depth >= thick) { edgeF = 0.0; }\n"
            // ★★★ 位移量是**归一化 UV**，不是像素。ReGlass 的原式：
            //   refrOffset = -normal * edgeFactor * 0.08 * vec2(inSize.y/inSize.x, 1.0)
            //   我第一版把它当成像素直接加到 uv0 上（uv0 是 0~1 的归一化值），
            //   于是偏移量 96 被当成「96 个屏幕宽度」⇒ clamp 到边界 ⇒
            //   整个边缘带采样同一个角落像素，看起来像一块纯色涂抹。
            //   x 分量乘宽高比：让横向和纵向的**视觉**位移量一致。
            + "    float2 off = -nrm * edgeF * uRefrAmount\n"
            + "            * float2(uScreenSize.y / max(uScreenSize.x, 1.0), 1.0)\n"
            + "            * (1.0 + 0.45 * uPressT);\n"
            + "\n"
            + "    float2 uv0 = (uPanelOrigin + fragCoord) / max(uScreenSize, float2(1.0));\n"
            + "    float2 uvC = clamp(uv0, float2(0.0), float2(1.0));\n"
            // ★★★ 内部快速路径（性能）：折射只在 depth < thick 的那一圈里存在，
            //   厚度之外 off 恒为 0 ⇒ 三个通道的采样点是同一个点，色散没有任何作用。
            //   但着色器仍然会老老实实采三次 —— 面板内部占面积 90% 以上，
            //   等于每个像素白花 2 次纹理读取（加上眩光那次一共 4 次）。
            //   这里先采一次未位移的 raw，内部直接用它，边缘带才走三通道分支。
            //   ★ 为什么不用「只在带内 drawRect」那套老办法：ReGlass 的玻璃内部
            //     是**模糊背景**，不是透明，必须整块画（见类注释 V5）。
            + "    float3 raw = uBg.eval(uvC * uBgSize).rgb;\n"
            + "    float3 col;\n"
            + "    if (depth >= thick) {\n"
            + "        col = raw;\n"
            + "    } else {\n"
            // ── 色散：R / G / B 用略微不同的位移量 ──
            //   ReGlass 的 NR=0.985 / NG=1.0 / NB=1.015，再乘色散强度
            + "        float kR = 1.0 + 0.015 * uRefDisp;\n"
            + "        float kB = 1.0 - 0.015 * uRefDisp;\n"
            + "        col = float3(0.0);\n"
            + "        col.r = uBg.eval(clamp(uv0 + off * kR, float2(0.0), float2(1.0)) * uBgSize).r;\n"
            + "        col.g = uBg.eval(clamp(uv0 + off, float2(0.0), float2(1.0)) * uBgSize).g;\n"
            + "        col.b = uBg.eval(clamp(uv0 + off * kB, float2(0.0), float2(1.0)) * uBgSize).b;\n"
            + "    }\n"
            + "\n"
            + "    col = mix(col, uTint, uTintA * 0.8);\n"
            + "\n"
            // ── 菲涅尔：玻璃正面反射的一层乳白 ──
            // ★★★ 这里是**减** depth，不是加。ReGlass 用的是 merged * inSize.y，
            //   而 merged 是**负的**有符号距离（内部为负）⇒ 等价于 -depth。
            //   我第一版写成 +depth：外缘处 1+0.2 再取 5 次方就已经 >1 被 clamp 成 1.0，
            //   整块面板恒定叠一层 14% 白 —— 正是用户反复抱怨的「白灰」。
            // ★ 先 clamp **底数** 再取 5 次方，不是取完再 clamp。
            //   底数会随 depth 变成负数（这正是「越往里越没有反光」的来源），
            //   而 pow(负数, 5.0) 在 SkSL/GLSL 里是未定义 —— 多数驱动走
            //   exp2(y*log2(x))，log2(负数) = NaN，NaN 会顺着 mix 传染整块面板。
            + "    float fresBase = clamp(1.0 - depth / 1500.0"
            + "            * pow(500.0 / max(uFresRange, 1e-6), 2.0) + uFresHard, 0.0, 1.0);\n"
            + "    float fres = pow(fresBase, 5.0);\n"
            + "    col = mix(col, mix(float3(1.0), uTint, uTintA * 0.5), fres * uFresFac * 0.7);\n"
            + "\n"
            // ── 眩光：朝光的那半边再多一层"雾面反光" ──
            //   ★ 复用上面已经采过的那次 raw，不再重复采样
            + "    float glBase = clamp(1.0 - depth / 1500.0"
            + "            * pow(500.0 / max(uGlareRange, 1e-6), 2.0) + uGlareHard, 0.0, 1.0);\n"
            + "    float gl = pow(glBase, 5.0);\n"
            + "    col = mix(col, mix(raw, uTint, uTintA * 0.5), 0.25 * gl);\n"
            + "\n"
            // ★ 外缘 1px 抗锯齿：面板原来的底色已经不画了，这一圈就是可见的边
            + "    float aa = clamp(-sd, 0.0, 1.0);\n"
            + "    return half4(half3(col), half(aa) * half(clamp(uAppear, 0.0, 1.0)));\n"
            + "}\n";
}
