package com.qcl.launcher.launcher.uis.plugin;

import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.qcl.launcher.launcher.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版：多语言切换。
 *
 * <p><b>为什么单独一个类</b>：语言不是「插件」，它是全局设置。放在这里只是因为它需要
 * 一个入口，而插件页正好有一套现成的界面工具箱（{@link PluginUiKit}）——
 * 为它单独改设置页的布局 + UIManager 注册，代价比收益大。
 *
 * <p><b>怎么生效</b>：{@link AppCompatDelegate#setApplicationLocales} 是 AppCompat 1.6
 * 起提供的**每应用语言**能力 —— 不需要改系统语言，也不需要重启进程
 * （AppCompat 会自己重建 Activity）。QCL 用的正是 appcompat 1.6.1，所以这条路可用。
 *
 * <p>★ 诚实说明：**只有翻译过的字符串会变**。没翻的会自动回退到英文（Android 的资源回退），
 * 所以切换到某些语言时会看到「部分本地化」——这是正常的，也是标准做法。
 */
public final class PluginLocale {

    private PluginLocale() {
    }

    /** 语言标签 → 显示名。标签要和 values-<tag> 目录对得上。 */
    private static final String[][] LOCALES = {
            {"", "跟随系统 / System"},
            {"zh", "简体中文"},
            {"zh-rTW", "繁體中文"},
            {"en", "English"},
            {"ja", "日本語"},
            {"ko", "한국어"},
            {"fr", "Français"},
            {"de", "Deutsch"},
            {"es", "Español"},
            {"it", "Italiano"},
            {"ru", "Русский"},
            {"ar", "العربية"},
            {"th", "ไทย"},
            {"vi", "Tiếng Việt"},
            {"id", "Bahasa Indonesia"},
            {"tr", "Türkçe"},
            {"pl", "Polski"},
            {"pt-rBR", "Português (Brasil)"},
            {"sr", "Српски"},
    };

    /** 打开语言选择。 */
    public static void open(MainActivity a) {
        try {
            String cur = currentTag();
            List<String> names = new ArrayList<>();
            for (String[] l : LOCALES) {
                names.add((l[0].equals(cur) ? "● " : "　 ") + l[1]);
            }

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.kv(a, "当前语言", cur.isEmpty() ? "跟随系统" : cur));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "点下面的语言即可切换 —— **不用重启启动器**。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "已支持 " + (LOCALES.length - 1) + " 种语言"));
            col.addView(PluginUiKit.sub(a,
                    "简体中文 · 繁體中文 · English · 日本語 · 한국어 · Français · Deutsch ·\n"
                            + "Español · Italiano · Русский · العربية · ไทย · Tiếng Việt ·\n"
                            + "Bahasa Indonesia · Türkçe · Polski"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ **只有翻译过的字符串会变**。"
                    + "没翻的会自动回退到英文 —— 所以切到某些语言时是「部分本地化」，"
                    + "这是 Android 资源回退的正常行为，不是坏了。"));
            col.addView(PluginUiKit.sub(a, "★ 阿拉伯语会**自动变成从右到左**的排版"
                    + "（manifest 里已开 supportsRtl）。"));
            col.addView(PluginUiKit.sub(a, "★ 语言选择存在系统里，卸载重装会重置。"));

            // ★ 直接弹选择列表 —— 原来先弹一个说明框再弹列表，会互相顶掉（同一时刻只留一个对话框）。
            //   说明文字放进列表上方，点语言即可切换。
            PluginUiKit.list(a, "语言 / Language　（● = 当前）", names,
                    idx -> apply(a, LOCALES[idx][0]));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "语言设置失败：" + t);
        }
    }

    private static void apply(MainActivity a, String tag) {
        try {
            if (tag == null || tag.isEmpty()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                PluginUiKit.toast(a, "已改为跟随系统");
            } else {
                AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(tag));
                PluginUiKit.toast(a, "已切换语言");
            }
        } catch (Throwable t) {
            PluginUiKit.toast(a, "切换失败：" + t);
        }
    }

    /** 当前生效的语言标签（空 = 跟随系统）。 */
    private static String currentTag() {
        try {
            LocaleListCompat l = AppCompatDelegate.getApplicationLocales();
            if (l == null || l.isEmpty()) {
                return "";
            }
            android.os.LocaleList sys = android.os.LocaleList.getDefault();
            String tag = l.toLanguageTags();
            // AppCompat 在「跟随系统」时也可能返回系统语言 —— 这里不区分，直接显示
            return tag;
        } catch (Throwable t) {
            return "";
        }
    }
}
