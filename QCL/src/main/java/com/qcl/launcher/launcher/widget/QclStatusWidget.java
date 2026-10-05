package com.qcl.launcher.launcher.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.backup.BackupRecord;
import com.qcl.launcher.launcher.backup.InstanceBackupHelper;
import com.qcl.launcher.launcher.setting.game.PublicGameSetting;
import com.qcl.launcher.launcher.stats.StatsTracker;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.gson.GsonUtils;

import java.io.File;
import java.util.List;

/**
 * ★★★ 社区版新增：QCL 桌面小部件。
 *
 * <p><b>它显示什么</b>：当前实例名、总游戏时长 / 启动次数、备份份数。
 * <b>点小部件本体</b>打开启动器；<b>点最下面那行</b>直接一键启动当前实例
 * （等价于点主界面的「启动」，走的是同一条路径）。
 *
 * <p><b>★ 为什么不按周期自动刷新</b>：{@code updatePeriodMillis=0} 是刻意的。
 * 系统周期唤醒最少 30 分钟一次，对「看一眼状态」这种需求完全没必要，
 * 却要一直付电费。改成：<b>启动器每次打开时刷一次</b>（{@link #refreshAll}），
 * 加上点小部件时也刷一次。数据本来就是「打开启动器才会变」的东西。
 *
 * <p><b>★ 全程不抛异常</b>：小部件跑在启动器进程里，它抛异常 = 启动器崩。
 * 所有读取都包在 try/catch 里，失败就显示占位符。
 */
public class QclStatusWidget extends AppWidgetProvider {

    private static final String TAG = "QCLWidget";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        try {
            for (int id : appWidgetIds) {
                manager.updateAppWidget(id, build(context));
            }
        } catch (Throwable t) {
            Log.w(TAG, "更新小部件失败: " + t);
        }
    }

    /** 供启动器在数据变化时主动刷新（MainActivity 打开时调用）。 */
    public static void refreshAll(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            if (manager == null) {
                return;
            }
            ComponentName cn = new ComponentName(context, QclStatusWidget.class);
            int[] ids = manager.getAppWidgetIds(cn);
            if (ids == null || ids.length == 0) {
                return;   // 用户没放小部件，什么都不用做
            }
            RemoteViews views = build(context);
            for (int id : ids) {
                manager.updateAppWidget(id, views);
            }
        } catch (Throwable t) {
            Log.w(TAG, "刷新小部件失败: " + t);
        }
    }

    // ------------------------------------------------------------------ 构建

    private static RemoteViews build(Context context) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_qcl_status);
        try {
            v.setTextViewText(R.id.widget_instance, instanceLine(context));
            v.setTextViewText(R.id.widget_stats, statsLine(context));
            v.setTextViewText(R.id.widget_backups, backupsLine(context));

            // 点本体 → 打开启动器
            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            v.setOnClickPendingIntent(R.id.widget_root,
                    PendingIntent.getActivity(context, 100, open, pendingFlags()));

            // 点最后一行 → 一键启动当前实例（走 MainActivity 的 qcl_auto_launch 通道）
            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            launch.putExtra("qcl_auto_launch", true);
            v.setOnClickPendingIntent(R.id.widget_hint,
                    PendingIntent.getActivity(context, 101, launch, pendingFlags()));
        } catch (Throwable t) {
            Log.w(TAG, "构建小部件失败: " + t);
        }
        return v;
    }

    private static int pendingFlags() {
        // API 31+ 强制要求声明可变性；我们不需要别的应用改这个 intent，用 IMMUTABLE。
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    private static String instanceLine(Context context) {
        try {
            PublicGameSetting pub = GsonUtils.getPublicGameSettingFromFile(
                    AppManifest.SETTING_DIR + "/public_game_setting.json");
            if (pub == null || pub.currentVersion == null || pub.currentVersion.isEmpty()) {
                return context.getString(R.string.widget_no_instance);
            }
            return new File(pub.currentVersion).getName();
        } catch (Throwable t) {
            return context.getString(R.string.widget_no_instance);
        }
    }

    private static String statsLine(Context context) {
        try {
            StatsTracker.Stats s = StatsTracker.get(context);
            long min = s.totalPlayMs / 60000L;
            long h = min / 60L;
            long m = min % 60L;
            String time = h > 0 ? h + "h " + m + "m" : m + "m";
            return context.getString(R.string.widget_stats, time, s.launchCount);
        } catch (Throwable t) {
            return "-";
        }
    }

    private static String backupsLine(Context context) {
        try {
            List<BackupRecord> records = InstanceBackupHelper.listRecords();
            return context.getString(R.string.widget_backups, records.size());
        } catch (Throwable t) {
            return "-";
        }
    }
}
