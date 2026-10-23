package com.qcl.launcher.launcher.glass;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.WeakHashMap;

/**
 * ★★★ 液态玻璃 · **边缘畸变自动挂载器**（v3.1）。
 *
 * <h3>★ v3.1 的三个变化（都来自真机反馈）</h3>
 * <ol>
 *   <li><b>默认关闭</b> —— 3.0 一上来就给整棵树套玻璃，用户第一反应是「性能很差」。
 *       现在必须由用户在外观设置里主动打开（{@link #setMode(int)}），默认 {@link #MODE_OFF}。</li>
 *   <li><b>收紧筛选</b> —— 3.0 连 22dp 的小按钮都套，几百块面板同时绘制。
 *       现在只给「够大的面板」套：导航栏、返回栏、卡片、对话框。
 *       小按钮不套 —— 它们在 14dp 的带宽里根本看不出畸变，却要照付绘制成本。</li>
 *   <li><b>不再有色调</b> —— 面板内部完全透明（见 {@link GlassDrawable}），
 *       所以这里不再有 {@code tintGlass}。</li>
 * </ol>
 *
 * <h3>为什么仍然「自动遍历」而不是手改布局</h3>
 * 界面上有一级页面、设置页 7 个子页、各种对话框，加起来 150+ 个布局文件。
 * 逐页手改不但工作量失控，而且**以后每加一个页面就要记得改** ——
 * 「记得改」是这项目里已经翻车过三次的事（签名、版本号、docs）。
 * 所以：运行时遍历 View 树，按尺寸筛出该套的面板。布局文件一个字不用改。
 */
public final class GlassApplier {

    private static final String TAG = "QCLGlass";

    // ═══════════════════════════════════════════════════════════
    //  模式（★ 默认关闭）
    // ═══════════════════════════════════════════════════════════

    /** 关闭：完全不做任何玻璃效果（**默认**）。 */
    public static final int MODE_OFF = 0;
    /** 轻量：只给「长边 ≥ 200dp」的大面板套（导航栏 / 返回栏 / 大卡片）。推荐。 */
    public static final int MODE_LIGHT = 1;
    /** 标准：长边 ≥ 110dp 的中等面板也套（小卡片 / 输入框 / 列表项）。 */
    public static final int MODE_FULL = 2;

    /**
     * ★★★ 默认关闭 —— 用户明确要求「这个液态玻璃模式在设置里开，默认是关的」。
     *
     * <p>为什么不能默认开：玻璃是逐像素着色，任何设备上都有成本；
     * 默认开等于把一个可选装饰变成所有人的负担。默认关、需要的人自己开，才是对的。
     */
    private static volatile int sMode = MODE_OFF;

    /** 打在这个 View 的 tag 上，记录「已经套过玻璃」。 */
    private static final int TAG_APPLIED = 0x7C1A5501;
    /** 记录控件原本的背景 —— 关掉玻璃时**原样还原**。 */
    private static final int TAG_ORIGINAL_BG = 0x7C1A5502;

    /**
     * 折射带宽度（dp）—— **v3.3 起由「折射强度」百分比算出来，不再是常量**。
     *
     * <p>★ 数值区间参考 kyant0 的 Backdrop 库：它在按钮上用 12dp、底部导航和对话框上用 16~24dp。
     * 本项目面板尺寸跨度大（62dp 的导航条到整屏对话框），所以给一段 10~22dp 的可调区间，
     * 默认 50% ⇒ 16dp，与 v3.2 的定值完全一致（老用户升级后观感不变）。
     */
    private static final float BAND_DP_MIN = 10f;
    private static final float BAND_DP_MAX = 22f;

    /**
     * ★★★ v3.3：折射强度百分比（0~100，默认 50）。
     *
     * <p>为什么做成可调：带宽和偏移量合不合适**完全取决于背景图的纹理密度** ——
     * 纯色背景上再强也看不出来，花哨壁纸上太强会像水波纹。这个数没法替所有人定。
     */
    private static volatile int sStrengthPercent = 50;

    public static void setStrength(int percent) {
        sStrengthPercent = Math.max(0, Math.min(100, percent));
    }

    public static int getStrength() {
        return sStrengthPercent;
    }

    /**
     * ★★★ 诊断用的一行摘要（给「我的 → 启动诊断」用）。
     *
     * <p><b>为什么必须暴露这一行</b>：玻璃「看起来没效果」有三种**完全不同**的原因 ——
     * <ol>
     *   <li>开关没开（用户以为开了，其实设置项在另一个页面）；</li>
     *   <li>这台机器的驱动编译不过 AGSL（{@code RuntimeShader} 类在，但编译抛异常）；</li>
     *   <li>编译过了，但没挂到任何控件上（覆盖范围门槛把它全筛掉了）。</li>
     * </ol>
     * 这三者在屏幕上长得一模一样。没有这行字，排查就只能靠猜 ——
     * 而「猜 → 改 → 用户说还是没有」这个循环在这个项目里已经绕了十几轮。
     * 把判断依据直接摆到用户眼前，一轮就能定位。
     */
    public static String debugLine() {
        int n = 0;
        try {
            n = sApplied.size();
        } catch (Throwable ignored) {
        }
        String mode;
        try {
            mode = !isEnabled() ? "关闭"
                    : (getMode() == MODE_FULL ? "标准" : "轻量");
        } catch (Throwable t) {
            mode = "未知";
        }
        String blur;
        try {
            Bitmap b = GlassBackdrop.blurred();
            blur = (b != null && b != GlassBackdrop.backdrop()) ? "就绪" : "缺失（退回原图）";
        } catch (Throwable t) {
            blur = "未知";
        }
        return "模式=" + mode + " 强度=" + sStrengthPercent
                + " 已挂载=" + n + " 个控件"
                + " 背景版本=" + GlassBackdrop.version()
                + " 模糊图=" + blur;
    }

    /** 百分比 → 带宽 dp。p=0 → 10dp，p=50 → 16dp（v3.2 的定值），p=100 → 22dp。 */
    private static float bandDp() {
        return BAND_DP_MIN + (BAND_DP_MAX - BAND_DP_MIN) * (sStrengthPercent / 100f);
    }

    /**
     * 百分比 → 最大偏移 dp。
     *
     * <p>★★★ 固定取带宽的 **2 倍**，不单独暴露 —— 这是 v3.2 得出的硬结论：
     * 偏移必须明显大于带宽（参考实现 1.5~2 倍）。v3.1 用 9dp 偏移配 14dp 带宽（0.64 倍），
     * 折射根本搬不动，观感只是「边缘发虚」。把两者绑在一起，用户怎么调都不会调出那个死区。
     */
    private static float strengthDp() {
        return 2f * bandDp();
    }
    /** 统一圆角（dp）—— **只在读不到面板真实圆角时才用**（v3.5 起不再是硬编码值）。 */
    private static final float RADIUS_DP = 14f;

    private static final Handler sMain = new Handler(Looper.getMainLooper());

    /** 记录每个 View 上挂的 GlassDrawable，好在 View 分离时归还着色器。 */
    private static final WeakHashMap<View, GlassDrawable> sApplied = new WeakHashMap<>();

    /** 已安装整窗监听的主界面。 */
    private static java.lang.ref.WeakReference<Activity> sHooked = new java.lang.ref.WeakReference<>(null);

    private GlassApplier() {
    }

    // ═══════════════════════════════════════════════════════════
    //  模式读写
    // ═══════════════════════════════════════════════════════════

    public static void setMode(int mode) {
        sMode = mode;
    }

    public static int getMode() {
        return sMode;
    }

    public static boolean isEnabled() {
        return sMode != MODE_OFF;
    }

    /** 兼容旧调用：true → LIGHT，false → OFF。 */
    public static void setEnabled(boolean on) {
        sMode = on ? MODE_LIGHT : MODE_OFF;
    }

    // ═══════════════════════════════════════════════════════════
    //  新页面自动接管
    // ═══════════════════════════════════════════════════════════

    /**
     * 装一个**整窗**的 View 树监听：新加进来、或者刚被显示出来的页面 / 弹框，自动套玻璃。
     *
     * <p>这个项目的页面显示方式有四条互不相干的路径：
     * <ol>
     *   <li>{@code UIManager.switchMainUI} / {@code switchMainUITab}（一级页）</li>
     *   <li>{@code SettingUIManager.switchSettingUIs}（设置页子页，另一层容器）</li>
     *   <li>页面内部自己 {@code setVisibility}（二级页、下载页左右分栏）</li>
     *   <li>{@code Dialog} / {@code PopupWindow}（挂在 WindowManager 上，不是 Activity 子树）</li>
     * </ol>
     * 逐个去接 = 以后每加一条新路径就得记得改。用 {@code OnHierarchyChangeListener}
     * 一次性兜住 2/3/4，1 由切换方法主动调 {@link #apply(Activity)} 覆盖。
     *
     * <p>★ 已全仓 grep 确认本项目**没有任何地方**用过 {@code setOnHierarchyChangeListener}，
     * 所以覆盖是安全的。若以后有人要用，请改成「先保存旧监听再转发」。
     */
    public static void attachToWindow(final Activity activity) {
        if (activity == null) {
            return;
        }
        if (sHooked.get() == activity) {
            return;
        }
        sHooked = new java.lang.ref.WeakReference<>(activity);
        try {
            hookTree(activity, activity.getWindow().getDecorView(), 0);
        } catch (Throwable t) {
            Log.w(TAG, "整窗监听安装失败（界面仍会在每次进入时扫一遍）", t);
        }
    }

    private static void hookTree(final Activity activity, View v, int depth) {
        if (v == null || depth > 24) {
            return;
        }
        try {
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                g.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        if (child != null && sMode != MODE_OFF) {
                            // ★ 此刻子 View 还没 layout（尺寸 0），maybeApply 会跳过；
                            //   延一帧再扫，那时才有尺寸。
                            scheduleScan(activity);
                        }
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) {
                        releaseTree(child);
                    }
                });
                int n = g.getChildCount();
                for (int i = 0; i < n; i++) {
                    hookTree(activity, g.getChildAt(i), depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * ★★★ 扫描去重闸门。
     *
     * <p>为什么必须有：{@code onChildViewAdded} 是**每个**子 View 加进来都会回调。
     * 列表 / 网格一次性塞 50 个条目 = 50 次回调 = 50 个 post。
     * 每个 post 都是一次**全树遍历**（几百个 View），50 次就是几万次访问 ——
     * 这正是「性能很差」的一种典型来源，而且是纯浪费：它们扫的是同一棵树、同一帧。
     * 用一个标志位把同一帧内的重复请求合并成一次。
     */
    private static boolean sScanPending = false;

    private static void scheduleScan(final Activity activity) {
        if (sScanPending) {
            return;
        }
        sScanPending = true;
        sMain.post(new Runnable() {
            @Override
            public void run() {
                sScanPending = false;
                if (sMode == MODE_OFF) {
                    return;
                }
                apply(activity);
            }
        });
    }

    /** 这个背景是不是玻璃。 */
    public static boolean isGlass(Drawable d) {
        return d instanceof GlassDrawable;
    }

    // ═══════════════════════════════════════════════════════════
    //  背景注入
    // ═══════════════════════════════════════════════════════════

    /**
     * 从 Activity 的根布局里取出当前背景位图，喂给 {@link GlassBackdrop}。
     *
     * <p>⚠️ 这里**会做模糊**（v4.0 起），不是零成本：{@code GlassBackdrop.set()} 内部要
     * 缩放 + 盒式模糊一次，900 长边下几毫秒到十几毫秒，**跑在调用线程上**。
     * 只在换背景时发生、且同一张图会被引用判等挡掉，所以可以留在主线程；
     * 若日后出现「换背景时掉帧」，第一件事是看 {@code QCLGlass} 的耗时日志，
     * 而不是直接改异步 —— 异步会让 View 不知道该重绘（v3.0 踩过）。
     */
    public static void refreshBackdrop(Activity activity, View backgroundHost) {
        if (activity == null || backgroundHost == null) {
            return;
        }
        if (sMode == MODE_OFF) {
            return;
        }
        Bitmap bmp = extractBitmap(backgroundHost.getBackground());
        if (bmp == null || bmp.isRecycled()) {
            return;
        }
        GlassBackdrop.set(bmp);
        apply(activity);
    }

    /** 从各种 Drawable 里挖出底层位图。挖不到返回 null。 */
    public static Bitmap extractBitmap(Drawable d) {
        if (d == null) {
            return null;
        }
        try {
            if (d instanceof TransitionDrawable) {
                // ★ TransitionDrawable 和 LayerDrawable 都没有 getLayers()，
                //   只有 getNumberOfLayers() + getDrawable(i)。
                TransitionDrawable td = (TransitionDrawable) d;
                int n = td.getNumberOfLayers();
                // 取最后一层 —— 淡入完成时显示的就是它
                for (int i = n - 1; i >= 0; i--) {
                    Drawable one = td.getDrawable(i);
                    if (one instanceof BitmapDrawable) {
                        return ((BitmapDrawable) one).getBitmap();
                    }
                }
            } else if (d instanceof BitmapDrawable) {
                return ((BitmapDrawable) d).getBitmap();
            } else if (d instanceof LayerDrawable) {
                LayerDrawable ld = (LayerDrawable) d;
                int n = ld.getNumberOfLayers();
                for (int i = 0; i < n; i++) {
                    Drawable one = ld.getDrawable(i);
                    if (one instanceof BitmapDrawable) {
                        return ((BitmapDrawable) one).getBitmap();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  扫描 + 挂载
    // ═══════════════════════════════════════════════════════════

    /** 从 Activity 的 decorView 开始整棵树扫一遍。主线程调用。 */
    public static void apply(Activity activity) {
        if (activity == null || sMode == MODE_OFF) {
            return;
        }
        try {
            View decor = activity.getWindow().getDecorView();
            if (decor == null) {
                return;
            }
            GlassDrawable.setScreen(
                    activity.getResources().getDisplayMetrics().widthPixels,
                    activity.getResources().getDisplayMetrics().heightPixels);
            walk(decor, 0);
        } catch (Throwable t) {
            Log.w(TAG, "全界面挂载玻璃失败（界面保持原样）", t);
        }
    }

    private static void walk(View v, int depth) {
        if (v == null || depth > 24) {
            return;
        }
        try {
            maybeApply(v);
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                int n = g.getChildCount();
                for (int i = 0; i < n; i++) {
                    walk(g.getChildAt(i), depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void maybeApply(View v) {
        // ⓪ ★★★ v3.6：没有背景位图就**什么都别套**。
        //   为什么放在最前面：{@link GlassDrawable#draw} 拿不到位图时是「什么都不画」，
        //   而面板原来的背景已经被换掉了 —— 结果就是**整块面板凭空消失**，
        //   界面上只剩浮在壁纸上的文字。这个失败模式很吓人，而且完全没有报错。
        //   ★ 只「不套新的」、不 detachAll：位图一旦设置过就不会再变 null
        //   （GlassBackdrop.clear() 没有任何调用点），所以不会把已套的面板卡成透明。
        if (GlassBackdrop.backdrop() == null) {
            return;
        }
        // ① 不该套的类型
        if (v instanceof ImageView) {
            return; // 图标/头像，玻璃会盖住图形
        }
        if (v instanceof ViewGroup && v.getBackground() == null) {
            return; // 纯排版容器，交给子控件各自套
        }

        // ② 尺寸门槛 —— ★ v3.1 的核心：只给够大的面板套
        int w = v.getWidth();
        int h = v.getHeight();
        if (w <= 0 || h <= 0) {
            return; // 还没 layout，等下一帧（见 scheduleScan）
        }
        float d = v.getResources().getDisplayMetrics().density;
        float longSideDp = Math.max(w, h) / d;
        float minLongSide = sMode == MODE_LIGHT ? 200f : 110f;
        if (longSideDp < minLongSide) {
            return;
        }
        // 细线 / 分隔条：短边太窄，套上去只是一条噪点
        if (Math.min(w, h) / d < 28f) {
            return;
        }

        // ③ 背景门槛：无背景 / 已是玻璃 / 图片，都不套
        Drawable bg = v.getBackground();
        if (bg == null || bg instanceof GlassDrawable) {
            return;
        }
        if (isImageBackdrop(bg)) {
            return; // 背景本身就是要显示的内容
        }

        // ④ 已标记过 → 跳过（幂等）
        Object tag = v.getTag(TAG_APPLIED);
        if (tag instanceof String && "v31".equals(tag)) {
            return;
        }

        GlassDrawable glass = new GlassDrawable(v)
                .radiusPx(radiusOfBackground(bg, RADIUS_DP * d))
                .band(bandDp())
                .strength(strengthDp())
                .depthEffect(true)
                // ★★★ v3.6/v3.7：把面板原来的背景交给它，**按 16% 压成半透明**画回来。
                //   1:1 画回 ⇒ 一块白灰卡片（用户报「白灰色的」）；
                //   完全不画 ⇒ 板块凭空消失（用户报「液态玻璃没有」）。
                //   16% 是这两者之间：壁纸清晰透出，面板靠边缘那圈折射 + 倒角明暗立住。
                .originalBackground(bg, 0.16f);

        // ★★★ 必须先存原背景再替换 —— 关掉开关时才能原样还原。
        //   不存的话，关掉后只能把背景设成 null，原本的白色圆角卡片会变成全透明，
        //   整个界面看起来像「坏掉了」。用户会反复开关试效果，这一条不能省。
        v.setTag(TAG_ORIGINAL_BG, bg);
        v.setBackground(glass);
        v.setTag(TAG_APPLIED, "v31");
        sApplied.put(v, glass);
    }

    /**
     * ★★★ v3.5：读面板背景**真实的圆角**（像素），读不到才用 fallback。
     *
     * <h3>为什么必须读，不能统一给 14dp</h3>
     * 玻璃的 SDF 是圆角矩形，它画出来的那一圈**就是面板可见的边**
     * （原背景已被 GlassDrawable 替换）。圆角对不上时：
     * <ul>
     *   <li>面板圆角比玻璃**小**（如 {@code launcher_button_white_blue} 是 3dp）⇒
     *       玻璃的 14dp 圆角比面板更圆，四角处玻璃往里缩 ⇒ 看起来「面板的角被切掉了」；</li>
     *   <li>面板圆角比玻璃**大**（如 {@code qcl_button_gray} 是 16dp）⇒
     *       玻璃圈跑到面板边以内 ⇒ 边缘多出一道 2dp 的错位亮线。</li>
     * </ul>
     * 本项目的面板圆角实测有 3 / 5 / 12 / 14 / 16dp 好几种，硬编码任何一个都不对。
     *
     * <h3>为什么优先用 {@code getOutline}</h3>
     * 它能**穿透** StateListDrawable（{@code launcher_button_parent} 就是 selector）、
     * LayerDrawable、RippleDrawable 这些包装，一步拿到最终形状；
     * 而 {@code GradientDrawable} 只覆盖「直接就是 shape」的那一半。
     * ★ {@code radius == 0} 是**有效答案**（面板本来就是直角），不能当成「读不到」。
     */
    private static float radiusOfBackground(Drawable bg, float fallbackPx) {
        if (bg == null) {
            return fallbackPx;
        }
        // ① 通用路径：让 Drawable 自己报它的外轮廓
        try {
            android.graphics.Outline outline = new android.graphics.Outline();
            bg.getOutline(outline);
            if (!outline.isEmpty()) {
                return Math.max(0f, outline.getRadius());
            }
        } catch (Throwable ignored) {
        }
        // ② 兜底：自己挖 shape 的 corners（per-corner 写法时 getCornerRadius() 返回 0）
        float r = maxCornerRadius(bg, 0);
        return r >= 0f ? r : fallbackPx;
    }

    /** 递归取各种 Drawable 里的最大圆角；返回 -1 = 挖不到（与「圆角为 0」区分开）。 */
    private static float maxCornerRadius(Drawable d, int depth) {
        if (d == null || depth > 4) {
            return -1f;
        }
        try {
            if (d instanceof GradientDrawable) {
                GradientDrawable gd = (GradientDrawable) d;
                float r = gd.getCornerRadius();
                if (r > 0f) {
                    return r;
                }
                float[] radii = gd.getCornerRadii();
                if (radii != null) {
                    float m = 0f;
                    for (float v : radii) {
                        if (v > m) {
                            m = v;
                        }
                    }
                    return m;
                }
                return 0f; // 明确没设圆角 = 直角
            }
            if (d instanceof LayerDrawable) {
                // ★ RippleDrawable 也走这里（它继承 LayerDrawable）
                LayerDrawable ld = (LayerDrawable) d;
                float m = -1f;
                for (int i = 0; i < ld.getNumberOfLayers(); i++) {
                    float v = maxCornerRadius(ld.getDrawable(i), depth + 1);
                    if (v > m) {
                        m = v;
                    }
                }
                return m;
            }
            if (d instanceof android.graphics.drawable.InsetDrawable) {
                return maxCornerRadius(
                        ((android.graphics.drawable.InsetDrawable) d).getDrawable(), depth + 1);
            }
            if (d instanceof DrawableContainer) {
                // StateListDrawable / AnimationDrawable 等：看当前那一层
                Drawable cur = ((DrawableContainer) d).getCurrent();
                if (cur != null && cur != d) {
                    return maxCornerRadius(cur, depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
        return -1f;
    }

    /** 背景本身是不是「要显示的图片」（那种不能被玻璃盖住）。 */
    private static boolean isImageBackdrop(Drawable bg) {
        try {
            if (bg instanceof BitmapDrawable) {
                return true;
            }
            if (bg instanceof LayerDrawable) {
                LayerDrawable ld = (LayerDrawable) bg;
                for (int i = 0; i < ld.getNumberOfLayers(); i++) {
                    if (ld.getDrawable(i) instanceof BitmapDrawable) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 递归重新套（一个容器下的所有子控件）。 */
    public static void reapplyTree(View root) {
        if (root == null || sMode == MODE_OFF) {
            return;
        }
        walk(root, 0);
    }

    /**
     * ★ 让所有已套玻璃的 View 重绘一遍。
     *
     * <p><b>为什么需要</b>：换背景时 {@link GlassBackdrop#set(Bitmap)} 只改了静态引用，
     * View 并不知道内容变了 —— 不主动 invalidate，边缘折射会一直显示上一张背景，
     * 看起来就是「背景换了，玻璃边没换」。
     *
     * <p>★ 从 {@code root.getRootView()} 开始扫：调用方传进来的通常是
     * {@code launcherLayout}，而导航栏 / 返回栏是它的**兄弟**，不在它的子树里。
     */
    public static void invalidateApplied(View root) {
        if (root == null) {
            return;
        }
        try {
            View top = root.getRootView();
            invalidateTree(top != null ? top : root, 0);
        } catch (Throwable ignored) {
        }
    }

    private static void invalidateTree(View v, int depth) {
        if (v == null || depth > 24) {
            return;
        }
        try {
            if (v.getBackground() instanceof GlassDrawable) {
                v.invalidate();
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    invalidateTree(g.getChildAt(i), depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 释放某棵树里所有玻璃持有的着色器实例。 */
    public static void releaseTree(View root) {
        if (root == null) {
            return;
        }
        try {
            GlassDrawable g = sApplied.remove(root);
            if (g != null) {
                g.release();
            }
            if (root instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) root;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    releaseTree(vg.getChildAt(i));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * ★ 关闭玻璃时，把已经套上的面板**还原**成原来的背景。
     *
     * <p>为什么需要：{@link #maybeApply} 把 View 的背景换成了 GlassDrawable，
     * 原来那个 drawable 就丢了。关掉开关后如果只是「不再套新的」，
     * 已经套上的会留在界面上 —— 表现就是「关了还在」。所以关的时候要整棵树清一遍。
     */
    public static void detachAll(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            detachTree(activity.getWindow().getDecorView(), 0);
        } catch (Throwable ignored) {
        }
    }

    private static void detachTree(View v, int depth) {
        if (v == null || depth > 24) {
            return;
        }
        try {
            if (v.getBackground() instanceof GlassDrawable) {
                GlassDrawable g = (GlassDrawable) v.getBackground();
                g.release();
                // ★★★ v3.7：先把原背景的透明度复原 ——
                //   v3.7 起玻璃会把原背景压到 16% 再画回来（见 GlassDrawable#originalAlpha），
                //   不还原的话，关掉玻璃后面板会**永久停在半透明**，看起来像界面坏了。
                g.restoreOriginal();
                // ★★★ 还原成**原来那个背景**（不是 null）——
                //   原背景在 maybeApply 里存进 TAG_ORIGINAL_BG 了。
                //   还原成 null 会让原本的白色圆角卡片变成全透明，界面像坏掉一样。
                Object orig = v.getTag(TAG_ORIGINAL_BG);
                if (orig instanceof Drawable) {
                    try {
                        ((Drawable) orig).setAlpha(255);
                    } catch (Throwable ignored) {
                    }
                }
                v.setBackground(orig instanceof Drawable ? (Drawable) orig : null);
                v.setTag(TAG_ORIGINAL_BG, null);
                v.setTag(TAG_APPLIED, null);
                sApplied.remove(v);
            }
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    detachTree(vg.getChildAt(i), depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
