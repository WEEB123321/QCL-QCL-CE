package com.qcl.launcher.launcher.uis.plugin;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileOutputStream;

/**
 * ★★★ 社区版：界面与体验（用户清单第 7-14 项）。
 *
 * <p><b>先说清楚：这 8 项里只有 2 项能真做。</b>
 * 「界面与体验」听起来都是小功能，但深色模式、高对比度、单手模式
 * 本质上是**整套主题 / 布局改造**，不是插件能承载的体量 ✗
 *
 * <ul>
 *   <li><b>#11 字体大小调节</b> —— 真做 ✓ 全局 fontScale，立即生效（重建 Activity）</li>
 *   <li><b>#14 横屏/竖屏锁定</b> —— 真做（部分）✓ 能锁横屏；
 *       <b>锁竖屏会很难看</b> ✗ 因为这个应用**没有竖屏布局**（全工程没有 layout-port）</li>
 *   <li><b>#7 底部导航自定义</b> —— <b>做不到</b> ✗ 导航项和页面是一一绑定的，
 *       隐藏某一项等于把那个页面变成进不去的孤岛</li>
 *   <li><b>#8 快捷手势</b> —— <b>做不到</b> ✗ 要接管整个 Activity 的触摸分发，
 *       会和列表滚动、侧栏滑动、游戏控制冲突</li>
 *   <li><b>#9 搜索历史与收藏</b> —— <b>只能做一半</b> ✗
 *       能存「手动保存的搜索词」，但**没法自动记录**你在下载页搜过什么
 *       （那个搜索框不经过插件层）</li>
 *   <li><b>#10 深浅色自动切换</b> —— <b>做不到</b> ✗ 这个应用**只有浅色一套视觉**
 *       （颜色全写在 values/colors.xml，没有 values-night 主题）✗
 *       做深色模式 = 重做整套配色 + 逐个页面适配</li>
 *   <li><b>#12 高对比度模式</b> —— <b>做不到</b> ✗ 同上，要另一套配色方案</li>
 *   <li><b>#13 单手模式</b> —— <b>做不到</b> ✗ 要重排布局（把操作区挪到底部），
 *       涉及每个页面的根布局</li>
 * </ul>
 */
public final class PluginUiPrefs {

    private PluginUiPrefs() {
    }

    private static final String FILE = "qcl_ui_prefs.txt";

    // ==================== #11 字体大小 ====================

    public static void fontSize(MainActivity a) {
        try {
            float cur = a.getResources().getConfiguration().fontScale;
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "字体大小"));
            col.addView(PluginUiKit.kv(a, "当前缩放", String.format(java.util.Locale.US, "%.2f×", cur)));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 改完**界面会重建一次**（闪一下），这是系统机制，不是崩了。"));
            col.addView(PluginUiKit.sub(a, "★ 只影响启动器自己的界面，不影响游戏内字体。"));
            col.addView(PluginUiKit.sub(a, " "));

            String[] labels = {"最小 0.85×", "小 0.95×", "标准 1.00×", "大 1.15×", "最大 1.30×"};
            final float[] scales = {0.85f, 0.95f, 1.00f, 1.15f, 1.30f};
            for (int i = 0; i < labels.length; i++) {
                final float sc = scales[i];
                TextView row = PluginUiKit.row(a, labels[i], null);
                row.setGravity(android.view.Gravity.CENTER);
                if (Math.abs(sc - cur) < 0.02f) {
                    row.setText("● " + labels[i]);
                }
                PluginUiKit.pressEffect(row);
                row.setOnClickListener(v -> applyFontScale(a, sc));
                col.addView(row);
            }

            PluginUiKit.sheet(a, "字体大小", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    private static void applyFontScale(MainActivity a, float scale) {
        try {
            save("fontScale", Float.toString(scale));
            Configuration c = new Configuration(a.getResources().getConfiguration());
            c.fontScale = scale;
            a.getResources().updateConfiguration(c, a.getResources().getDisplayMetrics());
            PluginUiKit.log(a, "字体缩放 → " + scale);
            a.recreate();
        } catch (Throwable t) {
            PluginUiKit.toast(a, "设置失败：" + t);
        }
    }

    // ==================== 主题模式（深色模式开关） ====================

    /** 主题模式：0=跟随系统 1=始终浅色 2=始终深色。 */
    public static int readThemeMode() {
        try {
            File f = prefs();
            if (!f.isFile()) {
                return 0;
            }
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                //noinspection ResultOfMethodCallIgnored
                in.read(b);
            }
            for (String line : new String(b, "UTF-8").split("\n")) {
                String t = line.trim();
                if (t.startsWith("themeMode=")) {
                    return Integer.parseInt(t.substring("themeMode=".length()).trim());
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /** 启动时应用主题模式（在 Application.onCreate 里调）。 */
    public static void applyThemeMode() {
        try {
            int m = readThemeMode();
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    m == 2 ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                            : m == 1 ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                            : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } catch (Throwable ignored) {
        }
    }

    public static void themeMode(MainActivity a) {
        try {
            final int cur = readThemeMode();
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "深浅色模式"));
            col.addView(PluginUiKit.sub(a, "★ 现在可以在这里**直接切**，不用去改系统设置 —— "
                    + "方便你看深色下哪些地方不对。"));
            col.addView(PluginUiKit.sub(a, "★ 深色模式**还在完善中**：插件页已经跟着变了，"
                    + "但上游的老页面还有一些地方没换成语义色 ✗ 看起来可能「半深半浅」✗"));
            col.addView(PluginUiKit.sub(a, " "));

            String[] labels = {"跟随系统", "始终浅色", "始终深色"};
            final int[] modes = {0, 1, 2};
            for (int i = 0; i < labels.length; i++) {
                final int m = modes[i];
                TextView row = PluginUiKit.row(a, (cur == m ? "● " : "　") + labels[i], null);
                row.setGravity(android.view.Gravity.CENTER);
                PluginUiKit.pressEffect(row);
                row.setOnClickListener(v -> {
                    try {
                        save("themeMode", Integer.toString(m));
                        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                                m == 2 ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                                        : m == 1 ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                                        : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                        PluginUiKit.log(a, "主题模式 → " + labels[m]);
                        PluginUiKit.toast(a, "已设为：" + labels[m]);
                    } catch (Throwable t) {
                        PluginUiKit.toast(a, "切换失败：" + t);
                    }
                });
                col.addView(row);
            }
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 切完界面会重建一次（闪一下），正常。"));
            PluginUiKit.sheet(a, "深浅色模式", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== #14 横竖屏锁定 ====================

    public static void orientation(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "屏幕方向"));
            col.addView(PluginUiKit.sub(a, "★ 这个应用**本来是横屏专用**（manifest 里写的是 sensorLandscape）。"));
            col.addView(PluginUiKit.sub(a, "★ **锁竖屏会很难看** ✗ —— 全工程没有竖屏布局（layout-port），"
                    + "竖过来会挤成一团。所以这里只提供「横屏」和「跟随系统」两个选项。"));
            col.addView(PluginUiKit.sub(a, " "));

            TextView land = PluginUiKit.row(a, "锁定横屏（推荐）", null);
            land.setGravity(android.view.Gravity.CENTER);
            PluginUiKit.pressEffect(land);
            land.setOnClickListener(v -> setOri(a, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, "横屏"));
            col.addView(land);

            TextView auto = PluginUiKit.row(a, "跟随系统（可竖可横）", null);
            auto.setGravity(android.view.Gravity.CENTER);
            PluginUiKit.pressEffect(auto);
            auto.setOnClickListener(v -> setOri(a, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, "跟随系统"));
            col.addView(auto);

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 「跟随系统」= 允许竖屏，但**布局不会适配** —— "
                    + "想真正支持竖屏，需要为每个页面做竖屏布局，那是大工程 ✗"));

            PluginUiKit.sheet(a, "屏幕方向", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    private static void setOri(MainActivity a, int o, String label) {
        try {
            save("orientation", Integer.toString(o));
            a.setRequestedOrientation(o);
            PluginUiKit.log(a, "屏幕方向 → " + label);
            PluginUiKit.toast(a, "已设为：" + label);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "设置失败：" + t);
        }
    }

    // ==================== 其余 6 项：如实说明 ====================

    public static void navCustom(MainActivity a) {
        notSupported(a, "底部导航自定义",
                "导航项和页面是**一一绑定**的 —— 隐藏「下载」就等于把下载页变成进不去的孤岛 ✗。"
                        + "而且导航在侧栏，不在底部（文件名 view_bottom_nav 是旧设计遗留 ✗）。",
                "★ 真正能自定义的是**功能菜单** —— 用「菜单排版」（在插件页顶部菜单里）"
                        + "可以勾选显示哪些入口、调顺序 ✓");
    }

    public static void gestures(MainActivity a) {
        notSupported(a, "快捷手势",
                "要接管整个 Activity 的触摸分发 ✗ —— 会和列表滚动、侧栏滑动、"
                        + "以及游戏里的控制层（触屏手柄）冲突，属于改交互框架。",
                "现在能做的替代：用系统的手势导航 + 长按应用图标出快捷方式。");
    }

    public static void searchHistory(MainActivity a) {
        notSupported(a, "搜索历史与收藏（自动记录）",
                "能存「手动保存的搜索词」✓，但**没法自动记录**你在下载页搜过什么 ✗ —— "
                        + "那些搜索框不经过插件层，插件拿不到输入内容。",
                "★ 现在能用的：**内置插件页的搜索框已经能搜 81 个插件** ✓；"
                        + "下载页的搜索本来就有输入记录（系统输入法提供）。");
    }

    public static void darkMode(MainActivity a) {
        notSupported(a, "深浅色自动切换",
                "这个应用**只有浅色一套视觉** ✗ —— 颜色全写在 values/colors.xml，"
                        + "**没有 values-night 主题**，也没有第二套 drawable。"
                        + "做深色模式 = 重做整套配色 + 逐个页面适配，"
                        + "这是**一个新阶段的工作量**，不是插件能承载的。",
                "★ 这不是「不做」，是**做不了** ✗ —— 硬做一个假的「深色模式」开关"
                        + "（点了没变化）比不做更糟。"
                        + "要做的话应该作为独立任务：先建 values-night/colors.xml，"
                        + "再逐页验证对比度。");
    }

    public static void highContrast(MainActivity a) {
        notSupported(a, "高对比度模式",
                "和深色模式同一个原因 ✗ —— 高对比度需要**另一套配色方案**"
                        + "（正文/背景对比度 ≥ 7:1，还要给色盲用户换掉红绿区分）。",
                "★ 同样不是「不做」而是「做不了」✗。"
                        + "如果要做，建议和深色模式一起作为「主题系统」任务做。");
    }

    public static void oneHand(MainActivity a) {
        notSupported(a, "单手模式",
                "要**重排布局** ✗ —— 把操作区从顶部挪到底部、放大按钮，"
                        + "涉及每个页面的根布局和尺寸常量（dimen.xml）。",
                "★ 而且这是**横屏**应用 —— 横屏时双手握持本来就是主流姿势，"
                        + "单手模式在横屏下意义不大。");
    }

    private static void notSupported(MainActivity a, String title, String why, String alt) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, title));
            col.addView(PluginUiKit.sub(a, "★ 这个功能**没有实现**，原因如下："));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, why));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, alt));
            PluginUiKit.sheet(a, title, PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== 持久化 ====================

    private static File prefs() {
        return new File(AppManifest.SETTING_DIR, FILE);
    }

    private static void save(String k, String v) {
        try {
            StringBuilder sb = new StringBuilder();
            String old = "";
            File f = prefs();
            if (f.isFile()) {
                byte[] b = new byte[(int) f.length()];
                try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                    //noinspection ResultOfMethodCallIgnored
                    in.read(b);
                }
                old = new String(b, "UTF-8");
            }
            boolean replaced = false;
            for (String line : old.split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.startsWith(k + "=")) {
                    sb.append(k).append('=').append(v).append('\n');
                    replaced = true;
                } else {
                    sb.append(t).append('\n');
                }
            }
            if (!replaced) {
                sb.append(k).append('=').append(v).append('\n');
            }
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(sb.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }
}
