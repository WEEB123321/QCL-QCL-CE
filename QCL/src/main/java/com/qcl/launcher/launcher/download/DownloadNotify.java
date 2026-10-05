package com.qcl.launcher.launcher.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.qcl.launcher.R;

/**
 * ★ 社区版新增：下载进度通知（通知栏 / 锁屏可见）。
 *
 * <p><b>★★★ 它到底解决什么问题 —— 别夸大</b>
 * 下载本身跑在 {@code DownloadTask}（一个 {@code AsyncTask}）里，<b>生命周期绑在应用进程上</b>。
 * 所以这个前台服务的真实作用是：<b>把进程优先级提上去，让用户切到别的应用时下载不被系统冻结/回收</b>，
 * 以及把进度放到通知栏让用户看得见。
 * 它<b>不</b>能让下载在「用户从最近任务划掉应用 / 强行停止」之后继续 —— 那种情况进程已经没了，
 * AsyncTask 也随之消失。要做到那一步得把整个下载引擎搬进 Service，属于重构，不在本轮范围。
 *
 * <p><b>★ 通知是「锦上添花」，绝不能因为它失败而带崩下载</b>：
 * Android 13+ 未授予 {@code POST_NOTIFICATIONS} 时通知会被静默丢弃（不报错）；
 * Android 14 起前台服务类型不匹配会抛异常。所有入口都做了兜底，
 * 最坏情况是「没有通知栏进度」，下载本身照常。
 *
 * <p><b>★ 动作按钮走 PendingIntent → Service → 静态监听器</b>：
 * 不注册独立的 BroadcastReceiver（那要额外声明 + 处理生命周期），
 * 直接让服务转发给正在显示的 {@code DownloadDialog}。同进程、零 IPC 成本。
 */
public final class DownloadNotify {

    private static final String TAG = "QCLDownloadNotify";

    static final String CHANNEL_ID = "qcl_download";
    static final int NOTIFICATION_ID = 0x5152;

    /** 通知栏按钮回调。由 {@code DownloadDialog} 实现。 */
    public interface ActionListener {
        /** 用户点了通知栏的「暂停 / 继续」 */
        void onTogglePauseFromNotify();

        /** 用户点了通知栏的「取消」 */
        void onCancelFromNotify();
    }

    private static volatile ActionListener listener;

    private static volatile boolean active = false;
    private static volatile boolean paused = false;
    /** 暂停原因是否为「仅 Wi-Fi」—— 决定暂停文案，不能只写「已暂停」让用户猜 */
    private static volatile boolean pausedByNetwork = false;
    private static volatile int percent = 0;
    private static volatile int doneFiles = 0;
    private static volatile int totalFiles = 0;
    private static volatile String speed = "";

    /** 节流：通知栏刷新太频繁既费电又会被系统限流，这里最多 800ms 一次。 */
    private static volatile long lastPostAt = 0L;
    private static final long MIN_POST_INTERVAL_MS = 800L;

    private DownloadNotify() {
    }

    // ------------------------------------------------------------------ 对外接口

    public static void setActionListener(ActionListener l) {
        listener = l;
    }

    public static boolean isActive() {
        return active;
    }

    /** 开始一次下载：起前台服务并挂上初始通知。 */
    public static void start(Context context, int totalFiles) {
        if (context == null) {
            return;
        }
        DownloadNotify.totalFiles = Math.max(totalFiles, 0);
        DownloadNotify.doneFiles = 0;
        DownloadNotify.percent = 0;
        DownloadNotify.speed = "";
        DownloadNotify.paused = false;
        DownloadNotify.pausedByNetwork = false;
        DownloadNotify.active = true;
        DownloadNotify.lastPostAt = 0L;
        try {
            createChannel(context);
            Intent intent = new Intent(context, DownloadForegroundService.class);
            intent.setAction(DownloadForegroundService.ACTION_START);
            ContextCompat.startForegroundService(context, intent);
        } catch (Throwable t) {
            // 通知发不出来（权限/系统限制）不影响下载
            Log.w(TAG, "启动下载前台服务失败，将只显示应用内进度: " + t);
        }
    }

    /** 更新进度。会被节流；百分比变化足够大时立即刷新。 */
    public static void update(Context context, int overallPercent, int doneFiles, String speed) {
        if (!active || context == null) {
            return;
        }
        int prev = DownloadNotify.percent;
        DownloadNotify.percent = Math.max(0, Math.min(100, overallPercent));
        DownloadNotify.doneFiles = doneFiles;
        if (speed != null) {
            DownloadNotify.speed = speed;
        }
        long now = System.currentTimeMillis();
        if (DownloadNotify.percent == prev && now - lastPostAt < MIN_POST_INTERVAL_MS) {
            return;
        }
        if (now - lastPostAt < MIN_POST_INTERVAL_MS && DownloadNotify.percent - prev < 1) {
            return;
        }
        post(context);
    }

    /** 同步暂停状态。{@code byNetwork} 为 true 时文案会说明「当前不是 Wi-Fi」，而不是干巴巴一句「已暂停」。 */
    public static void setPaused(Context context, boolean paused, boolean byNetwork) {
        if (!active || context == null) {
            return;
        }
        DownloadNotify.paused = paused;
        DownloadNotify.pausedByNetwork = byNetwork;
        post(context);
    }

    /** 结束：撤掉前台服务与通知。无论成功、失败还是取消都要调，否则通知会一直挂着。 */
    public static void finish(Context context) {
        active = false;
        paused = false;
        pausedByNetwork = false;
        listener = null;
        if (context == null) {
            return;
        }
        try {
            context.stopService(new Intent(context, DownloadForegroundService.class));
        } catch (Throwable t) {
            Log.w(TAG, "停止下载前台服务失败: " + t);
        }
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIFICATION_ID);
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ 服务回调

    static void dispatchTogglePause() {
        ActionListener l = listener;
        if (l == null) {
            return;
        }
        try {
            l.onTogglePauseFromNotify();
        } catch (Throwable t) {
            Log.w(TAG, "通知栏暂停回调异常: " + t);
        }
    }

    static void dispatchCancel() {
        ActionListener l = listener;
        if (l == null) {
            return;
        }
        try {
            l.onCancelFromNotify();
        } catch (Throwable t) {
            Log.w(TAG, "通知栏取消回调异常: " + t);
        }
    }

    // ------------------------------------------------------------------ 通知构建

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.download_notify_channel),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(context.getString(R.string.download_notify_channel_desc));
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        } catch (Throwable t) {
            Log.w(TAG, "创建下载通知渠道失败: " + t);
        }
    }

    /** 是否已获通知权限（Android 13+ 需要；低版本恒 true）。 */
    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        try {
            return ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static Notification build(Context context) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, com.qcl.launcher.launcher.MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent toggleIntent = serviceIntent(context,
                DownloadForegroundService.ACTION_TOGGLE_PAUSE, 1);
        PendingIntent cancelIntent = serviceIntent(context,
                DownloadForegroundService.ACTION_CANCEL, 2);

        builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.download_notify_title))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(contentIntent)
                .addAction(0, context.getString(paused
                        ? R.string.dialog_download_resume
                        : R.string.dialog_download_pause), toggleIntent)
                .addAction(0, context.getString(R.string.dialog_download_negative), cancelIntent);

        if (paused) {
            // 暂停时不显示进度条 —— 留着一条不动的进度条会让用户以为卡住了
            builder.setContentText(context.getString(pausedByNetwork
                    ? R.string.dialog_download_network_paused
                    : R.string.dialog_download_paused));
        } else {
            builder.setProgress(100, percent, false);
            builder.setContentText(context.getString(R.string.download_notify_progress,
                    doneFiles, totalFiles, percent) + (speed.isEmpty() ? "" : " · " + speed));
        }
        return builder.build();
    }

    private static PendingIntent serviceIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, DownloadForegroundService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void post(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            lastPostAt = System.currentTimeMillis();
            nm.notify(NOTIFICATION_ID, build(context));
        } catch (Throwable t) {
            Log.w(TAG, "刷新下载通知失败: " + t);
        }
    }
}
