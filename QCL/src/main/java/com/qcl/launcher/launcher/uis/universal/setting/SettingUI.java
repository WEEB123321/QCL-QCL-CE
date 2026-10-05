package com.qcl.launcher.launcher.uis.universal.setting;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import com.qcl.launcher.R;
/* loaded from: classes2.dex */
public class SettingUI extends BaseUI implements View.OnClickListener {
    public LinearLayout settingUI;
    public SettingUIManager settingUIManager;
    public LinearLayout startAboutUsUI;
    public LinearLayout startDonateUI;
    public LinearLayout startDownloadSettingUI;
    public LinearLayout startExteriorSettingUI;
    public LinearLayout startFeedbackUI;
    public LinearLayout startGlobalGameSettingUI;
    /** ★ 社区版新增：备份与恢复入口（低频功能收进「我的」，不占底部导航） */
    public LinearLayout startBackupUI;
    /** ★ 社区版新增：启动诊断入口（同上，只在出问题时才用得上） */
    public LinearLayout startDiagnosticUI;
    /** ★ 社区版新增：日志中心入口（和诊断互补：诊断看启动路标，日志看运行期日志） */
    public LinearLayout startLogCenterUI;
    /** ★ 社区版新增：本地统计入口 */
    public LinearLayout startStatsUI;
    /** ★ 社区版新增：新手上路入口 */
    public LinearLayout startTutorialUI;
    /** ★ 社区版新增：自动任务入口 */
    public LinearLayout startAutoTaskUI;
    /** ★ 社区版新增：资源冲突检测入口 */
    public LinearLayout startResourceConflictUI;
    /** ★ 社区版新增：兼容性备忘入口 */
    public LinearLayout startCompatUI;
    /** ★ 社区版新增：模组体检入口 */
    public LinearLayout startModCheckUI;
    /** ★ 社区版新增：通用设置入口（启动器更新 / 缓存 / 语言 / 导出日志） */
    public LinearLayout startUniversalSettingUI;

    public SettingUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onCreate() {
        super.onCreate();
        this.settingUI = (LinearLayout) this.activity.findViewById(R.id.ui_setting);
        this.startGlobalGameSettingUI = (LinearLayout) this.activity.findViewById(R.id.start_global_game_setting_ui);
        this.startExteriorSettingUI = (LinearLayout) this.activity.findViewById(R.id.start_exterior_setting_ui);
        this.startDownloadSettingUI = (LinearLayout) this.activity.findViewById(R.id.start_download_setting_ui);
        this.startFeedbackUI = (LinearLayout) this.activity.findViewById(R.id.start_feedback_ui);
        this.startDonateUI = (LinearLayout) this.activity.findViewById(R.id.start_donate_ui);
        this.startAboutUsUI = (LinearLayout) this.activity.findViewById(R.id.start_about_ui);
        this.startBackupUI = (LinearLayout) this.activity.findViewById(R.id.start_backup_ui);
        this.startDiagnosticUI = (LinearLayout) this.activity.findViewById(R.id.start_diagnostic_ui);
        this.startLogCenterUI = (LinearLayout) this.activity.findViewById(R.id.start_log_center_ui);
        this.startStatsUI = (LinearLayout) this.activity.findViewById(R.id.start_stats_ui);
        this.startTutorialUI = (LinearLayout) this.activity.findViewById(R.id.start_tutorial_ui);
        this.startAutoTaskUI = (LinearLayout) this.activity.findViewById(R.id.start_auto_task_ui);
        this.startResourceConflictUI = (LinearLayout) this.activity.findViewById(R.id.start_resource_conflict_ui);
        this.startCompatUI = (LinearLayout) this.activity.findViewById(R.id.start_compat_ui);
        this.startModCheckUI = (LinearLayout) this.activity.findViewById(R.id.start_mod_check_ui);
        this.startUniversalSettingUI = (LinearLayout) this.activity.findViewById(R.id.start_universal_setting_ui);
        this.startGlobalGameSettingUI.setOnClickListener(this);
        this.startExteriorSettingUI.setOnClickListener(this);
        this.startDownloadSettingUI.setOnClickListener(this);
        this.startFeedbackUI.setOnClickListener(this);
        this.startDonateUI.setOnClickListener(this);
        this.startAboutUsUI.setOnClickListener(this);
        if (this.startBackupUI != null) {
            this.startBackupUI.setOnClickListener(this);
        }
        if (this.startDiagnosticUI != null) {
            this.startDiagnosticUI.setOnClickListener(this);
        }
        if (this.startLogCenterUI != null) {
            this.startLogCenterUI.setOnClickListener(this);
        }
        if (this.startStatsUI != null) {
            this.startStatsUI.setOnClickListener(this);
        }
        if (this.startTutorialUI != null) {
            this.startTutorialUI.setOnClickListener(this);
        }
        if (this.startAutoTaskUI != null) {
            this.startAutoTaskUI.setOnClickListener(this);
        }
        if (this.startResourceConflictUI != null) {
            this.startResourceConflictUI.setOnClickListener(this);
        }
        if (this.startCompatUI != null) {
            this.startCompatUI.setOnClickListener(this);
        }
        if (this.startModCheckUI != null) {
            this.startModCheckUI.setOnClickListener(this);
        }
        if (this.startUniversalSettingUI != null) {
            this.startUniversalSettingUI.setOnClickListener(this);
        }
        this.settingUIManager = new SettingUIManager(this.context, this.activity);
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(this.context.getResources().getString(R.string.setting_ui_title), canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.settingUI, this.activity, this.context, true);
        init();
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.settingUI, this.activity, this.context, true);
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        this.settingUIManager.onActivityResult(i, i2, intent);
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onPause() {
        super.onPause();
        this.settingUIManager.onPause();
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onResume() {
        super.onResume();
        this.settingUIManager.onResume();
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View view) {
        if (view == this.startGlobalGameSettingUI) {
            SettingUIManager settingUIManager = this.settingUIManager;
            settingUIManager.switchSettingUIs(settingUIManager.universalGameSettingUI);
        }
        if (view == this.startExteriorSettingUI) {
            SettingUIManager settingUIManager3 = this.settingUIManager;
            settingUIManager3.switchSettingUIs(settingUIManager3.exteriorSettingUI);
        }
        if (view == this.startDownloadSettingUI) {
            SettingUIManager settingUIManager4 = this.settingUIManager;
            settingUIManager4.switchSettingUIs(settingUIManager4.downloadSettingUI);
        }
        if (view == this.startFeedbackUI) {
            SettingUIManager settingUIManager6 = this.settingUIManager;
            settingUIManager6.switchSettingUIs(settingUIManager6.feedbackUI);
        }
        if (view == this.startDonateUI) {
            SettingUIManager settingUIManager7 = this.settingUIManager;
            settingUIManager7.switchSettingUIs(settingUIManager7.donateUI);
        }
        if (view == this.startAboutUsUI) {
            SettingUIManager settingUIManager8 = this.settingUIManager;
            settingUIManager8.switchSettingUIs(settingUIManager8.aboutUsUI);
        }
        // ★ 社区版新增：备份与恢复是独立整页（不是设置右栏的一格），直接切页面。
        //   用 switchMainUI 走返回栈，玩家按返回键能回到设置页。
        if (view == this.startBackupUI) {
            this.activity.uiManager.switchMainUI(this.activity.uiManager.backupUI);
        }
        // ★ 社区版新增：启动诊断同样是独立整页，走 switchMainUI 以便返回键能回到设置页。
        //   判空是必要的：DiagnosticUI 的构造器万一失败，uiManager 里那个字段就是 null，
        //   而 switchMainUI(null) 是安全的空操作，页面不会切但也不会崩。
        if (view == this.startDiagnosticUI) {
            this.activity.uiManager.switchMainUI(this.activity.uiManager.diagnosticUI);
        }
        // ★ 社区版新增：日志中心同样是独立整页，走 switchMainUI 以便返回键回到设置页。
        if (view == this.startLogCenterUI) {
            this.activity.uiManager.switchMainUI(this.activity.uiManager.logCenterUI);
        }
        // ★ 社区版新增：本地统计同样是独立整页。
        if (view == this.startStatsUI) {
            this.activity.uiManager.switchMainUI(this.activity.uiManager.statsUI);
        }
        // ★ 社区版新增：新手上路同样是独立整页。
        if (view == this.startTutorialUI) {
            this.activity.uiManager.switchMainUI(this.activity.uiManager.tutorialUI);
        }
        // ★ 社区版新增：自动任务同样是独立整页。
        if (view == this.startAutoTaskUI) {
            this.activity.uiManager.switchMainUI(this.activity.uiManager.autoTaskUI);
        }
        // ★ 社区版新增：资源冲突检测同样是独立整页。
        if (view == this.startResourceConflictUI) {
            this.activity.uiManager.switchMainUI(this.activity.uiManager.resourceConflictUI);
        }
        // ★ 社区版新增：兼容性备忘同样是独立整页。
        if (view == this.startCompatUI) {
            this.activity.uiManager.switchMainUI(this.activity.uiManager.compatUI);
        }
        // ★ 社区版新增：模组体检同样是独立整页。
        if (view == this.startModCheckUI) {
            this.activity.uiManager.switchMainUI(this.activity.uiManager.modCheckUI);
        }
        // ★★★ 社区版：「通用」设置页。
        //   ★ 必须走 switchSettingUIs，不能走 switchMainUI —— 它是 ui_setting 的**子页**，
        //     和另外 7 个子页挤在同一个 RelativeLayout 里（天然重叠），
        //     只有 switchSettingUIs 会把非目标页逐个 onStop，保证同一时刻只显示一个。
        //     走 switchMainUI 的后果：① 上一个子页不停 → 两个叠在一起；
        //     ② switchMainUI 会连父页 ui_setting 一起停掉 → 自己反而看不见。
        if (view == this.startUniversalSettingUI) {
            SettingUIManager m = this.settingUIManager;
            m.switchSettingUIs(m.universalSettingUI);
        }
    }

    private void init() {
        this.settingUIManager.universalGameSettingUI.refresh();
    }
}
