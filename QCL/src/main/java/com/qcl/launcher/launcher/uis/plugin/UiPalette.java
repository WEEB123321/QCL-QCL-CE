package com.qcl.launcher.launcher.uis.plugin;

import android.content.Context;

import com.qcl.launcher.R;

/**
 * ★★★ 社区版：语义色调色板（深色模式地基）。
 *
 * <p><b>为什么要有这个类</b>：插件代码里原来到处写死颜色值
 * （{@code UiPalette.SURFACE} 这种），换主题时这些值**不会跟着变** ✗，
 * 结果就是「一半深一半浅」。
 *
 * <p>现在插件代码只引用这里的<b>语义字段</b>，而字段值来自
 * {@code R.color.qcl_s_*} —— 那两个资源在 {@code values/} 和
 * {@code values-night/} 里各有一套值，系统切深色时自动换 ✓
 *
 * <p>★ <b>必须在界面创建时重新 init</b>：Application.onCreate 只在进程启动跑一次，
 * 而主题变化会重建 Activity 但**不会**重跑 Application ✗
 * 所以 MainActivity.onCreate 里也要调一次，保证拿到的是当前主题的值 ✓
 */
public final class UiPalette {

    private UiPalette() {
    }

    /** 页面背景 */
    public static int BG;
    /** 面板 / 卡片底 */
    public static int SURFACE;
    /** 更亮的卡片（弹窗内） */
    public static int SURFACE_ALT;
    /** 正文 */
    public static int TEXT;
    /** 次要文字 / 说明 */
    public static int TEXT_SUB;
    /** 强调色（铁锈红） */
    public static int ACCENT;
    /** 分隔线 */
    public static int DIVIDER;

    /** 纯白 —— 强调色按钮上的文字，**不随主题变**（深色下也是白字压深红） */
    public static final int ON_ACCENT = 0xFFFFFFFF;

    private static volatile boolean ready;

    public static void init(Context c) {
        if (c == null) {
            return;
        }
        try {
            BG = c.getColor(R.color.qcl_s_bg);
            SURFACE = c.getColor(R.color.qcl_s_surface);
            SURFACE_ALT = c.getColor(R.color.qcl_s_surface_alt);
            TEXT = c.getColor(R.color.qcl_s_text);
            TEXT_SUB = c.getColor(R.color.qcl_s_text_sub);
            ACCENT = c.getColor(R.color.qcl_s_accent);
            DIVIDER = c.getColor(R.color.qcl_s_divider);
            ready = true;
        } catch (Throwable t) {
            // 拿不到资源就用浅色兜底 —— 宁可颜色不对，也不要 NPE 崩界面
            if (!ready) {
                BG = 0xFFEDE7DE;
                SURFACE = 0xFFF7F4EF;
                SURFACE_ALT = 0xFFFFFFFF;
                TEXT = 0xFF3A342C;
                TEXT_SUB = 0xFF8A8378;
                ACCENT = 0xFFA8452F;
                DIVIDER = 0xFFDED7CC;
                ready = true;
            }
        }
    }
}
