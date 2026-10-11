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
        // ★★★ 这条已经**做完了** —— 原来的「做不到」说明必须改掉 ✗
        //   功能已实现而文案还写着做不到，是最误导人的情况。
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "深浅色自动切换"));
            col.addView(PluginUiKit.sub(a, "★ 这个功能**已经做了** ✓ 入口在隔壁："
                    + "「界面 → 深浅色模式」。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "实现方式"));
            col.addView(PluginUiKit.sub(a, "· values/colors.xml 里建了 13 个**语义色**"
                    + "（正文/面板/强调/链接…）"));
            col.addView(PluginUiKit.sub(a, "· values-night/colors.xml 给同名的 13 个色配深色值"));
            col.addView(PluginUiKit.sub(a, "· values-night/themes.xml 的 parent 换成深色主题"));
            col.addView(PluginUiKit.sub(a, "· UiPalette 把语义色读成静态字段，插件代码只引用字段"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "★ 一条重要的设计约束"));
            col.addView(PluginUiKit.sub(a, "所有语义色的**日间值都与被替换的原值完全相同** ✓ —— "
                    + "所以转换后日间外观零变化 ✓ 只有夜间才不同 ✓ "
                    + "这条保证了「深色模式做一半也不会把浅色改坏」✓"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 还剩一步：**逐页目视验证**（看哪些地方对比度不够）—— "
                    + "这一步只能靠人眼，建议切到深色后把各页面翻一遍。"));
            PluginUiKit.sheet(a, "深浅色自动切换", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    public static void highContrast(MainActivity a) {
        // ★★★ 条件变了：语义色建好之后，高对比度**技术上可行了** ✓
        //   但只能覆盖「用语义色的页面」= 插件页 ✓
        //   上游老页面用的是资源引用（@color/xxx），要覆盖它们得再逐个改 ✗
        //   —— 如实说明，不假装能全app生效 ✗
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "高对比度模式"));
            col.addView(PluginUiKit.sub(a, "★ **还没做**，但和之前不一样了 —— "
                    + "**技术上可行了** ✓"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "为什么现在可行"));
            col.addView(PluginUiKit.sub(a, "深色模式那轮建的**语义色机制**正好能复用 ✓ —— "
                    + "给同一批语义色再配一套「高对比度值」（正文/背景对比度 ≥ 7:1）就行。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "★ 但有个前提要说清"));
            col.addView(PluginUiKit.sub(a, "只能覆盖**用了语义色的页面**（插件页 ✓）。"
                    + "上游老页面用的是资源引用（@color/colorPureBlack 这种），"
                    + "要给它们上高对比度得再逐个改 —— 那是深色模式那一轮的工作量 ✗"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 所以现在做的话会得到「插件页高对比、老页面不变」"
                    + "的半成品 ✗ —— 要做就等深色模式完全验证完，一起做 ✓"));
            PluginUiKit.sheet(a, "高对比度模式", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
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
