package com.qcl.launcher.launcher.uis.main;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.instance.ControlKeySyncHelper;
import com.qcl.launcher.launcher.server.ServerListActivity;
import com.qcl.launcher.launcher.server.ServerProfile;
import com.qcl.launcher.launcher.setting.SettingUtils;
import com.qcl.launcher.launcher.setting.game.PrivateGameSetting;
import com.qcl.launcher.launcher.uis.tools.BottomNavHelper;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.gson.GsonUtils;

import java.io.File;
import java.util.ArrayList;

/** 「游戏」入口：游戏实例 / 服务器列表，以及按键同步。 */
public final class GameEntryDialog {

    private GameEntryDialog() {
    }

    public static void show(MainActivity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        String gameDir = activity.launcherSetting == null
                ? null : activity.launcherSetting.gameFileDirectory;
        ArrayList<String> instances = SettingUtils.getLocalVersionNames(gameDir);
        int serverCount = countServers();
        String layout = activity.privateGameSetting == null
                ? "" : activity.privateGameSetting.controlLayout;
        String layoutLabel = layout == null || layout.trim().isEmpty()
                ? "默认布局" : layout;
        String instanceName = ServerProfile.currentInstanceName(activity);
        if (instanceName.isEmpty()) {
            instanceName = "当前实例";
        }

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        body.setPadding(pad, dp(activity, 4), pad, 0);

        Button gameInstances = new Button(activity);
        gameInstances.setAllCaps(false);
        gameInstances.setText("游戏实例（" + instances.size() + "）\n版本、模组、存档");
        body.addView(gameInstances, matchWrap());

        Button serverList = new Button(activity);
        serverList.setAllCaps(false);
        serverList.setText("服务器列表（" + serverCount + "）\n启动、插件、控制台");
        body.addView(serverList, matchWrap());

        View divider = new View(activity);
        divider.setBackgroundColor(0x33000000);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 1)));
        dividerParams.topMargin = dp(activity, 14);
        dividerParams.bottomMargin = dp(activity, 10);
        body.addView(divider, dividerParams);

        TextView title = new TextView(activity);
        title.setText("按键同步");
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(title);

        TextView hint = new TextView(activity);
        hint.setText("当前：" + instanceName + " · " + layoutLabel
                + "\n先在版本设置里改好按键，再同步给其他实例。");
        hint.setTextSize(12f);
        hint.setAlpha(0.72f);
        hint.setPadding(0, dp(activity, 4), 0, dp(activity, 6));
        body.addView(hint);

        Button sync = new Button(activity);
        sync.setAllCaps(false);
        sync.setText("同步按键到所有游戏实例");
        sync.setEnabled(!instances.isEmpty());
        body.addView(sync, matchWrap());

        Button restore = new Button(activity);
        restore.setAllCaps(false);
        restore.setText("恢复默认按键");
        body.addView(restore, matchWrap());

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("游戏")
                .setView(body)
                .setNegativeButton("关闭", null)
                .create();

        gameInstances.setOnClickListener(v -> {
            dialog.dismiss();
            if (activity.uiManager == null || activity.uiManager.versionListUI == null) {
                toast(activity, "版本列表未加载");
                return;
            }
            activity.uiManager.switchMainUITab(activity.uiManager.versionListUI);
            if (activity.bottomNav != null) {
                activity.bottomNav.show(BottomNavHelper.TAB_INSTANCE);
            }
        });

        serverList.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                activity.startActivity(new Intent(activity, ServerListActivity.class));
            } catch (Throwable t) {
                toast(activity, "打开服务器列表失败：" + t.getMessage());
            }
        });

        sync.setOnClickListener(v -> confirmSync(activity, dialog, instances,
                gameDir, layout, layoutLabel));

        restore.setOnClickListener(v -> confirmRestore(activity, dialog));

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private static void confirmSync(MainActivity activity, AlertDialog parent,
                                    ArrayList<String> instances, String gameDir,
                                    String layout, String layoutLabel) {
        if (instances.isEmpty()) {
            toast(activity, "还没有游戏实例");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("同步按键")
                .setMessage("把「" + layoutLabel + "」同步到 " + instances.size()
                        + " 个游戏实例？\n\n只修改按键布局选择，不动内存、Java、渲染器和存档。")
                .setPositiveButton("同步", (d, w) -> {
                    parent.dismiss();
                    runSync(activity, gameDir, layout);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void runSync(MainActivity activity, String gameDir, String layout) {
        PrivateGameSetting global = GsonUtils.getPrivateGameSettingFromFile(
                AppManifest.SETTING_DIR + "/private_game_setting.json");
        PrivateGameSetting template = global != null ? global : activity.privateGameSetting;
        ProgressDialog progress = ProgressDialog.show(activity, null,
                "正在同步按键…", true, false);
        new Thread(() -> {
            ControlKeySyncHelper.Result result;
            try {
                result = ControlKeySyncHelper.syncAll(activity, gameDir, template, layout);
            } catch (Throwable t) {
                result = new ControlKeySyncHelper.Result();
                result.lastError = t.getMessage();
            }
            final ControlKeySyncHelper.Result r = result;
            activity.runOnUiThread(() -> {
                dismiss(progress);
                if (activity.isFinishing()) {
                    return;
                }
                String message = "已同步 " + r.changed + " 个实例"
                        + (r.skipped > 0 ? "，跳过 " + r.skipped + " 个" : "")
                        + (r.lastError == null ? "" : "\n最后错误：" + r.lastError);
                new AlertDialog.Builder(activity)
                        .setTitle("按键同步完成")
                        .setMessage(message)
                        .setPositiveButton("知道了", null)
                        .show();
            });
        }, "qcl-control-sync").start();
    }

    private static void confirmRestore(MainActivity activity, AlertDialog parent) {
        new AlertDialog.Builder(activity)
                .setTitle("恢复默认按键")
                .setMessage("只恢复通过启动器改过、而且之后没有被外部修改的实例。\n\n"
                        + "手动改过 qcl.cfg、或从未在启动器里改过按键的实例不会被碰。")
                .setPositiveButton("恢复", (d, w) -> {
                    parent.dismiss();
                    runRestore(activity);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void runRestore(MainActivity activity) {
        ProgressDialog progress = ProgressDialog.show(activity, null,
                "正在恢复按键…", true, false);
        new Thread(() -> {
            ControlKeySyncHelper.Result result;
            try {
                result = ControlKeySyncHelper.restoreDefaults(activity);
            } catch (Throwable t) {
                result = new ControlKeySyncHelper.Result();
                result.lastError = t.getMessage();
            }
            final ControlKeySyncHelper.Result r = result;
            activity.runOnUiThread(() -> {
                dismiss(progress);
                if (activity.isFinishing()) {
                    return;
                }
                String message = "已恢复 " + r.changed + " 个实例"
                        + (r.skipped > 0 ? "\n保留 " + r.skipped
                        + " 个（未同步过、已删除或已被外部修改）" : "")
                        + (r.lastError == null ? "" : "\n最后错误：" + r.lastError);
                new AlertDialog.Builder(activity)
                        .setTitle("恢复完成")
                        .setMessage(message)
                        .setPositiveButton("知道了", null)
                        .show();
            });
        }, "qcl-control-restore").start();
    }

    private static int countServers() {
        File[] dirs = ServerProfile.serversRoot().listFiles(File::isDirectory);
        if (dirs == null) {
            return 0;
        }
        int count = 0;
        for (File dir : dirs) {
            if (ServerProfile.load(dir) != null) {
                count++;
            }
        }
        return count;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static void dismiss(ProgressDialog dialog) {
        try {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Throwable ignored) {
        }
    }

    private static int dp(MainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static void toast(MainActivity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
