package com.qcl.launcher.launcher.download;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * ★ 社区版新增：下载前台服务 —— 只是「通知 + 进程优先级」的壳，不承载下载逻辑。
 *
 * <p><b>★ 为什么不把下载搬进来</b>：下载引擎是 {@code DownloadTask}（{@code AsyncTask}），
 * 与 {@code DownloadDialog}、进度列表、失败重试的 UI 状态深度耦合。搬进 Service 等于
 * 把「下载 + 下载 UI」整体重构一遍，风险远大于收益。本服务只负责两件事：
 * <ol>
 *   <li>持有前台通知（让进度在通知栏可见）；</li>
 *   <li>把通知栏按钮的点击转发回 {@link DownloadNotify}（从而回到正在显示的对话框）。</li>
 * </ol>
 * 前台服务顺带把进程优先级提上去，用户切到别的应用时下载不会被轻易回收 —— 这是它真正的价值。
 *
 * <p><b>★ 关于前台服务类型</b>：Android 14（API 34）起 {@code foregroundServiceType} 是强制的，
 * 漏写会抛 {@code MissingForegroundServiceTypeException}。下载属于数据同步，用 {@code dataSync}，
 * 并需要清单里的 {@code FOREGROUND_SERVICE_DATA_SYNC} 权限（API 34 起新增）。
 */
public class DownloadForegroundService extends Service {

    private static final String TAG = "QCLDownloadService";

    public static final String ACTION_START = "com.qcl.launcher.download.START";
    public static final String ACTION_TOGGLE_PAUSE = "com.qcl.launcher.download.TOGGLE_PAUSE";
    public static final String ACTION_CANCEL = "com.qcl.launcher.download.CANCEL";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE_PAUSE.equals(action)) {
            DownloadNotify.dispatchTogglePause();
            // 暂停状态由对话框回写（它才知道是被用户暂停还是被网络暂停），这里不擅自改
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            DownloadNotify.dispatchCancel();
            return START_NOT_STICKY;
        }
        // 默认（含 ACTION_START）：进入前台并挂上进度通知
        try {
            DownloadNotify.createChannel(this);
            Notification n = DownloadNotify.build(this);
            startForeground(DownloadNotify.NOTIFICATION_ID, n);
        } catch (Throwable t) {
            // ★ 前台服务启动失败必须 stopSelf()：用 startForegroundService 起的服务
            //   若没在 5 秒内调用 startForeground，系统会直接杀掉整个应用进程（ANR 级），
            //   那会把正在进行的下载一起带走 —— 比「没有通知」严重得多。
            Log.w(TAG, "进入前台失败，主动停止服务以免系统杀进程: " + t);
            stopSelf();
        }
        return START_NOT_STICKY;
    }
}
