package com.qcl.launcher.launcher.ui;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.qcl.launcher.launcher.setting.launcher.LauncherSetting;

/**
 * ★★★ 社区版新增：安全区（刘海 / 挖孔 / 大圆角）自动避让。
 *
 * <h3>为什么需要</h3>
 * 主界面是**沉浸式全屏**（{@code LAYOUT_FULLSCREEN | LAYOUT_HIDE_NAVIGATION}，
 * 挖孔模式 SHORT_EDGES），内容从屏幕最顶端开始画。于是：
 * <ul>
 *   <li><b>圆角大的手机</b>：顶部那排入口按钮、底部导航条会被 R 角**切掉一块**；</li>
 *   <li><b>有刘海/挖孔的手机</b>：会被摄像头区域挡住。</li>
 * </ul>
 *
 * <h3>怎么自动检测（★ 全部来自系统真实上报，不猜机型、不写死名单）</h3>
 * <ol>
 *   <li><b>系统栏</b>：{@code getInsetsIgnoringVisibility(systemBars())}（API 30+）
 *       或 {@code getStableInsetTop/Bottom()}（API 28+）。<br>
 *       ★ 用「忽略可见性」的版本是关键 —— 我们隐藏了状态栏/导航栏，
 *       普通的 {@code getInsets()} 会返回 0，那样就永远检测不到需要避让。</li>
 *   <li><b>刘海 / 挖孔</b>：{@code insets.getDisplayCutout()} 的
 *       {@code getSafeInset*}（API 28+）。</li>
 *   <li><b>大圆角</b>：{@code insets.getRoundedCorner(四个角)} 的半径（API 31+）。
 *       系统栏 inset 在「没有刘海、圆角也大」的机型上可能是 0，
 *       这时圆角半径是唯一能反映「内容会被切」的信号。</li>
 * </ol>
 *
 * <h3>取三者的最大值</h3>
 * 三个来源各自覆盖一种情况，取 max 才能同时兜住。圆角按**半径的一半**计入 ——
 * 用整半径会把界面推得太靠里（圆角只影响边缘那一小圈），用 0 又挡不住。
 */
public final class SafeAreaHelper {

    private static final String TAG = "QCLSafeArea";

    /** 「始终留边」模式在自动结果之上额外加的量（dp）—— 给检测不到的机型兜底 */
    private static final int CONSERVATIVE_EXTRA_DP = 12;

    /** 圆角半径小于这个值（dp）就不算「大圆角」，不必为它避让 */
    private static final int BIG_CORNER_DP = 16;

    private SafeAreaHelper() {
    }

    /** 最近一次实际生效的安全区，供「启动诊断」页展示 —— 让用户能自证它真的在工作。 */
    public static volatile String lastApplied = "";

    /**
     * 给主界面装上安全区。
     *
     * @param activity  宿主 Activity（用于取设置与 dp 换算）
     * @param root      根布局（用来接 WindowInsets 回调，本身不加 padding）
     * @param content   内容容器（加 padding；★ 它上面挂了背景图，padding 不会裁掉背景）
     * @param bottomNav 底部导航条（抬升）
     * @param fab       右下角悬浮球（抬升）
     */
    public static void install(final Activity activity, final View root, final View content,
                               final View bottomNav, final View fab) {
        if (root == null || content == null) {
            return;
        }
        // ★ 记下布局里写的原始边距，后面每次都在「原始值 + 安全区」上算，
        //   否则反复触发 inset 回调会把边距越加越大。
        final int baseNavMargin = readBottomMargin(bottomNav);
        final int baseFabMargin = readBottomMargin(fab);
        final int basePadLeft = content.getPaddingLeft();
        final int basePadTop = content.getPaddingTop();
        final int basePadRight = content.getPaddingRight();
        final int basePadBottom = content.getPaddingBottom();

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                try {
                    apply(activity, content, bottomNav, fab, insets,
                            basePadLeft, basePadTop, basePadRight, basePadBottom,
                            baseNavMargin, baseFabMargin);
                } catch (Throwable t) {
                    Log.w(TAG, "应用安全区失败（已忽略）: " + t);
                }
                return insets;
            }
        });
        try {
            root.requestApplyInsets();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ 计算与应用

    private static void apply(Activity activity, View content, View bottomNav, View fab,
                              WindowInsets insets,
                              int basePadLeft, int basePadTop, int basePadRight, int basePadBottom,
                              int baseNavMargin, int baseFabMargin) {

        int mode = readMode(activity);
        if (mode == LauncherSetting.SAFE_AREA_OFF) {
            reset(content, bottomNav, fab, basePadLeft, basePadTop, basePadRight, basePadBottom,
                    baseNavMargin, baseFabMargin);
            lastApplied = "已关闭（内容按沉浸式全屏铺满）";
            return;
        }

        int[] safe = computeSafeInsets(activity, insets);
        int top = safe[0], bottom = safe[1], left = safe[2], right = safe[3];
        int cornerPx = safe[4];

        if (mode == LauncherSetting.SAFE_AREA_ON) {
            int extra = dp(activity, CONSERVATIVE_EXTRA_DP);
            top += extra;
            bottom += extra;
            left += extra;
            right += extra;
        }

        // 四周都算完之后再夹一下，避免把内容挤没（例如某些横屏机型的左右 inset 很大）
        int maxH = content.getWidth() / 3;
        if (maxH > 0) {
            left = Math.min(left, maxH);
            right = Math.min(right, maxH);
        }
        int maxV = content.getHeight() / 3;
        if (maxV > 0) {
            top = Math.min(top, maxV);
            bottom = Math.min(bottom, maxV);
        }

        content.setPadding(basePadLeft + left, basePadTop + top,
                basePadRight + right, basePadBottom + bottom);
        setBottomMargin(bottomNav, baseNavMargin + bottom);
        setBottomMargin(fab, baseFabMargin + bottom);

        lastApplied = describe(activity, top, bottom, left, right, cornerPx, mode);
    }

    private static void reset(View content, View bottomNav, View fab,
                              int l, int t, int r, int b, int navMargin, int fabMargin) {
        content.setPadding(l, t, r, b);
        setBottomMargin(bottomNav, navMargin);
        setBottomMargin(fab, fabMargin);
    }

    /**
     * 算出四边安全区与圆角半径。
     *
     * @return {@code [top, bottom, left, right, cornerRadiusPx]}（单位 px）
     */
    private static int[] computeSafeInsets(Activity activity, WindowInsets insets) {
        int top = 0, bottom = 0, left = 0, right = 0;
        int corner = 0;

        // ★★ 这里刻意把 API 30 / 31 的代码放进**嵌套类**再调用，而不是直接写在
        //   if (SDK_INT >= 30) 里。原因：`android.graphics.Insets`（API 29）和
        //   `RoundedCorner`（API 31）在低版本设备上**根本不存在**，
        //   写在同一个方法体里，ART 在类加载/方法校验阶段就可能抛
        //   NoClassDefFoundError —— 那会让整个监听器失效（甚至把应用带走）。
        //   放进嵌套类后，类只在真正被调用的那一支才会加载。这是 AndroidX 的标准做法。
        if (Build.VERSION.SDK_INT >= 30) {
            int[] sb = Api30.getSystemBarInsets(insets);
            top = sb[0];
            bottom = sb[1];
            left = sb[2];
            right = sb[3];
        } else {
            if (Build.VERSION.SDK_INT >= 28) {
                top = insets.getStableInsetTop();
                bottom = insets.getStableInsetBottom();
                left = insets.getStableInsetLeft();
                right = insets.getStableInsetRight();
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout dc = insets.getDisplayCutout();
                if (dc != null) {
                    top = Math.max(top, dc.getSafeInsetTop());
                    bottom = Math.max(bottom, dc.getSafeInsetBottom());
                    left = Math.max(left, dc.getSafeInsetLeft());
                    right = Math.max(right, dc.getSafeInsetRight());
                }
            }
        }

        // 大圆角：没有刘海、系统栏又很薄的机型，这是唯一的信号
        if (Build.VERSION.SDK_INT >= 31) {
            corner = Api31.maxCornerRadius(insets);
            if (corner >= dp(activity, BIG_CORNER_DP)) {
                int half = corner / 2;
                top = Math.max(top, half);
                bottom = Math.max(bottom, half);
                left = Math.max(left, half);
                right = Math.max(right, half);
            }
        }
        return new int[]{top, bottom, left, right, corner};
    }

    /** 只在 API 30+ 被加载。 */
    private static final class Api30 {
        static int[] getSystemBarInsets(WindowInsets insets) {
            // ★ 忽略可见性：我们隐藏了系统栏，普通 getInsets() 会返回 0。
            Insets sb = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[]{sb.top, sb.bottom, sb.left, sb.right};
        }
    }

    /** 只在 API 31+ 被加载。 */
    private static final class Api31 {
        static int maxCornerRadius(WindowInsets insets) {
            int max = 0;
            int[] positions = {
                    android.view.RoundedCorner.POSITION_TOP_LEFT,
                    android.view.RoundedCorner.POSITION_TOP_RIGHT,
                    android.view.RoundedCorner.POSITION_BOTTOM_LEFT,
                    android.view.RoundedCorner.POSITION_BOTTOM_RIGHT,
            };
            for (int p : positions) {
                try {
                    android.view.RoundedCorner c = insets.getRoundedCorner(p);
                    if (c != null) {
                        max = Math.max(max, c.getRadius());
                    }
                } catch (Throwable ignored) {
                }
            }
            return max;
        }
    }

    // ------------------------------------------------------------------ 工具

    private static int readMode(Activity activity) {
        try {
            com.qcl.launcher.launcher.MainActivity a =
                    (com.qcl.launcher.launcher.MainActivity) activity;
            if (a.launcherSetting != null) {
                int m = a.launcherSetting.safeAreaMode;
                if (m >= 0 && m <= 2) {
                    return m;
                }
            }
        } catch (Throwable ignored) {
        }
        return LauncherSetting.SAFE_AREA_AUTO;
    }

    private static int readBottomMargin(View v) {
        if (v == null) {
            return 0;
        }
        try {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                return ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static void setBottomMargin(View v, int margin) {
        if (v == null) {
            return;
        }
        try {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                if (mlp.bottomMargin != margin) {
                    mlp.bottomMargin = margin;
                    v.setLayoutParams(mlp);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    private static int toDp(Activity a, int px) {
        return Math.round(px / a.getResources().getDisplayMetrics().density);
    }

    private static String describe(Activity a, int top, int bottom, int left, int right,
                                   int cornerPx, int mode) {
        StringBuilder sb = new StringBuilder();
        sb.append(mode == LauncherSetting.SAFE_AREA_ON ? "始终留边" : "自动");
        sb.append("：上 ").append(toDp(a, top)).append("dp")
          .append(" / 下 ").append(toDp(a, bottom)).append("dp")
          .append(" / 左 ").append(toDp(a, left)).append("dp")
          .append(" / 右 ").append(toDp(a, right)).append("dp");
        if (cornerPx > 0) {
            sb.append("（圆角半径 ").append(toDp(a, cornerPx)).append("dp）");
        }
        return sb.toString();
    }
}
