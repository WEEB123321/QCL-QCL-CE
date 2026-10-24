package com.qcl.launcher.launcher.uis.game.manager;

import android.content.Context;
import android.content.Intent;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.game.manager.right.AutoInstallUI;
import com.qcl.launcher.launcher.uis.game.manager.right.ModManagerUI;
import com.qcl.launcher.launcher.uis.game.manager.right.VersionSettingUI;
import com.qcl.launcher.launcher.uis.game.manager.right.WorldManagerUI;
import com.qcl.launcher.launcher.uis.tools.BaseUI;

/* loaded from: classes2.dex */
public class GameManagerUIManager {
    public AutoInstallUI autoInstallUI;
    public BaseUI[] gameManagerUIs;
    public ModManagerUI modManagerUI;
    public VersionSettingUI versionSettingUI;
    public WorldManagerUI worldManagerUI;

    public GameManagerUIManager(Context context, MainActivity mainActivity) {
        this.versionSettingUI = new VersionSettingUI(context, mainActivity);
        this.modManagerUI = new ModManagerUI(context, mainActivity);
        this.autoInstallUI = new AutoInstallUI(context, mainActivity);
        this.worldManagerUI = new WorldManagerUI(context, mainActivity);
        this.versionSettingUI.onCreate();
        this.modManagerUI.onCreate();
        this.autoInstallUI.onCreate();
        this.worldManagerUI.onCreate();
        VersionSettingUI versionSettingUI = this.versionSettingUI;
        this.gameManagerUIs = new BaseUI[]{versionSettingUI, this.modManagerUI, this.autoInstallUI, this.worldManagerUI};
        switchGameManagerUIs(versionSettingUI);
    }

    /**
     * ★★★ v3.0.3：onStart 失败即拉黑的页面名单。
     *
     * <p>实例页 4 个子页的派发原先全是裸调用，任意一个 onStart/onStop 抛异常
     * 就冒泡到主线程 = 整机闪退。语义与 {@code UIManager.safeDispatch} 一致，
     * 实现统一收在 {@link com.qcl.launcher.launcher.uis.tools.UiDispatch}。
     */
    private final java.util.LinkedHashSet<BaseUI> brokenUIs = new java.util.LinkedHashSet<>();

    public boolean hasBrokenPages() {
        return !brokenUIs.isEmpty();
    }

    public void switchGameManagerUIs(BaseUI baseUI) {
        int i = 0;
        while (true) {
            BaseUI[] baseUIArr = this.gameManagerUIs;
            if (i >= baseUIArr.length) {
                return;
            }
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

    public void onActivityResult(int i, int i2, Intent intent) {
        for (BaseUI baseUI : this.gameManagerUIs) {
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
        for (BaseUI baseUI : this.gameManagerUIs) {
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
        for (BaseUI baseUI : this.gameManagerUIs) {
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
