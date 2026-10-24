package com.qcl.launcher.launcher.uis.game.download;

import android.content.Context;
import android.content.Intent;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.game.download.right.DownloadMinecraftUI;
import com.qcl.launcher.launcher.uis.game.download.right.DownloadModUI;
import com.qcl.launcher.launcher.uis.game.download.right.DownloadPackageUI;
import com.qcl.launcher.launcher.uis.game.download.right.DownloadResourcePackUI;
import com.qcl.launcher.launcher.uis.game.download.right.DownloadShaderUI;
import com.qcl.launcher.launcher.uis.game.download.right.DownloadWorldUI;
import com.qcl.launcher.launcher.uis.tools.BaseUI;

/* loaded from: classes2.dex */
public class DownloadUIManager {
    public DownloadMinecraftUI downloadMinecraftUI;
    public DownloadModUI downloadModUI;
    public DownloadPackageUI downloadPackageUI;
    public DownloadResourcePackUI downloadResourcePackUI;
    public DownloadShaderUI downloadShaderUI;
    public BaseUI[] downloadUIs;
    public DownloadWorldUI downloadWorldUI;

    public DownloadUIManager(Context context, MainActivity mainActivity) {
        this.downloadMinecraftUI = new DownloadMinecraftUI(context, mainActivity);
        this.downloadModUI = new DownloadModUI(context, mainActivity);
        this.downloadPackageUI = new DownloadPackageUI(context, mainActivity);
        this.downloadResourcePackUI = new DownloadResourcePackUI(context, mainActivity);
        this.downloadWorldUI = new DownloadWorldUI(context, mainActivity);
        this.downloadShaderUI = new DownloadShaderUI(context, mainActivity);
        this.downloadMinecraftUI.onCreate();
        this.downloadModUI.onCreate();
        this.downloadPackageUI.onCreate();
        this.downloadResourcePackUI.onCreate();
        this.downloadWorldUI.onCreate();
        this.downloadShaderUI.onCreate();
        DownloadMinecraftUI downloadMinecraftUI = this.downloadMinecraftUI;
        this.downloadUIs = new BaseUI[]{downloadMinecraftUI, this.downloadModUI, this.downloadPackageUI, this.downloadResourcePackUI, this.downloadWorldUI, this.downloadShaderUI};
        switchDownloadUI(downloadMinecraftUI);
    }

    /**
     * ★★★ v3.0.3：onStart 失败即拉黑的页面名单。
     *
     * <p>下载页 6 个子页的派发原先全是裸调用，任意一个 onStart/onStop 抛异常
     * 就冒泡到主线程 = 整机闪退。语义与 {@code UIManager.safeDispatch} 一致，
     * 实现统一收在 {@link com.qcl.launcher.launcher.uis.tools.UiDispatch}。
     */
    private final java.util.LinkedHashSet<BaseUI> brokenUIs = new java.util.LinkedHashSet<>();

    public boolean hasBrokenPages() {
        return !brokenUIs.isEmpty();
    }

    public void switchDownloadUI(BaseUI baseUI) {
        int i = 0;
        while (true) {
            BaseUI[] baseUIArr = this.downloadUIs;
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
        for (BaseUI baseUI : this.downloadUIs) {
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
        for (BaseUI baseUI : this.downloadUIs) {
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
        for (BaseUI baseUI : this.downloadUIs) {
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
