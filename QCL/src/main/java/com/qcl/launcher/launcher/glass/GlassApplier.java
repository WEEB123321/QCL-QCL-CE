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
     * 本项目面板尺寸跨度大（62dp 的导航条到整屏对话框），所以给一段可调区间。
     *
     * <p>★★★ v4.1：区间从 10~22dp 提到 <b>14~30dp</b>，默认 50% ⇒ 22dp（66px）。
     * 原因：用户报「背景扭曲加大，这个样子和没有一样」。原来 16dp 的带在横屏
     * 380px 高的面板上只占 12.6%，又窄又淡；22dp 占 17%，压缩带能看清走向。
     * ★ 带宽加大必须同时加大位移 —— 两者由 {@link #refrGain()} 绑成固定倍数关系，
     * 不会出现「带变宽但搬不动」的死区；而且这个倍数**与面板大小无关**（v4.2）。
     */
    private static final float BAND_DP_MIN = 14f;
    private static final float BAND_DP_MAX = 30f;

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
     * ★★★ v3.0.4：折射位移增益（**无量纲倍数**，不是 dp、不是百分比）。
     *
     * <p>为什么从常量变成可调：v4.2 把定标基准从「面板高度」改成「折射带宽度」之后，
     * 位移/带宽 恒为 2.16 倍 —— 这个数对**所有手机都一样对了**，但它是不是用户
     * 想要的强度，仍然取决于他的壁纸纹理密度。带宽已经有滑杆了，位移没有，
     * 于是「想再强一点」的人没有任何旋钮可拧。
     *
     * <p>★ 存的是**倍数**，不是设置里的百分比。百分比 → 倍数的换算只在这一处
     * （{@code GAIN_PERCENT_DIV}），调用方拿到手就是能直接喂给
     * {@link GlassDrawable#refrGain(float)} 的量，不可能再传错单位。
     */
    private static volatile float sRefrGain = GlassDrawable.REFR_GAIN_DEFAULT;

    /** 设置项百分比 → 倍数的除数。20% = 1.0×，44% = 2.2×，55% = 2.75×。 */
    public static final int GAIN_PERCENT_DIV = 20;

    /** 百分比的最小/最大值（对应 1.0× ~ 2.75×，略宽于 GlassDrawable 的 [1.2, 2.6] 夹取）。 */
    public static final int GAIN_PERCENT_MIN = 20;
    public static final int GAIN_PERCENT_MAX = 55;

    /** 默认百分比：44 / 20 = 2.2，即 v4.2 定稿值。 */
    public static final int GAIN_PERCENT_DEFAULT = 44;

    public static void setGainPercent(int percent) {
        int p = Math.max(GAIN_PERCENT_MIN, Math.min(GAIN_PERCENT_MAX, percent));
        sRefrGain = p / (float) GAIN_PERCENT_DIV;
    }

    public static int getGainPercent() {
        return Math.round(sRefrGain * GAIN_PERCENT_DIV);
    }

    /** 当前生效的位移倍数（已按 {@link GlassDrawable} 的区间夹好）。 */
    public static float refrGain() {
        return sRefrGain;
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
                + " 位移增益=" + String.format("%.2f", sRefrGain) + "x"
                + " 已挂载=" + n + " 个控件"
                + " 背景版本=" + GlassBackdrop.version()
                + " 模糊图=" + blur;
    }

    /** 当前已挂上玻璃的控件数（给设置页实时显示用）。 */
    public static int appliedCount() {
        try {
            return sApplied.size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * ★ v3.0.4：**位移 ÷ 带宽**的实际倍数。
     *
     * <h3>为什么要把这个数暴露出来</h3>
     * 它是「全手机适配」的唯一可验证判据：v4.2 之前位移按**面板高度**定标，
     * 同一台手机上这个比值能在 0.55× ~ 10.7× 之间飘（差 15 倍）。
     * 现在它只由增益决定，**与屏幕尺寸、密度、朝向、面板大小全部无关** ——
     * 所以设置页上把这个数亮出来，用户一眼就能确认「这台手机也是对的」。
     *
     * <p>0.983 是 IOR = 1.4 时 {@code max(-tan(thetaT - thetaI))} 的解析值
     * （见 {@link GlassShader} 的剖面推导），不是拟合常数。
     */
    public static float displacementRatio() {
        return 0.983f * sRefrGain;
    }

    /** 当前折射带宽度（dp）—— 设置页显示用。 */
    public static float bandDpPublic() {
        return bandDp();
    }

    /** 百分比 → 带宽 dp。p=0 → 14dp，p=50 → 22dp，p=100 → 30dp（v4.1 起）。 */
    private static float bandDp() {
        return BAND_DP_MIN + (BAND_DP_MAX - BAND_DP_MIN) * (sStrengthPercent / 100f);
    }

    /**
     * ★★★ v4.2：折射增益不再从带宽二次换算，直接给固定倍数。
     *
     * <h3>为什么删掉了 {@code strengthDp()}</h3>
     * v4.1 这里是 {@code 2 * bandDp()}（dp），再在 {@link GlassDrawable} 里除以 32
     * 换成「占面板高度的比例」。那个换算把一个**无量纲的位移倍数**和
     * **面板高度**绑在了一起 —— 结果同一台手机上，40dp 的列表项位移 36px
     * （比带宽还窄，搬不动），整屏面板位移 708px（带宽的 10.7 倍，折叠成条纹）。
     * 用户报的「一坨屎」「有些手机不行」就是这个：面板越大越烂，越小越看不见。
     *
     * <p>现在位移 = 带宽 × {@link #sRefrGain}（v3.0.4 起可由设置调整），
     * 位移/带宽 对所有屏幕、密度、朝向、面板尺寸都一样。
     *
     * <p>★ 「折射强度」滑杆仍然有效，它改的是**带宽**（见 {@link #bandDp()}），
     * 位移按固定倍数跟随。一个设置项只表达一件事：带越宽，被扭曲的那一圈越大。
     *
     * @deprecated v3.0.4 起请用 {@link #refrGain()} —— 那个读的是可调字段。
     *             本方法保留只为不打断旧调用点，返回的仍是 v4.2 的定稿值。
     */
    @Deprecated
    private static float refrGainDefault() {
        return GlassDrawable.REFR_GAIN_DEFAULT;
    }

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
        // ★ v4.3：同时装「布局完成即补扫」—— 没有它，切页时会先画一帧原背景
        attachLayoutHook(activity);
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

    /**
     * ★★★ v4.3：是否有「待处理」的扫描请求。
     *
     * <p>和 {@link #sScanPending} 的区别：那个只用来合并同一帧内的多次 {@code post}，
     * 这个表示「有一页刚切进来、还没被扫到」，必须活到**下一次 layout 完成**为止。
     */
    private static volatile boolean sPendingScan = false;

    /** 已经装过 layout 监听的 Activity（每个 Activity 只装一次）。 */
    private static java.lang.ref.WeakReference<Activity> sLayoutHooked =
            new java.lang.ref.WeakReference<>(null);

    /**
     * ★★★ v4.3：装「布局完成即补扫」监听 —— 修「切页时玻璃闪一下变回原样」。
     *
     * <h3>为什么 {@code post()} 不够（这就是那个 bug）</h3>
     * 页面切换的时序是：{@code setVisibility(GONE/VISIBLE)} → 请求 layout → 遍历（measure/layout/draw）。
     * 而 {@code View.post()} 投递的 Runnable 跑在**消息队列的下一条消息**上，
     * 通常在**下一次遍历之前**就执行了 —— 那一刻刚变可见的页面还没 measure，
     * 所有面板 {@code getWidth()==0}，{@link #maybeApply} 直接跳过。
     * 于是这一帧画的是**面板原来的背景**（没有玻璃），要等下一次扫描才补上 ——
     * 用户看到的就是「切换时玻璃没了，切完又好了」。
     *
     * <h3>为什么 OnGlobalLayout 是对的</h3>
     * 它在 {@code performLayout()} 末尾、{@code performDraw()} **之前**触发
     * ⇒ 在这里补扫，新页面**第一帧画出来就已经是玻璃**，没有中间态。
     *
     * <p>★ 只在 {@link #sPendingScan} 为真时才扫：正常滚动/动画每帧都会 layout，
     * 无条件扫等于每帧全树遍历几百个 View。
     */
    public static void attachLayoutHook(final Activity activity) {
        if (activity == null || sLayoutHooked.get() == activity) {
            return;
        }
        sLayoutHooked = new java.lang.ref.WeakReference<>(activity);
        try {
            final View decor = activity.getWindow().getDecorView();
            decor.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (!sPendingScan || sMode == MODE_OFF) {
                                return;
                            }
                            sPendingScan = false;
                            apply(activity);
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "布局监听安装失败（仍会在每次切页后 post 补扫一次）", t);
        }
    }

    /**
     * ★★★ v4.3：请求一次补扫。
     *
     * <p>两条路同时铺：
     * <ol>
     *   <li>置 {@link #sPendingScan} —— 下一次 layout 完成时由
     *       {@link #attachLayoutHook} 的监听器扫（**正确的那一帧**）；</li>
     *   <li>再 {@code post} 一次兜底 —— 万一这次切换根本没触发 layout
     *       （例如页面本来就已测量过），也得扫。</li>
     * </ol>
     * ★ 兜底那次**不清** {@code sPendingScan}：清了就把第 ① 条路废掉，
     *   又回到「先扫一遍 0 尺寸、然后永远不补」的老 bug。
     */
    public static void requestScan(final Activity activity) {
        if (activity == null) {
            return;
        }
        sPendingScan = true;
        try {
            activity.getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    if (sMode == MODE_OFF) {
                        return;
                    }
                    apply(activity);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void scheduleScan(final Activity activity) {
        // ★ v4.3：新加进来的子 View 同样要等 layout 完成才能扫（否则尺寸是 0）
        sPendingScan = true;
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

        // ④ ★★★ v4.4：**全屏的透明背景容器不是面板**，绝不能套玻璃。
        //
        //   实测：几乎所有页面根布局都是
        //     <LinearLayout android:background="@color/launcher_ui_background"
        //                   android:layout_width="match_parent"
        //                   android:layout_height="match_parent">
        //   而 launcher_ui_background = #00000000（**全透明**）。
        //   它的语义是「我什么都不画，让壁纸透出来」，压根不是一块面板。
        //
        //   套上玻璃的后果正是用户报的那一条：
        //   <ul>
        //     <li>整页变成一张**铺满内容区的不透明模糊壁纸** —— 这就是「一个框」。
        //         它左边贴着导航栏边界（76dp 的直边）、右边贴着屏幕边缘，
        //         24dp 的圆角全被裁在屏幕外 ⇒ 看起来「没有圆角」。</li>
        //     <li>这张底图的取样点是 {@code getLocationOnScreen()}（**布局位置**），
        //         而切页动画期间显示列表只录制一次、之后靠 RenderNode 的动画矩阵平移
        //         ⇒ 底图内容被**烤死**在终点位置。于是页面里的控件在滑，
        //         这张底图却像钉住不动 —— 「液态玻璃界面在动但是框不动」。</li>
        //   </ul>
        //
        //   ★ 只排除「全透明 + 几乎铺满整屏」这一种组合：
        //     有可见底色的全屏面板、以及任何正常尺寸的面板（卡片 / 按钮 / 列表项 /
        //     底部导航 / 对话框）一律照旧套，行为不变。
        if (isTransparentBackdrop(bg) && isFullPageContainer(v)) {
            return;
        }

        // ⑤ 已标记过 → 跳过（幂等）
        Object tag = v.getTag(TAG_APPLIED);
        if (tag instanceof String && "v31".equals(tag)) {
            return;
        }

        GlassDrawable glass = new GlassDrawable(v)
                .radiusPx(glassRadius(bg, d, Math.min(v.getWidth(), v.getHeight())))
                .band(bandDp())
                .refrGain(refrGain())
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
    /**
     * 玻璃圆角的下限（dp）。
     *
     * <p>★★★ v4.1：用户报「圆角加多点」。原来完全跟随面板背景的圆角（实测 3~16dp），
     * 大部分面板落在 8~12dp，在整块玻璃上看起来偏「方」，缺少玻璃该有的圆润感。
     *
     * <p>这里取「面板真实圆角」与「24dp」中的**较大者** —— 只放大、不缩小：
     * 原本就是 16dp 的面板会变成 24dp，原本 3dp 的小按钮也会变圆一些。
     * 不直接写死 24dp 是因为某些面板本身是直角（{@code radius == 0} 是有效答案），
     * 硬盖上去会让玻璃轮廓跑到面板外面。
     */
    private static final float GLASS_RADIUS_DP = 24f;

    /**
     * 玻璃实际使用的圆角（像素）：跟随面板真实圆角，但**不低于 24dp**，且不超过短边的 28%。
     *
     * <p>★ 短边 28% 这个上限是必须的：圆角超过短边一半就成了胶囊，
     * 一条 40dp 高的按钮会被圆成药丸，和它原本的方形观感冲突。
     */
    private static float glassRadius(Drawable bg, float density, int minSidePx) {
        float floor = GLASS_RADIUS_DP * density;
        float real = radiusOfBackground(bg, floor);
        float r = Math.max(real, floor);
        float cap = minSidePx > 0 ? minSidePx * 0.28f : r;
        return Math.min(r, cap);
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

    /**
     * ★★★ v4.4：背景是不是「完全透明」—— 也就是画上去什么都看不见。
     *
     * <p>为什么要单独判：{@code @color/launcher_ui_background} 是 {@code #00000000}，
     * 它会被 inflate 成一个 alpha=0 的 {@link android.graphics.drawable.ColorDrawable}，
     * 既不是 {@code null} 也不是图片 ⇒ 老的判断全放过去了，
     * 于是「什么都没有」的页面根被当成一块面板套上了玻璃（见 {@link #maybeApply} ④）。
     *
     * <p>★ 只认 {@code ColorDrawable}：那是 {@code @color/...} 的落地类型，
     * 也正是页面根用的那种。别的类型 {@code getAlpha()} 语义不可靠
     * （例如 {@code GradientDrawable} 返回的是 setAlpha 设的值，不是颜色的 alpha），
     * 猜错会把本该套玻璃的面板排除掉，宁可漏判。
     */
    private static boolean isTransparentBackdrop(Drawable bg) {
        try {
            if (bg instanceof android.graphics.drawable.ColorDrawable) {
                int c = ((android.graphics.drawable.ColorDrawable) bg).getColor();
                return android.graphics.Color.alpha(c) == 0;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * ★★★ v4.4：这个 View 是不是「整页容器」（页面根布局 / 整页子页）。
     *
     * <h3>为什么不能用「面积 ≥ 屏幕 92%」来判</h3>
     * 页面根挂在 {@code launcher_layout} 里，而后者有 {@code paddingStart="76dp"}（竖排导航栏），
     * 于是页面根的宽度只有屏宽的 <b>约 79%</b>、面积约 79% —— 用 92% 根本命不中，
     * 又不敢往下降到 70%（那会开始误伤大对话框）。
     *
     * <h3>改用「撑满高度 + 够宽」</h3>
     * 页面根的特征是**高度等于整屏**：它没有任何上/下留白。
     * 而卡片、按钮、列表项、底部导航、对话框都不可能占满屏高。
     *
     * <p>★ 阈值取 78% / 60%，不用更紧的值：页面根被状态栏、导航栏、
     * 以及 {@code launcher_layout} 的 padding 吃掉一部分后，
     * 实测只占屏高 80%~95%（看设备是否沉浸式），阈值卡太高会漏判 ——
     * 漏判就等于这个 bug 还在。
     * <p>★ 也不会误伤：走到这一步的前提是「背景**完全透明**」，
     * 而真正的面板（卡片 / 按钮 / 对话框）都有可见底色，早在上一道判断就被分开了。
     * 透明背景的对话框本来就看不见，不存在「套不套玻璃」的问题。
     */
    private static boolean isFullPageContainer(View v) {
        try {
            android.util.DisplayMetrics dm = v.getResources().getDisplayMetrics();
            int sw = dm.widthPixels;
            int sh = dm.heightPixels;
            if (sw <= 0 || sh <= 0) {
                return false;
            }
            return (long) v.getHeight() * 100L >= (long) sh * 78L
                    && (long) v.getWidth() * 100L >= (long) sw * 60L;
        } catch (Throwable ignored) {
            return false;
        }
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
