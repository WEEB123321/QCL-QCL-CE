package com.qcl.launcher.launcher.uis.autotask;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.autotask.AutoTaskRunner;
import com.qcl.launcher.launcher.backup.BackupRecord;
import com.qcl.launcher.launcher.backup.InstanceBackupHelper;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import java.io.File;
import java.util.List;

/**
 * ★★★ 社区版新增：自动任务设置页。
 *
 * <p><b>★ 交互取舍</b>：不用 Switch 控件，改成「点一下切换取值」的灰色卡片。
 * 理由有二：一是与备份页观感统一（项目全部用灰色圆角卡片）；二是 Switch 在本项目的
 * 主题下需要额外配色适配，多一处不确定就多一处崩溃面。
 *
 * <p><b>★ 诚实标注执行时机</b>：所有「定时」任务都在<b>打开启动器时</b>检查是否到期，
 * 页面底部明确写出这一点 —— 否则用户会以为它能像闹钟一样准点后台执行。
 */
public class AutoTaskUI extends BaseUI implements View.OnClickListener {

    private LinearLayout autoTaskUI;
    private LinearLayout container;
    private TextView status;

    /** 定时备份间隔候选（天）：关闭 / 每天 / 每周 / 每月 */
    private static final int[] INTERVAL_DAYS = {0, 1, 7, 30};
    /** 保留份数候选：3 / 5 / 10 / 不限(0) */
    private static final int[] KEEP_VALUES = {3, 5, 10, 0};

    /** ★ 存档备份保留份数的候选（默认 3；0 = 不限制） */
    private static final int[] WORLD_KEEP_VALUES = {1, 3, 5, 10, 0};

    public AutoTaskUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.autoTaskUI = this.activity.findViewById(R.id.ui_auto_task);
        this.container = this.activity.findViewById(R.id.auto_task_container);
        this.status = this.activity.findViewById(R.id.auto_task_status);
        View runNow = this.activity.findViewById(R.id.auto_task_run_now);
        if (runNow != null) {
            runNow.setOnClickListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(
                this.context.getResources().getString(R.string.auto_task_title),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.autoTaskUI, this.activity, this.context, true);
        rebuild();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.autoTaskUI, this.activity, this.context, true);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.auto_task_run_now) {
            runNow();
        }
    }

    // ------------------------------------------------------------------ 渲染

    private void rebuild() {
        if (this.container == null) {
            return;
        }
        this.container.removeAllViews();
        AutoTaskRunner.Setting s = AutoTaskRunner.get(this.context);

        this.container.addView(cycleRow(
                this.context.getString(R.string.auto_task_scheduled_backup),
                this.context.getString(R.string.auto_task_scheduled_hint),
                intervalLabel(s.scheduledBackupIntervalDays),
                new Runnable() {
                    @Override
                    public void run() {
                        AutoTaskRunner.Setting cur = AutoTaskRunner.get(context);
                        cur.scheduledBackupIntervalDays = next(INTERVAL_DAYS, cur.scheduledBackupIntervalDays);
                        AutoTaskRunner.save(context, cur);
                        rebuild();
                    }
                }));

        this.container.addView(toggleRow(
                this.context.getString(R.string.auto_task_before_launch),
                this.context.getString(R.string.auto_task_before_launch_hint),
                s.backupBeforeLaunch,
                new Runnable() {
                    @Override
                    public void run() {
                        AutoTaskRunner.Setting cur = AutoTaskRunner.get(context);
                        cur.backupBeforeLaunch = !cur.backupBeforeLaunch;
                        AutoTaskRunner.save(context, cur);
                        rebuild();
                    }
                }));

        // ★ 社区版新增：启动前检查（#21）。存在 LauncherSetting 里（它本来就是启动器全局设置）。
        boolean preCheck = true;
        try {
            preCheck = this.activity.launcherSetting == null || this.activity.launcherSetting.preLaunchCheck;
        } catch (Throwable ignored) {
        }
        this.container.addView(toggleRow(
                this.context.getString(R.string.auto_task_pre_launch_check),
                this.context.getString(R.string.auto_task_pre_launch_check_hint),
                preCheck,
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            activity.launcherSetting.preLaunchCheck = !activity.launcherSetting.preLaunchCheck;
                            com.qcl.launcher.utils.gson.GsonUtils.saveLauncherSetting(
                                    activity.launcherSetting,
                                    com.qcl.launcher.manifest.AppManifest.SETTING_DIR + "/launcher_setting.json");
                        } catch (Throwable ignored) {
                        }
                        rebuild();
                    }
                }));

        this.container.addView(toggleRow(
                this.context.getString(R.string.auto_task_after_crash),
                this.context.getString(R.string.auto_task_after_crash_hint),
                s.backupAfterCrash,
                new Runnable() {
                    @Override
                    public void run() {
                        AutoTaskRunner.Setting cur = AutoTaskRunner.get(context);
                        cur.backupAfterCrash = !cur.backupAfterCrash;
                        AutoTaskRunner.save(context, cur);
                        rebuild();
                    }
                }));

        // ★★★ 社区版新增：退出后自动备份存档（#28）。默认关 —— 存档可能几百 MB，
        //   每退一次游戏就复制一份，磁盘消耗很实在，必须由用户自己开。
        this.container.addView(toggleRow(
                this.context.getString(R.string.auto_task_world_backup),
                this.context.getString(R.string.auto_task_world_backup_hint),
                s.backupWorldsOnExit,
                new Runnable() {
                    @Override
                    public void run() {
                        AutoTaskRunner.Setting cur = AutoTaskRunner.get(context);
                        cur.backupWorldsOnExit = !cur.backupWorldsOnExit;
                        AutoTaskRunner.save(context, cur);
                        rebuild();
                    }
                }));

        // ★ 存档备份保留份数。★ 与「实例备份保留份数」不同，这里**默认给 3** ——
        //   因为这项只在用户主动开启「退出后自动备份」之后才生效，
        //   而既然开了每次退出都备份，就必须有个自动回收，否则几天就把磁盘写满。
        this.container.addView(cycleRow(
                this.context.getString(R.string.auto_task_world_keep),
                this.context.getString(R.string.auto_task_world_keep_hint),
                worldKeepLabel(s.keepWorldBackups),
                new Runnable() {
                    @Override
                    public void run() {
                        AutoTaskRunner.Setting cur = AutoTaskRunner.get(context);
                        cur.keepWorldBackups = next(WORLD_KEEP_VALUES, cur.keepWorldBackups);
                        AutoTaskRunner.save(context, cur);
                        rebuild();
                    }
                }));

        this.container.addView(toggleRow(
                this.context.getString(R.string.auto_task_clean_cache),
                this.context.getString(R.string.auto_task_clean_cache_hint),
                s.cleanCacheOnSchedule,
                new Runnable() {
                    @Override
                    public void run() {
                        AutoTaskRunner.Setting cur = AutoTaskRunner.get(context);
                        cur.cleanCacheOnSchedule = !cur.cleanCacheOnSchedule;
                        AutoTaskRunner.save(context, cur);
                        rebuild();
                    }
                }));

        this.container.addView(cycleRow(
                this.context.getString(R.string.auto_task_keep),
                this.context.getString(R.string.auto_task_keep_hint),
                keepLabel(s.keepBackups),
                new Runnable() {
                    @Override
                    public void run() {
                        AutoTaskRunner.Setting cur = AutoTaskRunner.get(context);
                        cur.keepBackups = next(KEEP_VALUES, cur.keepBackups);
                        AutoTaskRunner.save(context, cur);
                        // 立刻按新份数裁剪一次，让「改了就生效」可见
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    AutoTaskRunner.onLauncherOpened(context, null);
                                } catch (Throwable ignored) {
                                }
                            }
                        }).start();
                        rebuild();
                    }
                }));

        // ★ 社区版新增：启动前 / 退出后自定义脚本。
        //   ★ 注意：脚本字段存在 LauncherSetting（全局启动器设置）里，
        //     不是 AutoTaskRunner.Setting —— LaunchScriptHelper 也从那边读，必须同源。
        this.container.addView(cycleRow(
                this.context.getString(R.string.auto_task_pre_script),
                this.context.getString(R.string.auto_task_script_hint),
                scriptLabel(preScript()),
                new Runnable() {
                    @Override
                    public void run() {
                        showScriptDialog(true);
                    }
                }));

        this.container.addView(cycleRow(
                this.context.getString(R.string.auto_task_post_script),
                this.context.getString(R.string.auto_task_script_hint),
                scriptLabel(postScript()),
                new Runnable() {
                    @Override
                    public void run() {
                        showScriptDialog(false);
                    }
                }));

        // ★ 社区版新增：桌面快捷方式（一键启动当前实例）。
        //   与上面的「脚本」放同一页：都属「让启动器按你的方式自动做事」。
        this.container.addView(cycleRow(
                this.context.getString(R.string.shortcut_row_title),
                this.context.getString(R.string.shortcut_row_hint),
                this.context.getString(R.string.shortcut_pin),
                new Runnable() {
                    @Override
                    public void run() {
                        createShortcut();
                    }
                }));

        refreshStatus(s);
    }

    /** 创建桌面快捷方式。桌面不支持时如实告知，不假装成功。 */
    private void createShortcut() {
        try {
            String version = currentVersionPath();
            if (version == null || version.isEmpty()) {
                toast(R.string.shortcut_no_instance);
                return;
            }
            String name = new File(version).getName();
            if (!com.qcl.launcher.launcher.shortcut.ShortcutHelper.isSupported(this.context)) {
                toast(R.string.shortcut_unsupported);
                return;
            }
            boolean ok = com.qcl.launcher.launcher.shortcut.ShortcutHelper
                    .pinLaunchShortcut(this.context, name);
            toast(ok ? R.string.shortcut_created : R.string.shortcut_unsupported);
        } catch (Throwable t) {
            toast(R.string.shortcut_unsupported);
        }
    }

    private String preScript() {
        try {
            return this.activity.launcherSetting.preLaunchScript;
        } catch (Throwable t) {
            return "";
        }
    }

    private String postScript() {
        try {
            return this.activity.launcherSetting.postExitScript;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 保存脚本到 LauncherSetting 并落盘（与 DownloadSettingUI 用同一套持久化）。 */
    private void saveScript(boolean pre, String text) {
        try {
            if (pre) {
                this.activity.launcherSetting.preLaunchScript = text;
            } else {
                this.activity.launcherSetting.postExitScript = text;
            }
            com.qcl.launcher.utils.gson.GsonUtils.saveLauncherSetting(
                    this.activity.launcherSetting,
                    com.qcl.launcher.manifest.AppManifest.SETTING_DIR + "/launcher_setting.json");
        } catch (Throwable ignored) {
        }
    }

    /** 脚本摘要：空 = 未设置；否则截断显示，避免超长脚本把行撑开。 */
    private String scriptLabel(String script) {
        if (script == null || script.trim().isEmpty()) {
            return this.context.getString(R.string.auto_task_script_empty);
        }
        String oneLine = script.replace('\n', ' ').trim();
        return oneLine.length() > 18 ? oneLine.substring(0, 18) + "…" : oneLine;
    }

    /**
     * ★ 社区版新增：脚本编辑对话框。
     * 用 EditText 直接编辑整段脚本；留空即等于「不执行」。
     */
    private void showScriptDialog(final boolean pre) {
        try {
            final android.widget.EditText input = new android.widget.EditText(this.context);
            input.setText(pre ? preScript() : postScript());
            input.setHint(R.string.auto_task_script_placeholder);
            input.setTextSize(12f);
            input.setMinLines(3);
            input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);

            new android.app.AlertDialog.Builder(this.activity)
                    .setTitle(pre ? R.string.auto_task_pre_script : R.string.auto_task_post_script)
                    .setView(input)
                    .setPositiveButton(R.string.auto_task_script_ok,
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    String text = input.getText() == null ? "" : input.getText().toString();
                                    saveScript(pre, text);
                                    rebuild();
                                }
                            })
                    .setNegativeButton(R.string.legacy_cn_lang_cancel, null)
                    .create().show();
        } catch (Throwable ignored) {
        }
    }

    private void refreshStatus(AutoTaskRunner.Setting s) {
        if (this.status == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(this.context.getString(R.string.auto_task_last_run))
                .append(": ")
                .append(s.lastScheduledRunAt <= 0L
                        ? this.context.getString(R.string.auto_task_never)
                        : InstanceBackupHelper.formatTime(s.lastScheduledRunAt));
        try {
            List<BackupRecord> records = InstanceBackupHelper.listRecords();
            long total = 0L;
            for (BackupRecord r : records) {
                total += r.sizeBytes;
            }
            sb.append('\n')
                    .append(this.context.getString(R.string.auto_task_backup_count,
                            records.size(), InstanceBackupHelper.formatSize(total)));
        } catch (Throwable ignored) {
        }
        sb.append('\n').append(this.context.getString(R.string.auto_task_timing_note));
        this.status.setText(sb.toString());
    }

    // ------------------------------------------------------------------ 立即执行

    private void runNow() {
        final String version = currentVersionPath();
        if (version == null || version.isEmpty()) {
            toast(R.string.backup_create_none);
            return;
        }
        toast(R.string.backup_working);
        new Thread(new Runnable() {
            @Override
            public void run() {
                // ★ 抽成独立方法而不是在 try/catch 里给 final 变量赋值 ——
                //   Java 不允许 blank final 同时在 try 与 catch 里赋值
                //   （编译器无法证明 catch 时它一定未被赋过值）。
                final boolean ok = createManualBackup(version);
                AutoTaskUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rebuild();
                        Toast.makeText(AutoTaskUI.this.activity,
                                ok ? R.string.auto_task_run_done : R.string.auto_task_run_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    /** 手动备份当前实例。返回是否成功；任何异常都当作失败，绝不外抛。 */
    private boolean createManualBackup(String versionPath) {
        try {
            File dir = new File(versionPath);
            return InstanceBackupHelper.createBackup(
                    dir.getName() + " (manual)", dir, null) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 行构建

    private View toggleRow(String title, String hint, boolean on, final Runnable onClick) {
        return row(title, hint, on ? this.context.getString(R.string.auto_task_on)
                                   : this.context.getString(R.string.auto_task_off), on, onClick);
    }

    private View cycleRow(String title, String hint, String value, final Runnable onClick) {
        return row(title, hint, value, false, onClick);
    }

    private View row(String title, String hint, String value, boolean on, final Runnable onClick) {
        LinearLayout box = new LinearLayout(this.context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackgroundResource(R.drawable.qcl_button_gray);
        box.setPadding(px(10), px(10), px(10), px(10));
        box.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        box.setLayoutParams(lp);
        box.setClickable(true);
        box.setFocusable(true);
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    onClick.run();
                } catch (Throwable ignored) {
                }
            }
        });

        LinearLayout info = new LinearLayout(this.context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this.context);
        t.setText(title);
        t.setTextSize(13f);
        t.setTextColor(Color.BLACK);
        info.addView(t);

        if (hint != null && !hint.isEmpty()) {
            TextView h = new TextView(this.context);
            h.setText(hint);
            h.setTextSize(10f);
            h.setTextColor(Color.parseColor("#99000000"));
            info.addView(h);
        }
        box.addView(info);

        TextView v = new TextView(this.context);
        v.setText(value);
        v.setTextSize(13f);
        v.setTextColor(on ? Color.parseColor("#FF185FA5") : Color.parseColor("#99000000"));
        v.setPadding(px(8), px(4), px(4), px(4));
        box.addView(v);
        return box;
    }

    // ------------------------------------------------------------------ 工具

    private String intervalLabel(int days) {
        if (days <= 0) {
            return this.context.getString(R.string.auto_task_off);
        }
        if (days == 1) {
            return this.context.getString(R.string.auto_task_every_day);
        }
        if (days == 7) {
            return this.context.getString(R.string.auto_task_every_week);
        }
        return this.context.getString(R.string.auto_task_every_month);
    }

    private String keepLabel(int keep) {
        return keep <= 0 ? this.context.getString(R.string.auto_task_keep_unlimited)
                         : String.valueOf(keep);
    }

    /** 存档备份保留份数的显示（每份 = 每个世界各留几份）。 */
    private String worldKeepLabel(int keep) {
        return keep <= 0 ? this.context.getString(R.string.auto_task_keep_unlimited)
                         : this.context.getString(R.string.auto_task_world_keep_value, keep);
    }

    /** 在候选数组里取「下一个」值；当前值不在候选里时回到第一个。 */
    private static int next(int[] options, int current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i] == current) {
                return options[(i + 1) % options.length];
            }
        }
        return options[0];
    }

    private String currentVersionPath() {
        try {
            return this.activity.publicGameSetting.currentVersion;
        } catch (Throwable t) {
            return null;
        }
    }

    private int px(int dp) {
        return Math.round(dp * this.context.getResources().getDisplayMetrics().density);
    }

    private void toast(int res) {
        try {
            Toast.makeText(this.context, res, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
