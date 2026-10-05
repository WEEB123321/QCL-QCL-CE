package com.qcl.launcher.launcher.uis.backup;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.backup.BackupRecord;
import com.qcl.launcher.launcher.backup.InstanceBackupHelper;
import com.qcl.launcher.launcher.backup.InstanceDiffHelper;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import java.io.File;
import java.util.List;

/**
 * ★ 社区版新增：备份与恢复页。
 *
 * <p><b>收纳位置</b>：按需求书「低频功能收进更多」——本页不占底部导航，
 * 入口挂在「我的 → 备份与恢复」（SettingUI 里加一项跳转）。
 *
 * <p><b>★ 交互设计（低学习成本）</b>：整页只有两件事 —— 上面一个"备份当前实例"的大按钮，
 * 下面一个备份列表，每条只有"回滚 / 删除"两个动作。不引入任何需要理解的抽象概念。
 *
 * <p><b>★ 耗时操作全部走后台线程</b>：备份/回滚都是整目录拷贝，可能几十秒，
 * 绝不能在主线程做（会 ANR）。这里用 Thread + runOnUiThread 回主线程刷新。
 */
public class BackupUI extends BaseUI implements View.OnClickListener {

    private LinearLayout backupUI;
    private LinearLayout createButton;
    private TextView createTarget;
    private LinearLayout listContainer;
    private TextView emptyHint;

    /** 防止连点造成并发拷贝（两个线程同时写同一个实例目录 = 数据损坏） */
    private volatile boolean working = false;

    public BackupUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.backupUI = this.activity.findViewById(R.id.ui_backup);
        this.createButton = this.activity.findViewById(R.id.backup_create_button);
        this.createTarget = this.activity.findViewById(R.id.backup_create_target);
        this.listContainer = this.activity.findViewById(R.id.backup_list_container);
        this.emptyHint = this.activity.findViewById(R.id.backup_empty_hint);
        if (this.createButton != null) {
            this.createButton.setOnClickListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(
                this.context.getResources().getString(R.string.backup_ui_title),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.backupUI, this.activity, this.context, true);
        refreshTarget();
        refreshList();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.backupUI, this.activity, this.context, true);
    }

    /** 顶部按钮上显示"要备份哪个实例"，避免玩家点了才发现在备份别的东西 */
    private void refreshTarget() {
        if (createTarget == null) {
            return;
        }
        String version = currentVersionPath();
        if (version == null || version.isEmpty()) {
            createTarget.setText(R.string.backup_create_none);
        } else {
            createTarget.setText(instanceNameOf(version));
        }
    }

    private void refreshList() {
        if (listContainer == null) {
            return;
        }
        listContainer.removeAllViews();
        List<BackupRecord> records = InstanceBackupHelper.listRecords();
        if (emptyHint != null) {
            emptyHint.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        }
        for (BackupRecord r : records) {
            listContainer.addView(buildRow(r));
        }
    }

    /** 构建一条备份：实例名 / 时间 / 体积 + 回滚 + 删除 */
    private View buildRow(final BackupRecord record) {
        LinearLayout row = new LinearLayout(this.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.qcl_button_gray);
        row.setPadding(px(10), px(10), px(10), px(10));
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = px(6);
        row.setLayoutParams(rp);

        LinearLayout info = new LinearLayout(this.context);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        info.setLayoutParams(ip);

        TextView title = new TextView(this.context);
        title.setText(record.instanceName == null ? record.id : record.instanceName);
        title.setTextSize(13f);
        title.setTextColor(Color.BLACK);
        info.addView(title);

        TextView meta = new TextView(this.context);
        meta.setText(InstanceBackupHelper.formatTime(record.createdAt)
                + " · " + InstanceBackupHelper.formatSize(record.sizeBytes));
        meta.setTextSize(10f);
        meta.setTextColor(Color.parseColor("#99000000"));
        info.addView(meta);

        row.addView(info);

        TextView rollback = actionText(this.context.getString(R.string.backup_rollback));
        rollback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRollback(record);
            }
        });
        row.addView(rollback);

        // ★ 社区版新增：快照对比（这个快照 vs 当前实例差了什么）
        TextView diff = actionText(this.context.getString(R.string.backup_diff));
        diff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDiff(record);
            }
        });
        row.addView(diff);

        TextView delete = actionText(this.context.getString(R.string.backup_delete));
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(record);
            }
        });
        row.addView(delete);

        return row;
    }

    // ------------------------------------------------------------------ 快照对比

    /**
     * ★ 社区版新增：把「快照 → 当前实例」的差异列出来。
     * <p>走后台线程：要遍历两个实例目录（可能几万文件），主线程做必 ANR。
     */
    private void showDiff(final BackupRecord record) {
        final String versionPath = currentVersionPath();
        if (versionPath == null || versionPath.isEmpty()) {
            Toast.makeText(this.context, R.string.backup_create_none, Toast.LENGTH_SHORT).show();
            return;
        }
        toast(R.string.backup_diff_working);
        final String label = record.instanceName == null ? record.id : record.instanceName;
        new Thread(new Runnable() {
            @Override
            public void run() {
                // ★ 抽成方法：Java 不允许 blank final 同时在 try 与 catch 里赋值
                final String text = buildDiffText(record, versionPath, label);
                BackupUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showDiffDialog(text);
                    }
                });
            }
        }).start();
    }

    /** 计算对比文本。任何异常都转成一段可读文本返回，绝不外抛。 */
    private String buildDiffText(BackupRecord record, String versionPath, String label) {
        try {
            InstanceDiffHelper.Diff d = InstanceDiffHelper.diff(
                    new File(record.backupDir), new File(versionPath));
            return InstanceDiffHelper.render(d, label,
                    this.context.getString(R.string.backup_diff_current), 60);
        } catch (Throwable t) {
            return String.valueOf(t);
        }
    }

    private void showDiffDialog(String text) {
        try {
            TextView tv = new TextView(this.context);
            tv.setText(text);
            tv.setTextSize(10f);
            tv.setTextColor(Color.BLACK);
            tv.setTextIsSelectable(true);
            tv.setPadding(px(12), px(12), px(12), px(12));
            ScrollView sv = new ScrollView(this.context);
            sv.addView(tv);
            new AlertDialog.Builder(this.activity)
                    .setTitle(R.string.backup_diff)
                    .setView(sv)
                    .setPositiveButton(R.string.backup_diff_close, null)
                    .create().show();
        } catch (Throwable ignored) {
        }
    }

    private TextView actionText(String text) {
        TextView tv = new TextView(this.context);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(Color.parseColor("#FF185FA5"));
        tv.setPadding(px(12), px(6), px(6), px(6));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    // ------------------------------------------------------------------ 备份

    @Override
    public void onClick(View view) {
        if (view == this.createButton) {
            doBackup();
        }
    }

    private void doBackup() {
        if (working) {
            return;
        }
        final String versionPath = currentVersionPath();
        if (versionPath == null || versionPath.isEmpty()) {
            Toast.makeText(this.context, R.string.backup_create_none, Toast.LENGTH_SHORT).show();
            return;
        }
        final File versionDir = new File(versionPath);
        if (!versionDir.isDirectory()) {
            Toast.makeText(this.context, R.string.backup_create_none, Toast.LENGTH_SHORT).show();
            return;
        }
        final String name = instanceNameOf(versionPath);
        working = true;
        toast(R.string.backup_working);
        new Thread(new Runnable() {
            @Override
            public void run() {
                BackupRecord r = InstanceBackupHelper.createBackup(name, versionDir, null);
                working = false;
                final boolean ok = r != null;
                // 注意：这里必须写 BackupUI.this —— 匿名 Runnable 里的 this 是 Runnable 自己
                BackupUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshList();
                        Toast.makeText(BackupUI.this.activity,
                                ok ? BackupUI.this.activity.getString(R.string.backup_created, name)
                                   : BackupUI.this.activity.getString(R.string.backup_failed, name),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ 回滚 / 删除

    private void confirmRollback(final BackupRecord record) {
        if (working) {
            return;
        }
        new AlertDialog.Builder(this.activity)
                .setMessage(this.context.getString(R.string.backup_rollback_confirm,
                        record.instanceName == null ? record.id : record.instanceName))
                .setPositiveButton(R.string.backup_rollback, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        doRollback(record);
                    }
                })
                .setNegativeButton(R.string.legacy_cn_lang_cancel, null)
                .create().show();
    }

    private void doRollback(final BackupRecord record) {
        final String versionPath = currentVersionPath();
        if (versionPath == null || versionPath.isEmpty()) {
            Toast.makeText(this.context, R.string.backup_create_none, Toast.LENGTH_SHORT).show();
            return;
        }
        working = true;
        toast(R.string.backup_working);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = InstanceBackupHelper.rollback(record, new File(versionPath), null);
                working = false;
                final boolean done = ok;
                // 同样必须用 BackupUI.this，匿名 Runnable 的 this 不是外部类
                BackupUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshList();
                        String label = record.instanceName == null ? record.id : record.instanceName;
                        Toast.makeText(BackupUI.this.activity,
                                done ? BackupUI.this.activity.getString(R.string.backup_rollback_done, label)
                                     : BackupUI.this.activity.getString(R.string.backup_rollback_failed, label),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void confirmDelete(final BackupRecord record) {
        if (working) {
            return;
        }
        new AlertDialog.Builder(this.activity)
                .setMessage(this.context.getString(R.string.backup_delete_confirm,
                        InstanceBackupHelper.formatSize(record.sizeBytes)))
                .setPositiveButton(R.string.backup_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        InstanceBackupHelper.delete(record);
                        refreshList();
                    }
                })
                .setNegativeButton(R.string.legacy_cn_lang_cancel, null)
                .create().show();
    }

    // ------------------------------------------------------------------ 工具

    private String currentVersionPath() {
        try {
            return this.activity.publicGameSetting.currentVersion;
        } catch (Throwable t) {
            return null;
        }
    }

    private String instanceNameOf(String path) {
        if (path == null) {
            return "";
        }
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private int px(int dp) {
        return Math.round(dp * this.context.getResources().getDisplayMetrics().density);
    }

    private void toast(int res) {
        Toast.makeText(this.context, res, Toast.LENGTH_SHORT).show();
    }
}
