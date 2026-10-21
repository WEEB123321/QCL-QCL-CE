package com.qcl.launcher.launcher.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * ★★★ 液态玻璃 · **纯边缘畸变**着色器（v3.1）。
 *
 * <h3>★ 这一版为什么砍到只剩「一圈畸变」</h3>
 * 3.0 的版本把整块面板都画出来：背景模糊 + 色调 + 菲涅尔亮边 + 高光 + 投影 + RGB 色散。
 * 真机反馈有三个问题，根因都在「画得太多」：
 * <ol>
 *   <li><b>性能差</b> —— 整块面板每像素都要算折射 + 采样，几百块面板叠加就是幻灯片。</li>
 *   <li><b>「图片被贴在板块上」</b> —— 面板里画了一份背景副本，面板一滚动，
 *       副本跟着面板走，看起来像图片粘在板块上滑。这不是 bug，是「画了副本」的必然结果。</li>
 *   <li><b>白色边太抢眼</b> —— 菲涅尔 + 高光在深色背景上是一圈发光的白线，很假。</li>
 * </ol>
 *
 * <p>v3.1 的取舍：<b>面板内部一律返回完全透明</b>，让真正的背景原样透出来；
 * 只有靠近边缘的 {@code uBand} 像素宽的一条带子里，才做折射位移采样。
 * 于是：
 * <ul>
 *   <li>看到的永远是**真实背景**，不是副本 ⇒ 滚动时不会「粘在板块上」；</li>
 *   <li>没有模糊、没有白边、没有投影 ⇒ 就是用户要的「一层畸变圈」；</li>
 *   <li>填充率降到只剩边缘一圈 ⇒ 比 3.0 快一个数量级。</li>
 * </ul>
 *
 * <h3>⚠️ 关于「不模糊」的代价</h3>
 * 不做模糊意味着玻璃后面是清晰的原图，严格说这不是「毛玻璃」而是「透镜」。
 * 这是**用户明确要求**的（原话：「其他的不要，比如背景模糊之类的」），
 * 所以不再保留模糊路径。
 *
 * <h3>AGSL 要点</h3>
 * <ul>
 *   <li>原点在**左上角、y 轴向下**，与 GLSL 相反，所以**不要翻转 y**。</li>
 *   <li>{@code fragCoord} 是画布局部坐标，与 {@code drawRect} 同一套。</li>
 *   <li>循环必须能编译期展开 —— 这里没有循环。</li>
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
            sPath = "AGSL 边缘畸变（纯折射，无模糊/无白边）";
            Log.i(TAG, "玻璃着色器可用（v3.1 边缘畸变）");
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
     * 面板着色器源码（AGSL）。
     *
     * <h3>结构</h3>
     * <pre>
     *   SDF 求到边缘的距离 d
     *     ├ d &gt;= 0（面板外）      → 全透明，什么都不画
     *     ├ d &lt; -uBand（内部）    → 全透明，让真实背景透出来   ★ 这就是「不要副本」
     *     └ 边缘带内               → 沿法线偏移采样，越靠边偏得越多
     * </pre>
     *
     * <p>★ 偏移量用 {@code k = t²} 而非线性：平方让畸变集中在最外缘，
     * 内侧平滑收敛到 0，不会出现「带子边界处突然错位」的硬缝。
     */
    static final String SRC = ""
            // ---- 输入（只有 8 个 uniform，3.0 是 20 个）----
            + "uniform shader uBg;\n"
            + "uniform vec2  uBgSize;\n"       // 背景纹理尺寸（像素）
            + "uniform vec2  uPanelSize;\n"    // 面板尺寸（像素）
            + "uniform vec2  uPanelOrigin;\n"  // 面板在屏幕上的左上角
            + "uniform vec2  uScreenSize;\n"
            + "uniform float uRadius;\n"       // 圆角（像素）
            + "uniform float uBand;\n"         // 畸变带宽度（像素）
            + "uniform float uStrength;\n"     // 最大偏移（像素）
            + "\n"
            // 圆角矩形 SDF，返回 (距离, 法线xy)，距离 < 0 在内部
            + "vec3 sdgBox(vec2 p, vec2 b, float r) {\n"
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
            + "    float r = min(uRadius / H.y, min(b.x, b.y));\n"
            + "\n"
            + "    vec3 g = sdgBox(p, b, r);\n"
            + "    float dPx = g.x * H.y;\n"
            + "\n"
            + "    // ---- 面板外：不画 ----\n"
            + "    if (dPx >= 0.0) { return half4(0.0); }\n"
            + "\n"
            + "    // ---- ★★★ 内部：一律透明，让真实背景原样透出 ----\n"
            + "    //   这是「图片不再粘在板块上」和「性能」两件事的共同解。\n"
            + "    float depth = -dPx;\n"
            + "    if (depth > uBand) { return half4(0.0); }\n"
            + "\n"
            + "    // ---- 边缘带：折射位移采样 ----\n"
            + "    float nlen = length(g.yz);\n"
            + "    vec2 n = (nlen > 1e-6) ? (g.yz / nlen) : vec2(0.0);\n"
            + "    float t = 1.0 - depth / max(uBand, 1e-4);\n"
            + "    float k = t * t;\n"
            + "    vec2 off = n * (k * uStrength) / H.y;\n"
            + "\n"
            + "    vec2 uv = clamp((uPanelOrigin + fragCoord) / H + off, vec2(0.0), vec2(1.0));\n"
            + "    vec3 c = uBg.eval(uv * uBgSize).rgb;\n"
            + "    return half4(half3(c), 1.0);\n"
            + "}\n";
}
