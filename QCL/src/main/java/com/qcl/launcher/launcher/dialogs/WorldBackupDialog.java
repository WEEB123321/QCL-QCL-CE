package com.qcl.launcher.launcher.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.backup.InstanceBackupHelper;
import com.qcl.launcher.launcher.backup.world.WorldBackupHelper;
import com.qcl.launcher.launcher.backup.world.WorldBackupRecord;
import com.qcl.launcher.launcher.game.World;

import java.io.File;
import java.util.List;

/**
 * ★ 社区版新增：单个存档的版本管理弹窗（备份 / 恢复 / 删除）。
 *
 * <p><b>★ 为什么是弹窗而不是新开一个页面</b>：本项目的导航是「25 个页面预加载 +
 * 显隐切换」，加一个页面要动 {@code activity_main.xml}、{@code UIManager}、
 * {@code MainActivity} 三处。而存档版本管理是<b>低频、且天然属于某个世界</b>的操作 ——
 * 从世界菜单点进来，做完就关，弹窗是更合适的形态，也不给导航结构增加负担。
 *
 * <p><b>★ 交互设计（低学习成本）</b>：整屏只有三件事 —— 上面一个「备份当前存档」大按钮，
 * 下面一串快照，每条只有「恢复 / 删除」。不引入任何需要理解的抽象概念。
 *
 * <p><b>★ 耗时操作全部走后台线程</b>：备份/恢复是整目录拷贝，可能几十秒，绝不能在主线程做。
 */
public class WorldBackupDialog extends Dialog implements View.OnClickListener {

    private final MainActivity activity;
    private final World world;
    /** 存档目录名 —— 备份按它分组（世界可以在游戏里改名，目录名不会） */
    private final String worldKey;

    private LinearLayout listContainer;
    private TextView emptyHint;
    private TextView titleText;

    /** 防止连点造成并发拷贝（两个线程同时写同一个存档目录 = 数据损坏） */
    private volatile boolean working = false;

    private WorldBackupDialog(@NonNull Context context, MainActivity activity, World world, String worldKey) {
        super(context);
        this.activity = activity;
        this.world = world;
        this.worldKey = worldKey;
        buildUi(context);
        refreshList();
    }

    /** 入口：从世界列表的长按/更多菜单调用。 */
    public static void show(Context context, MainActivity activity, World world) {
        if (world == null || world.getFile() == null) {
            return;
        }
        File dir = world.getFile().toFile();
        if (dir == null || !dir.isDirectory()) {
            Toast.makeText(context, R.string.world_backup_none_target, Toast.LENGTH_SHORT).show();
            return;
        }
        new WorldBackupDialog(context, activity, world, dir.getName()).show();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi(Context context) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        titleText = new TextView(context);
        String name = world.getWorldName();
        titleText.setText(context.getString(R.string.world_backup_title)
                + (name == null || name.isEmpty() ? "" : " · " + name));
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleText.setTextColor(Color.BLACK);
        titleText.setGravity(Gravity.START);
        root.addView(titleText);

        // 「备份当前存档」——整屏最大的按钮，主要动作只此一个
        TextView create = new TextView(context);
        create.setText(R.string.world_backup_create);
        create.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        create.setTextColor(Color.WHITE);
        create.setGravity(Gravity.CENTER);
        create.setBackgroundResource(R.drawable.launcher_button_white);
        create.setBackgroundColor(Color.parseColor("#FF185FA5"));
        create.setPadding(0, dp(14), 0, dp(14));
        create.setClickable(true);
        create.setFocusable(true);
        create.setOnClickListener(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(12);
        root.addView(create, cp);

        emptyHint = new TextView(context);
        emptyHint.setText(R.string.world_backup_empty);
        emptyHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        emptyHint.setTextColor(Color.parseColor("#99000000"));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(12);
        root.addView(emptyHint, ep);

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        root.addView(listContainer, lp);

        setContentView(scroll);
        setCancelable(true);
        Window w = getWindow();
        if (w != null) {
            WindowManager.LayoutParams params = w.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            w.setAttributes(params);
        }
    }

    private void refreshList() {
        if (listContainer == null) {
            return;
        }
        listContainer.removeAllViews();
        List<WorldBackupRecord> records = WorldBackupHelper.listRecords(gameDir(), worldKey);
        if (emptyHint != null) {
            emptyHint.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        }
        for (WorldBackupRecord r : records) {
            listContainer.addView(buildRow(r));
        }
    }

    private View buildRow(final WorldBackupRecord record) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.qcl_button_gray);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(6);
        row.setLayoutParams(rp);

        LinearLayout info = new LinearLayout(getContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(getContext());
        // 自动产生的「恢复前备份」要标出来，否则玩家会疑惑「这条哪来的」
        String label = InstanceBackupHelper.formatTime(record.createdAt);
        if (record.autoBeforeRestore) {
            label = label + "（" + getContext().getString(R.string.backup_restore_safety) + "）";
        }
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        title.setTextColor(Color.BLACK);
        info.addView(title);

        TextView meta = new TextView(getContext());
        meta.setText(InstanceBackupHelper.formatSize(record.sizeBytes));
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        meta.setTextColor(Color.parseColor("#99000000"));
        info.addView(meta);

        row.addView(info);

        TextView restore = actionText(getContext().getString(R.string.world_backup_restore));
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRestore(record);
            }
        });
        row.addView(restore);

        TextView delete = actionText(getContext().getString(R.string.world_backup_delete));
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(record);
            }
        });
        row.addView(delete);

        return row;
    }

    private TextView actionText(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setTextColor(Color.parseColor("#FF185FA5"));
        tv.setPadding(dp(12), dp(6), dp(6), dp(6));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    // ------------------------------------------------------------------ 动作

    @Override
    public void onClick(View view) {
        doBackup();
    }

    private void doBackup() {
        if (working) {
            return;
        }
        final File dir = world.getFile() == null ? null : world.getFile().toFile();
        if (dir == null || !dir.isDirectory()) {
            Toast.makeText(getContext(), R.string.world_backup_none_target, Toast.LENGTH_SHORT).show();
            return;
        }
        final File gd = gameDir();
        final String name = world.getWorldName();
        working = true;
        toast(R.string.world_backup_working);
        new Thread(new Runnable() {
            @Override
            public void run() {
                WorldBackupRecord r = WorldBackupHelper.createBackup(gd, worldKey, name, dir, false, null);
                working = false;
                final boolean ok = r != null;
                // 注意：这里必须写 WorldBackupDialog.this —— 匿名 Runnable 里的 this 是 Runnable 自己
                WorldBackupDialog.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshList();
                        Toast.makeText(WorldBackupDialog.this.activity,
                                ok ? WorldBackupDialog.this.activity.getString(R.string.world_backup_created, worldKey)
                                   : WorldBackupDialog.this.activity.getString(R.string.world_backup_failed, worldKey),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void confirmRestore(final WorldBackupRecord record) {
        if (working) {
            return;
        }
        new AlertDialog.Builder(getContext())
                .setMessage(getContext().getString(R.string.world_backup_restore_confirm, worldKey))
                .setPositiveButton(R.string.world_backup_restore, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        doRestore(record);
                    }
                })
                .setNegativeButton(R.string.legacy_cn_lang_cancel, null)
                .create().show();
    }

    private void doRestore(final WorldBackupRecord record) {
        final File dir = world.getFile() == null ? null : world.getFile().toFile();
        if (dir == null) {
            Toast.makeText(getContext(), R.string.world_backup_none_target, Toast.LENGTH_SHORT).show();
            return;
        }
        final File gd = gameDir();
        working = true;
        toast(R.string.world_backup_working);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = WorldBackupHelper.restore(gd, record, dir, null);
                working = false;
                final boolean done = ok;
                WorldBackupDialog.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshList();
                        Toast.makeText(WorldBackupDialog.this.activity,
                                done ? WorldBackupDialog.this.activity.getString(R.string.world_backup_restore_done, worldKey)
                                     : WorldBackupDialog.this.activity.getString(R.string.world_backup_restore_failed, worldKey),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void confirmDelete(final WorldBackupRecord record) {
        if (working) {
            return;
        }
        new AlertDialog.Builder(getContext())
                .setMessage(getContext().getString(R.string.world_backup_delete_confirm,
                        InstanceBackupHelper.formatSize(record.sizeBytes)))
                .setPositiveButton(R.string.world_backup_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        WorldBackupHelper.delete(gameDir(), record);
                        refreshList();
                    }
                })
                .setNegativeButton(R.string.legacy_cn_lang_cancel, null)
                .create().show();
    }

    // ------------------------------------------------------------------ 工具

    private File gameDir() {
        try {
            return new File(this.activity.launcherSetting.gameFileDirectory);
        } catch (Throwable t) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }

    private void toast(int res) {
        Toast.makeText(getContext(), res, Toast.LENGTH_SHORT).show();
    }
}
