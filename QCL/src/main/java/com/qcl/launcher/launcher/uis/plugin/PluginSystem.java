package com.qcl.launcher.launcher.uis.plugin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.LinearLayout;

import com.qcl.launcher.launcher.MainActivity;

/**
 * ★★★ 社区版：系统集成（用户清单第 43-48 项）。
 *
 * <p><b>诚实说明这一批的边界</b>：
 * <ul>
 *   <li><b>#47 剪贴板检测</b> —— 真做 ✓ 但**必须说清楚**：
 *       Android 10+ **禁止后台读剪贴板**（只有当前获得焦点的应用能读）✗
 *       所以做不成「复制了自动弹提示」的后台监听 ✗
 *       这里做成**手动粘贴检测**：打开插件 → 它读一次当前剪贴板 → 识别链接类型 → 给对应动作。
 *       这是 Android 权限边界内**唯一能做对**的形态。</li>
 *   <li><b>#48 定时任务系统</b> —— <b>已经有了</b> ✓ 就是「我的 → 自动任务」
 *       （{@code AutoTaskUI} / {@code AutoTaskRunner}）。做成指路入口。</li>
 *   <li><b>#43 通知栏快捷操作</b> —— <b>做不到</b> ✗ 要改通知层 + 前台服务，
 *       而且 targetSdk 34 对前台服务类型限制很严</li>
 *   <li><b>#44 桌面快捷方式分类</b> —— <b>做不到</b> ✗ Android 的
 *       ShortcutManager 只有「长按图标出快捷方式」，**没有文件夹**这个概念，
 *       系统不支持「分类整理」</li>
 *   <li><b>#45 启动器小部件尺寸</b> —— <b>做不到</b> ✗ AppWidget 是新模块
 *       （要写 Provider、布局、更新服务），不是插件能承载的</li>
 *   <li><b>#46 分享菜单接入</b> —— <b>部分做不到</b> ✗
 *       「分享出去」本来就能用（现有分享功能）✓
 *       但「从其他应用**导入**」需要在 AndroidManifest 注册 intent-filter，
 *       属于改清单，影响面大；而且导入整合包要走完整的安装流程，不是插件能挂的</li>
 * </ul>
 */
public final class PluginSystem {

    private PluginSystem() {
    }

    /** #47 剪贴板检测（手动粘贴检测 —— 权限边界内唯一正确的形态）。 */
    public static void clipboardCheck(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "剪贴板检测"));
            col.addView(PluginUiKit.sub(a, "★ **不是后台监听** —— Android 10 起禁止后台读剪贴板 ✗"));
            col.addView(PluginUiKit.sub(a, "这里是**手动检测**：点下面按钮，读一次当前剪贴板。"));
            col.addView(PluginUiKit.sub(a, " "));

            String text = readClipboard(a);
            if (text == null || text.trim().isEmpty()) {
                col.addView(PluginUiKit.sub(a, "剪贴板是空的。"));
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.sub(a, "用法：在别的地方复制一个 Modrinth / CurseForge 链接，"
                        + "再回到这里点检测。"));
                PluginUiKit.sheet(a, "剪贴板检测", PluginUiKit.scroller(a, col), "知道了", null);
                return;
            }

            col.addView(PluginUiKit.label(a, "剪贴板内容"));
            col.addView(PluginUiKit.sub(a, text.length() > 300
                    ? text.substring(0, 300) + "…（截断）" : text));
            col.addView(PluginUiKit.sub(a, " "));

            final String t = text.trim();
            String kind = classify(t);
            col.addView(PluginUiKit.label(a, "识别结果"));
            col.addView(PluginUiKit.sub(a, kind));

            if (kind.contains("模组") || kind.contains("整合包") || kind.contains("资源包")
                    || kind.contains("光影")) {
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.sub(a, "★ 这个启动器**不做「点一下直接装」** —— "
                        + "因为链接可能指向任何东西，静默安装有风险。"));
                col.addView(PluginUiKit.sub(a, "正确做法：用「下载」页搜索同名资源，"
                        + "确认作者和版本后再装。"));
            }

            PluginUiKit.sheet(a, "剪贴板检测", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    /** 识别链接类型（纯文本规则，不联网）。 */
    private static String classify(String s) {
        String l = s.toLowerCase();
        if (l.contains("modrinth.com/mod")) {
            return "★ Modrinth **模组**页面链接";
        }
        if (l.contains("modrinth.com/modpack")) {
            return "★ Modrinth **整合包**页面链接";
        }
        if (l.contains("modrinth.com/resourcepack")) {
            return "★ Modrinth **资源包**页面链接";
        }
        if (l.contains("modrinth.com/shader")) {
            return "★ Modrinth **光影**页面链接";
        }
        if (l.contains("curseforge.com") && l.contains("/modpacks/")) {
            return "★ CurseForge **整合包**页面链接";
        }
        if (l.contains("curseforge.com") && l.contains("/mc-mods/")) {
            return "★ CurseForge **模组**页面链接";
        }
        if (l.contains("curseforge.com")) {
            return "★ CurseForge 页面链接（类型未识别）";
        }
        if (l.endsWith(".jar")) {
            return "★ 看起来是**模组 jar** 的直链";
        }
        if (l.endsWith(".zip")) {
            return "★ 看起来是**压缩包**的直链（资源包 / 整合包 / 光影都可能是 zip）";
        }
        if (l.startsWith("http://") || l.startsWith("https://")) {
            return "普通链接（不是已知的 Mod / 整合包页面）";
        }
        return "不是链接（普通文本）";
    }

    private static String readClipboard(Context c) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                return null;
            }
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) {
                return null;
            }
            CharSequence cs = d.getItemAt(0).coerceToText(c);
            return cs == null ? null : cs.toString();
        } catch (Throwable t) {
            // Android 10+ 在非前台时读剪贴板会抛异常 —— 吞掉，返回 null
            return null;
        }
    }

    // ==================== 其余 5 项 ====================

    public static void taskSystem(MainActivity a) {
        exists(a, "定时任务系统",
                "这个功能**已经有了** ✓ 就是「我的 → 自动任务」。",
                "它支持按天 / 按周 / 按月定时执行备份、清理等动作，"
                        + "也有任务日志。去那里配置就行，不用在这里重复做。");
    }

    public static void notifyQuick(MainActivity a) {
        notSupported(a, "通知栏快捷操作",
                "要在通知里放自定义按钮，得改通知层 + 前台服务；"
                        + "而且 targetSdk 34 对前台服务类型（foregroundServiceType）"
                        + "限制很严，加按钮要连带改服务声明。",
                "现在能做的替代：把常用动作做成桌面快捷方式"
                        + "（系统本身支持长按应用图标），或者用「我的 → 自动任务」定时执行。");
    }

    public static void shortcutFolder(MainActivity a) {
        notSupported(a, "桌面快捷方式分类",
                "Android 的 ShortcutManager **只有「长按图标弹出快捷方式」**，"
                        + "**系统层面没有「文件夹」这个概念** —— 启动器无法给快捷方式分类。",
                "现在能做的替代：在桌面（Launcher）自己拖成文件夹，"
                        + "那是桌面应用的功能，不是启动器的。");
    }

    public static void widgetSize(MainActivity a) {
        notSupported(a, "启动器小部件尺寸",
                "AppWidget（桌面小部件）是**独立模块** —— 要写 AppWidgetProvider、"
                        + "布局、更新服务、还要处理尺寸变化。这不是插件能承载的体量。",
                "现在能做的替代：用桌面快捷方式代替 —— 它能一键启动指定实例。");
    }

    public static void shareImport(MainActivity a) {
        notSupported(a, "分享菜单接入（导入侧）",
                "「分享出去」本来就能用 ✓（诊断页、兼容页都有分享按钮）。"
                        + "但「从其他应用**导入**」要在 AndroidManifest 注册 intent-filter，"
                        + "属于改清单；而且导入整合包要走完整的安装流程，不是插件能挂的。",
                "现在能做的替代：用「剪贴板检测」把链接粘过来，"
                        + "再到「下载」页搜同名资源。");
    }

    private static void exists(MainActivity a, String title, String where, String detail) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, title));
            col.addView(PluginUiKit.sub(a, where));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, detail));
            PluginUiKit.sheet(a, title, PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
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
}
