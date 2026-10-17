package com.qcl.launcher.launcher.uis.plugin;

import android.widget.LinearLayout;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.StartupTrace;

/**
 * ★★★ 社区版：**安全模式开关**。
 *
 * <p><b>为什么需要它</b>：2.0.10 修好「安全模式震荡」之后，行为变成了：
 * 上次启动没走完 → 本次跳过 native（bytehook / SDL3）→ 启动器能开 ✓
 * <b>但游戏大概率跑不起来</b> ✗（那两个库是渲染/注入用的 ✗）
 *
 * <p>更关键的是：**安全模式会一直开着** ✗ ——
 * 因为「启动成功」只在 native 加载**真的走完**时才写 ✗
 * 而安全模式下**根本不加载 native** ✗ ⇒ 永远写不进去 ✗
 * ⇒ **用户被卡在安全模式里，没有出口** ✗✓
 *
 * <p>这个插件就是那个出口 ✓：
 * <ul>
 *   <li><b>下次正常启动</b> —— 手动写一次「启动成功」✓ 下次就会加载 native ✓
 *       （如果 native 真的坏了，下次还是会闪退 ✗ 那就再进安全模式 ✓
 *        但至少**用户可以自己试** ✓ 而不是被永久锁死 ✗）</li>
 *   <li><b>看当前状态</b> —— 直接读路标 ✓ 不用去翻诊断报告 ✓</li>
 * </ul>
 *
 * <p><b>★ 为什么不自动重试</b>：自动「试一次正常启动」会让「闪退 → 安全 → 闪退 → 安全」
 * 变成循环 ✗ —— 那正是 2.0.10 刚修掉的问题 ✗
 * ⇒ **只做手动开关** ✓ 由用户决定什么时候再试 ✓
 */
public final class PluginSafetyMode {

    private PluginSafetyMode() {
    }

    public static void ui(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);

            col.addView(PluginUiKit.text(a, "安全模式"));
            col.addView(PluginUiKit.sub(a, "★ 安全模式 = 上次启动没走完 → 本次跳过 native 库"
                    + "（bytehook / SDL3）✗"));
            col.addView(PluginUiKit.sub(a, "★ 后果：启动器能开 ✓ 但游戏大概率跑不起来 ✗"
                    + "（那两个库是渲染/注入用的 ✗）"));
            col.addView(PluginUiKit.sub(a, " "));

            final boolean on;
            try {
                on = StartupTrace.lastRunStartupIncomplete();
            } catch (Throwable t) {
                col.addView(PluginUiKit.sub(a, "读不到状态 ✗：" + t));
                PluginUiKit.sheet(a, "安全模式", PluginUiKit.scroller(a, col), "关闭", null);
                return;
            }

            col.addView(PluginUiKit.label(a, "当前状态"));
            col.addView(PluginUiKit.sub(a, on
                    ? "✗ 上次启动**没走完** → 这次是安全模式（跳过了 native）"
                    : "✓ 上次启动**正常走完** → native 已加载"));
            col.addView(PluginUiKit.sub(a, " "));

            col.addView(PluginUiKit.label(a, "操作"));
            PluginUiKit.row(a, "下次正常启动（关掉安全模式）", v -> {
                try {
                    StartupTrace.mark(StartupTrace.MARK_STARTUP_OK);
                    PluginUiKit.info(a, "已设置",
                            "下次启动会**加载 native 库** ✓\n\n"
                                    + "★ 如果 native 真的有问题，下次还会闪退 ✗\n"
                                    + "　那就会自动回到安全模式 ✓ 不会卡死 ✓\n\n"
                                    + "★ 如果下次能正常打开、游戏也能跑 ✓\n"
                                    + "　说明 native 没问题 ✓ 之后就不用再管了 ✓");
                } catch (Throwable t) {
                    PluginUiKit.toast(a, "设置失败：" + t);
                }
            });
            col.addView(PluginUiKit.sub(a, "　（写一次「启动成功」路标 ✓ 下次就会加载 native ✓）"));
            col.addView(PluginUiKit.sub(a, " "));

            col.addView(PluginUiKit.label(a, "说明"));
            col.addView(PluginUiKit.sub(a, "★ 为什么安全模式会「一直开着」✗："));
            col.addView(PluginUiKit.sub(a, "　「启动成功」只在 native **真的加载完** 时才写 ✓"));
            col.addView(PluginUiKit.sub(a, "　而安全模式下**根本不加载** native ✗ ⇒ 永远写不进去 ✗"));
            col.addView(PluginUiKit.sub(a, "　⇒ 所以需要这个手动开关 ✓ 否则用户被永久锁死 ✗"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 为什么不自动重试 ✗："));
            col.addView(PluginUiKit.sub(a, "　自动「试一次正常启动」会让「闪退→安全→闪退→安全」变成循环 ✗"));
            col.addView(PluginUiKit.sub(a, "　那正是 2.0.10 刚修掉的问题 ✗ ⇒ **只做手动** ✓"));

            PluginUiKit.sheet(a, "安全模式", PluginUiKit.scroller(a, col), "关闭", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }
}
