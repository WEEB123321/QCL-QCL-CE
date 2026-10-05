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

    public void switchSettingUIs(BaseUI baseUI) {
        int i = 0;
        while (true) {
            BaseUI[] baseUIArr = this.settingUIs;
            if (i >= baseUIArr.length) {
                return;
            }
            if (baseUIArr[i] == baseUI) {
                baseUIArr[i].onStart();
            } else {
                baseUIArr[i].onStop();
            }
            i++;
        }
    }

    public void onActivityResult(int i, int i2, Intent intent) {
        for (BaseUI baseUI : this.settingUIs) {
            baseUI.onActivityResult(i, i2, intent);
        }
    }

    public void onPause() {
        for (BaseUI baseUI : this.settingUIs) {
            baseUI.onPause();
        }
    }

    public void onResume() {
        for (BaseUI baseUI : this.settingUIs) {
            baseUI.onResume();
        }
    }
}
