package com.qcl.launcher.launcher.perf;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * ★ 社区版新增：游戏内内存悬浮窗。
 *
 * <p><b>★★★ 为什么这个悬浮窗只报内存，不报 FPS</b>
 * 本工程<b>没有任何现成的帧率数据源</b>（全仓 {@code fps} 零命中）。Java 层能拿到的
 * {@code Choreographer} 帧回调测的是 <b>Android UI 线程的 vsync 节奏</b>，
 * 而 Minecraft 的画面是渲染线程 / GL 线程在出，两者根本不是一回事 ——
 * 把 UI 线程的 vsync 当「游戏 FPS」显示出来，就是一个看着很专业、实际全是假数据的数字。
 * 所以这里<b>不做</b> FPS。要真 FPS 得在原生层挂 swap buffers 钩子，或让渲染器主动上报，
 * 那是另一件事。
 *
 * <p><b>★ 为什么内存才是这个项目真正要盯的量</b>
 * README 明确写了「按剩余内存动态夹取堆」—— 也就是 JVM 堆上限是从设备剩余内存推算出来的。
 * 玩家遇到「进不去 / 卡顿 / 崩」时，第一个要回答的问题就是「堆用了多少、还剩多少」。
 * 这里把四个量一次给全：
 * <ul>
 *   <li><b>Java 堆</b>：{@code Runtime} 的 used / max —— 游戏真正会 OOM 的地方；</li>
 *   <li><b>Native 堆</b>：{@code Debug.getNativeHeapAllocatedSize} —— LWJGL/渲染器的 C 侧分配；</li>
 *   <li><b>进程 PSS</b>：{@code Debug.MemoryInfo.totalPss} —— 系统眼里这个应用占了多少物理内存；</li>
 *   <li><b>系统可用</b>：{@code ActivityManager.MemoryInfo} —— 整机还剩多少，决定堆能开多大。</li>
 * </ul>
 *
 * <p><b>★ 更新循环刻意不产生垃圾</b>：每秒一次、复用同一个 {@link StringBuilder} 拼接，
 * 不用 {@code String.format}（它会 new 一个 Formatter，还要拆箱一堆 Integer）。
 * 一个监控工具如果自己持续制造分配，就会污染它正在测的东西 ——
 * 每秒一个几百字节的短字符串是唯一不可避免的分配，量级上完全可以忽略。
 */
public class MemoryOverlayWindow {

    private static final long REFRESH_INTERVAL_MS = 1000L;

    private final Activity activity;
    private final ViewGroup parent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 复用，避免每秒产生新对象 */
    private final StringBuilder sb = new StringBuilder(256);

    private View panel;
    private TextView statsView;
    private TextView closeBtn;
    private FrameLayout.LayoutParams panelLp;
    private boolean attached;
    private volatile boolean closed;

    private static MemoryOverlayWindow current;

    public MemoryOverlayWindow(Activity activity, ViewGroup parent) {
        this.activity = activity;
        this.parent = parent;
    }

    public static boolean isShowing() {
        MemoryOverlayWindow w = current;
        return w != null && w.attached;
    }

    /** 显示（已在显示则忽略，不会叠加出两个）。 */
    public static void showFor(Activity activity, ViewGroup parent) {
        if (isShowing()) {
            return;
        }
        new MemoryOverlayWindow(activity, parent).show();
    }

    public static void closeCurrentIfAny() {
        MemoryOverlayWindow w = current;
        if (w != null) {
            w.close();
        }
    }

    public void show() {
        if (this.attached) {
            return;
        }
        current = this;
        this.mainHandler.post(this::attach);
    }

    public void close() {
        this.closed = true;
        if (current == this) {
            current = null;
        }
        this.mainHandler.removeCallbacks(this.tick);
        this.mainHandler.post(() -> {
            if (this.panel != null && this.panel.getParent() instanceof ViewGroup) {
                ((ViewGroup) this.panel.getParent()).removeView(this.panel);
            }
            this.panel = null;
            this.attached = false;
        });
    }

    // ------------------------------------------------------------------ 刷新循环

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (MemoryOverlayWindow.this.closed || !MemoryOverlayWindow.this.attached) {
                return;
            }
            MemoryOverlayWindow.this.refresh();
            MemoryOverlayWindow.this.mainHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @SuppressLint("SetTextI18n")
    private void refresh() {
        if (this.statsView == null) {
            return;
        }
        try {
            // ① Java 堆
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            long max = rt.maxMemory();
            int usedMb = (int) (used / 1048576L);
            int maxMb = (int) (max / 1048576L);
            int pct = max > 0L ? (int) (used * 100L / max) : 0;

            // ② Native 堆（C 侧分配，LWJGL / 渲染器都在这里）
            int nativeMb = (int) (Debug.getNativeHeapAllocatedSize() / 1048576L);

            // ③ 进程 PSS（系统视角的物理内存占用）
            Debug.MemoryInfo mi = new Debug.MemoryInfo();
            Debug.getMemoryInfo(mi);
            int pssMb = mi.getTotalPss() / 1024;

            // ④ 整机可用内存
            ActivityManager am = (ActivityManager) this.activity.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo sysInfo = new ActivityManager.MemoryInfo();
            int availMb = -1;
            int totalMb = -1;
            if (am != null) {
                am.getMemoryInfo(sysInfo);
                availMb = (int) (sysInfo.availMem / 1048576L);
                totalMb = (int) (sysInfo.totalMem / 1048576L);
            }

            this.sb.setLength(0);
            this.sb.append("Java 堆  ").append(usedMb).append(" / ").append(maxMb).append(" MB  (")
                    .append(pct).append("%)")
                    .append('\n').append("Native 堆 ").append(nativeMb).append(" MB")
                    .append('\n').append("进程 PSS  ").append(pssMb).append(" MB");
            if (availMb >= 0) {
                this.sb.append('\n').append("整机可用  ").append(availMb).append(" / ").append(totalMb).append(" MB");
            }
            this.statsView.setText(this.sb.toString());
            // 堆吃紧时变红 —— 这是这个悬浮窗唯一需要「一眼看出」的信息
            this.statsView.setTextColor(pct >= 90 ? 0xFFFF6B6B : (pct >= 70 ? 0xFFFFD166 : 0xFFCFD8DC));
        } catch (Throwable t) {
            // 监控工具绝不能因为读数失败而把游戏带崩
            this.statsView.setText("读取内存信息失败");
        }
    }

    // ------------------------------------------------------------------ 界面

    private void attach() {
        if (this.attached || this.closed) {
            return;
        }
        int pad = dp(8);

        FrameLayout box = new FrameLayout(this.activity);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC000000);          // 半透明黑，盖在游戏画面上也能看清
        bg.setCornerRadius(dp(6));
        box.setBackground(bg);
        box.setPadding(pad, pad, pad, pad);

        LinearLayout column = new LinearLayout(this.activity);
        column.setOrientation(LinearLayout.VERTICAL);
        box.addView(column, new FrameLayout.LayoutParams(-1, -1));

        // 标题行（拖动手柄 + 关闭）
        LinearLayout header = new LinearLayout(this.activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this.activity);
        title.setText("≡ 内存");
        title.setTextColor(0xFF9E9E9E);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        this.closeBtn = new TextView(this.activity);
        this.closeBtn.setText("×");
        this.closeBtn.setTextColor(0xFFFFFFFF);
        this.closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        this.closeBtn.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(0x33FFFFFF);
        closeBg.setStroke(dp(1), 0x55FFFFFF);
        this.closeBtn.setBackground(closeBg);
        this.closeBtn.setClickable(true);
        this.closeBtn.setFocusable(true);
        this.closeBtn.setOnClickListener(v -> close());
        int closeSize = dp(20);
        header.addView(this.closeBtn, new LinearLayout.LayoutParams(closeSize, closeSize));

        column.addView(header, new LinearLayout.LayoutParams(-1, -2));

        this.statsView = new TextView(this.activity);
        this.statsView.setTextColor(0xFFCFD8DC);
        this.statsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        this.statsView.setLineSpacing(dp(1), 1f);
        column.addView(this.statsView, new LinearLayout.LayoutParams(-2, -2));

        this.panelLp = new FrameLayout.LayoutParams(dp(190), -2, Gravity.TOP | Gravity.START);
        this.panelLp.leftMargin = dp(8);
        this.panelLp.topMargin = dp(8);
        box.setLayoutParams(this.panelLp);

        // 拖动：整块面板都能拖（比只有标题栏好按），但落在关闭按钮上时不拖
        box.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startLeft;
            private int startTop;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (closeBtn != null && isTouchInside(closeBtn, event)) {
                            return false;   // 交给关闭按钮自己处理
                        }
                        this.downRawX = event.getRawX();
                        this.downRawY = event.getRawY();
                        this.startLeft = panelLp.leftMargin;
                        this.startTop = panelLp.topMargin;
                        this.dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - this.downRawX);
                        int dy = (int) (event.getRawY() - this.downRawY);
                        if (!this.dragging
                                && Math.abs(dx) + Math.abs(dy) < dp(6)) {
                            return true;   // 抖动阈值：避免轻微误触就把面板挪走
                        }
                        this.dragging = true;
                        int maxLeft = Math.max(0, parent.getWidth() - panel.getWidth());
                        int maxTop = Math.max(0, parent.getHeight() - panel.getHeight());
                        panelLp.leftMargin = Math.max(0, Math.min(maxLeft, this.startLeft + dx));
                        panelLp.topMargin = Math.max(0, Math.min(maxTop, this.startTop + dy));
                        panel.setLayoutParams(panelLp);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return false;
                }
            }
        });

        this.parent.addView(box);
        this.panel = box;
        this.attached = true;
        refresh();
        this.mainHandler.postDelayed(this.tick, REFRESH_INTERVAL_MS);
    }

    private boolean isTouchInside(View v, MotionEvent event) {
        try {
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            float x = event.getRawX();
            float y = event.getRawY();
            return x >= loc[0] && x <= loc[0] + v.getWidth()
                    && y >= loc[1] && y <= loc[1] + v.getHeight();
        } catch (Throwable t) {
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources().getDisplayMetrics().density);
    }
}
