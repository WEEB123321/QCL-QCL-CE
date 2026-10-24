package com.qcl.launcher.launcher.uis.universal.setting;

import android.content.Context;
import android.content.Intent;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.launcher.uis.universal.setting.right.UniversalGameSettingUI;
import com.qcl.launcher.launcher.uis.universal.setting.right.help.AboutUsUI;
import com.qcl.launcher.launcher.uis.universal.setting.right.help.DonateUI;
import com.qcl.launcher.launcher.uis.universal.setting.right.help.FeedbackUI;
import com.qcl.launcher.launcher.uis.universal.setting.right.launcher.DownloadSettingUI;
import com.qcl.launcher.launcher.uis.universal.setting.right.launcher.ExteriorSettingUI;

/* loaded from: classes2.dex */
public class SettingUIManager {
    public AboutUsUI aboutUsUI;
    public DonateUI donateUI;
    public DownloadSettingUI downloadSettingUI;
    public ExteriorSettingUI exteriorSettingUI;
    public FeedbackUI feedbackUI;
    public BaseUI[] settingUIs;
    public UniversalGameSettingUI universalGameSettingUI;

    /**
     * ★★★ 社区版：「通用」设置页（启动器更新 / 缓存 / 语言 / 导出日志 / 一键清理）。
     *
     * <p><b>★ 为什么必须放在这里，而不能像其它新页那样走 {@code switchMainUI}</b>：
     * {@code ui_setting_universal} 是 {@code ui_setting} 的**子节点**，和另外 7 个子页一起
     * 挤在同一个 {@code RelativeLayout} 里 —— 它们**天然重叠**，靠本类
     * {@link #switchSettingUIs} 把非目标页逐个 {@code onStop()} 来保证只显示一个。
     *
     * <p>踩过的坑：一开始把它当独立整页用 {@code switchMainUI} 打开，结果是
     * ① 上一个子页没被停掉 → **两个子页同时可见、叠在一起**；
     * ② {@code switchMainUI} 会把它父页 {@code ui_setting} 一起 {@code onStop} 掉 →
     *    它作为子节点反而显示不出来。
     */
    public UniversalSettingUI universalSettingUI;

    public SettingUIManager(Context context, MainActivity mainActivity) {
        this.universalGameSettingUI = new UniversalGameSettingUI(context, mainActivity);
        this.downloadSettingUI = new DownloadSettingUI(context, mainActivity);
        this.exteriorSettingUI = new ExteriorSettingUI(context, mainActivity);
        this.feedbackUI = new FeedbackUI(context, mainActivity);
        this.donateUI = new DonateUI(context, mainActivity);
        this.aboutUsUI = new AboutUsUI(context, mainActivity);
        this.universalSettingUI = new UniversalSettingUI(context, mainActivity);
        this.universalGameSettingUI.onCreate();
        this.downloadSettingUI.onCreate();
        this.exteriorSettingUI.onCreate();
        this.feedbackUI.onCreate();
        this.donateUI.onCreate();
        this.aboutUsUI.onCreate();
        this.universalSettingUI.onCreate();
        UniversalGameSettingUI universalGameSettingUI = this.universalGameSettingUI;
        this.settingUIs = new BaseUI[]{universalGameSettingUI, this.downloadSettingUI,
                this.exteriorSettingUI, this.feedbackUI, this.donateUI, this.aboutUsUI,
                this.universalSettingUI};
        switchSettingUIs(universalGameSettingUI);
    }

    /**
     * ★★★ v3.0.3：onStart 失败即拉黑的页面名单。
     *
     * <p>为什么必须有：{@link #switchSettingUIs} 早先是裸的 {@code onStart()/onStop()}，
     * 8 个设置子页里任意一个抛异常都会冒泡到主线程 = 整机闪退。
     * 用户报的「点设置就闪退」正是这一类，而堆栈跟他的操作看起来毫无关系。
     */
    private final java.util.LinkedHashSet<BaseUI> brokenUIs = new java.util.LinkedHashSet<>();

    /**
     * ★ v3.0.3：本类是否有页面被隔离过（给诊断页用）。
     *
     * <p>★ 为什么必须暴露：被隔离的页面在界面上就是「点了没反应」，
     * 不暴露出来的话用户只会以为功能坏了，而日志在另一台设备上。
     */
    public boolean hasBrokenPages() {
        return !brokenUIs.isEmpty();
    }

    public void switchSettingUIs(BaseUI baseUI) {
        int i = 0;
        while (true) {
            BaseUI[] baseUIArr = this.settingUIs;
            if (i >= baseUIArr.length) {
                // ★★★ 3.0：设置页 8 个子页切换后补扫液态玻璃。
                //   这一层**不能**靠 UIManager 兜住 —— 子页全部挂在 ui_setting 内部的
                //   同一个 RelativeLayout 里，切换只是 setVisibility，不走 switchMainUI。
                //   不接这里，8 个设置子页就只有「第一次进」那一屏是玻璃。
                rescanGlass(baseUI);
                return;
            }
            // ★★★ v3.0.3：逐个包起来。见 UiDispatch 的类注释 ——
            //   这里原先一处 try/catch 都没有，一个子页崩 = 整个启动器崩。
            final BaseUI ui = baseUIArr[i];
            if (ui == baseUI) {
                com.qcl.launcher.launcher.uis.tools.UiDispatch.run("onStart", ui,
                        new Runnable() {
                            @Override
                            public void run() {
                                ui.onStart();
                            }
                        }, true, brokenUIs);
            } else {
                com.qcl.launcher.launcher.uis.tools.UiDispatch.run("onStop", ui,
                        new Runnable() {
                            @Override
                            public void run() {
                                ui.onStop();
                            }
                        }, false, brokenUIs);
            }
            i++;
        }
    }

    /** 主界面的玻璃补扫（延到下一帧，原因见 MainActivity.rescanLiquidGlass 注释）。 */
    private void rescanGlass(BaseUI ui) {
        try {
            MainActivity activity = ui != null ? ui.activity : null;
            if (activity != null) {
                activity.rescanLiquidGlass();
            }
        } catch (Throwable ignored) {
        }
    }

    // ★★★ v3.0.3：下面三个循环同样逐个包起来。
    //   「从文件选择器返回就闪退」「从后台切回来就闪退」就是这里出去的 ——
    //   onActivityResult 的调用方是系统，用户只会觉得「我选了张图就崩了」。

    public void onActivityResult(int i, int i2, Intent intent) {
        for (BaseUI baseUI : this.settingUIs) {
            final BaseUI ui = baseUI;
            final int req = i, res = i2;
            final Intent data = intent;
            com.qcl.launcher.launcher.uis.tools.UiDispatch.run("onActivityResult", ui,
                    new Runnable() {
                        @Override
                        public void run() {
                            ui.onActivityResult(req, res, data);
                        }
                    }, false, brokenUIs);
        }
    }

    public void onPause() {
        for (BaseUI baseUI : this.settingUIs) {
            final BaseUI ui = baseUI;
            com.qcl.launcher.launcher.uis.tools.UiDispatch.run("onPause", ui,
                    new Runnable() {
                        @Override
                        public void run() {
                            ui.onPause();
                        }
                    }, false, brokenUIs);
        }
    }

    public void onResume() {
        for (BaseUI baseUI : this.settingUIs) {
            final BaseUI ui = baseUI;
            com.qcl.launcher.launcher.uis.tools.UiDispatch.run("onResume", ui,
                    new Runnable() {
                        @Override
                        public void run() {
                            ui.onResume();
                        }
                    }, false, brokenUIs);
        }
    }
}
