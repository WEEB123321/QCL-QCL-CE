/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.AlertDialog$Builder
 *  android.app.Dialog
 *  android.content.Context
 *  android.os.AsyncTask
 *  android.os.AsyncTask$Status
 *  android.os.Handler
 *  android.os.Handler$Callback
 *  android.os.Message
 *  android.view.View
 *  android.view.View$OnClickListener
 *  android.widget.Button
 *  android.widget.TextView
 *  android.widget.Toast
 *  androidx.annotation.NonNull
 *  androidx.recyclerview.widget.LinearLayoutManager
 *  androidx.recyclerview.widget.RecyclerView
 *  androidx.recyclerview.widget.RecyclerView$Adapter
 *  androidx.recyclerview.widget.RecyclerView$LayoutManager
 *  androidx.recyclerview.widget.SimpleItemAnimator
 */
package com.qcl.launcher.launcher.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.download.DownloadNotify;
import com.qcl.launcher.launcher.list.install.DownloadTaskListAdapter;
import com.qcl.launcher.launcher.list.install.DownloadTaskListBean;
import com.qcl.launcher.task.DownloadTask;
import com.qcl.launcher.utils.io.NetSpeed;
import com.qcl.launcher.utils.io.NetSpeedTimer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import com.qcl.launcher.R;
public class DownloadDialog
extends Dialog
implements View.OnClickListener,
Handler.Callback,
DownloadNotify.ActionListener {
    private MainActivity activity;
    private ArrayList<DownloadTaskListBean> list;
    private boolean alert;
    private RecyclerView taskListView;
    private DownloadTaskListAdapter downloadTaskListAdapter;
    private NetSpeedTimer netSpeedTimer;
    private TextView speedText;
    private Button cancelButton;

    /**
     * ★ 1.4.3：暂停 / 继续 / 重试。
     *   暂停按钮文案在「暂停 / 继续」之间切换，不做两个按钮 —— 同一个动作的两个方向，
     *   两个按钮会让用户猜「现在点哪个」。
     */
    private Button pauseButton;
    /** ★ 1.4.3：仅在「有文件下载失败」时出现；点它用失败清单原地重下，不必关掉对话框重来。 */
    private Button retryButton;
    /** ★ 1.4.3：失败清单 / 暂停提示行。 */
    private TextView statusText;
    private ArrayList<DownloadTaskListBean> failedList = new ArrayList<>();
    private boolean paused = false;

    private Handler handler;
    private DownloadTask downloadTask;

    /**
     * ★ 社区版新增：通知栏进度用的聚合进度表（key = bean.path，value = 0..100）。
     *   为什么不用适配器里那份列表：适配器把「已完成」的项 remove 掉了，
     *   拿它算总进度会随着文件完成而分母缩水，进度条反而往回跳。
     */
    private final HashMap<String, Integer> notifyProgress = new HashMap<>();
    private int notifyTotalFiles = 0;
    /** 最近一次单文件速度（通知栏里顺带显示，来自 DownloadFeedback.updateSpeed） */
    private volatile String notifySpeed = "";
    private static final int REQ_NOTIFY = 0x2001;

    /** ★ 1.2.3：全部下载成功后的回调（模组下载完要做 class 检测/注入） */
    private Runnable onComplete;

    public void setOnComplete(Runnable r) {
        this.onComplete = r;
    }

    public DownloadDialog(@NonNull Context context, MainActivity activity, ArrayList<DownloadTaskListBean> list, boolean alert) {
        super(context);
        this.activity = activity;
        this.list = list;
        this.alert = alert;
        this.setContentView(R.layout.dialog_download);
        this.setCancelable(false);
        this.init();
    }

    private void init() {
        this.handler = new Handler();
        this.taskListView = (RecyclerView)this.findViewById(R.id.download_task_list);
        this.taskListView.setLayoutManager((RecyclerView.LayoutManager)new LinearLayoutManager(this.getContext()));
        this.downloadTaskListAdapter = new DownloadTaskListAdapter(this.getContext());
        this.taskListView.setAdapter((RecyclerView.Adapter)this.downloadTaskListAdapter);
        Objects.requireNonNull(this.taskListView.getItemAnimator()).setAddDuration(0L);
        this.taskListView.getItemAnimator().setChangeDuration(0L);
        this.taskListView.getItemAnimator().setMoveDuration(0L);
        this.taskListView.getItemAnimator().setRemoveDuration(0L);
        ((SimpleItemAnimator)this.taskListView.getItemAnimator()).setSupportsChangeAnimations(false);
        this.speedText = (TextView)this.findViewById(R.id.download_speed_text);
        this.cancelButton = (Button)this.findViewById(R.id.cancel);
        this.cancelButton.setOnClickListener((View.OnClickListener)this);
        this.pauseButton = (Button)this.findViewById(R.id.pause);
        this.pauseButton.setOnClickListener((View.OnClickListener)this);
        this.retryButton = (Button)this.findViewById(R.id.retry);
        this.retryButton.setOnClickListener((View.OnClickListener)this);
        this.statusText = (TextView)this.findViewById(R.id.download_status_text);
        Handler handler = new Handler((Handler.Callback)this);
        this.netSpeedTimer = new NetSpeedTimer(this.getContext(), new NetSpeed(), handler).setDelayTime(0L).setPeriodTime(1000L);
        this.netSpeedTimer.startSpeedTimer();
        requestNotificationPermissionIfNeeded();
        this.startDownload(this.list);
    }

    /**
     * ★ 社区版新增：Android 13+ 通知需要运行时授权。
     * 在「第一次真正要用到通知」的时刻申请，而不是应用一启动就弹 ——
     * 那时候用户还不知道通知用来干什么，拒绝率更高。
     * 拒绝也不阻断下载，只是没有通知栏进度。
     */
    private void requestNotificationPermissionIfNeeded() {
        try {
            if (DownloadNotify.canPostNotifications(this.getContext())) {
                return;
            }
            ActivityCompat.requestPermissions(this.activity,
                    new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
        } catch (Throwable ignored) {
        }
    }

    /**
     * ★ 1.4.3：把「建任务 + 启动」抽出来，让「重试」能原地复用。
     *   AsyncTask 不能二次 execute，所以重试必须新建实例。
     */
    private void startDownload(ArrayList<DownloadTaskListBean> beans) {
        this.paused = false;
        this.pauseButton.setText(R.string.dialog_download_pause);
        this.pauseButton.setVisibility(View.VISIBLE);
        this.retryButton.setVisibility(View.GONE);
        this.statusText.setVisibility(View.GONE);
        // ★ 社区版新增：通知栏进度。重试会走这里重建任务，所以要一并重置聚合进度与监听器
        this.notifyProgress.clear();
        this.notifyTotalFiles = beans.size();
        this.notifySpeed = "";
        DownloadNotify.setActionListener(this);
        DownloadNotify.start(this.getContext(), this.notifyTotalFiles);
        int maxDownloadTask = this.activity.launcherSetting.maxDownloadTask;
        if (this.activity.launcherSetting.autoDownloadTaskQuantity) {
            maxDownloadTask = 64;
        }
        this.downloadTask = new DownloadTask(this.getContext(), new DownloadTask.Feedback(){

            @Override
            public void addTask(DownloadTaskListBean bean) {
                DownloadDialog.this.handler.post(() -> DownloadDialog.this.downloadTaskListAdapter.addDownloadTask(bean));
            }

            @Override
            public void updateProgress(DownloadTaskListBean bean) {
                DownloadDialog.this.handler.post(() -> {
                    DownloadDialog.this.downloadTaskListAdapter.onProgress(bean);
                    // ★ 社区版新增：同步通知栏聚合进度
                    DownloadDialog.this.refreshNotifyProgress(bean);
                });
            }

            @Override
            public void updateSpeed(String speed) {
                // ★ 社区版新增：对话框里速度由 NetSpeedTimer 统一显示，
                //   这里只把速度留给通知栏用（通知栏没有 NetSpeedTimer）
                DownloadDialog.this.notifySpeed = speed;
            }

            @Override
            public void removeTask(DownloadTaskListBean bean) {
                DownloadDialog.this.handler.post(() -> DownloadDialog.this.downloadTaskListAdapter.onComplete(bean));
            }

            @Override
            public void onFinished(ArrayList<DownloadTaskListBean> failedFile) {
                DownloadDialog.this.handler.post(() -> {
                    if (failedFile.size() > 0) {
                        // ★ 1.4.3：以前这里直接弹「安装失败」并关掉对话框，用户只能从头再来。
                        //   现在留在对话框里给出失败清单 + 重试按钮 —— 下载失败的代价不该是重下全部。
                        DownloadDialog.this.showFailure(failedFile);
                    } else {
                        // ★ 社区版新增：成功也要撤掉前台服务与通知，否则会一直挂着一条不动的进度条
                        DownloadNotify.finish(DownloadDialog.this.getContext());
                        DownloadDialog.this.exit();
                        if (DownloadDialog.this.alert) {
                            Toast.makeText((Context)DownloadDialog.this.getContext(), (CharSequence)DownloadDialog.this.getContext().getString(R.string.dialog_download_success), (int)0).show();
                        }
                        if (DownloadDialog.this.onComplete != null) {
                            DownloadDialog.this.onComplete.run();
                        }
                    }
                });
            }

            @Override
            public void onCancelled() {
            }

            /**
             * ★ 社区版新增：仅 Wi-Fi 下载。因为切到计费网络被自动暂停 / 回到 Wi-Fi 自动恢复时，
             * 把对话框的按钮与提示同步过去 —— 否则会出现「任务实际停着，界面却显示可以点暂停」
             * 这种状态与界面不一致的情况。
             */
            @Override
            public void onNetworkHold(final boolean holding) {
                DownloadDialog.this.handler.post(() -> {
                    if (DownloadDialog.this.retryButton.getVisibility() == View.VISIBLE) {
                        return;   // 已经在失败态，不要被网络事件覆盖掉失败清单
                    }
                    DownloadDialog.this.paused = holding;
                    if (holding) {
                        DownloadDialog.this.pauseButton.setText(R.string.dialog_download_resume);
                        DownloadDialog.this.speedText.setText(
                                (CharSequence) DownloadDialog.this.getContext().getString(R.string.dialog_download_network_paused));
                    } else {
                        DownloadDialog.this.pauseButton.setText(R.string.dialog_download_pause);
                        DownloadDialog.this.speedText.setText((CharSequence) "");
                    }
                    // ★ 社区版新增：通知栏也要说明「为什么暂停」——
                    //   只写「已暂停」会让用户以为是下载坏了，而不是「当前不是 Wi-Fi」
                    DownloadNotify.setPaused(DownloadDialog.this.getContext(), holding, holding);
                });
            }
        });
        this.downloadTask.setMaxTask(maxDownloadTask);
        // ★ 社区版新增：仅 Wi-Fi 下载开关随任务实例带下去（任务内部会自行检测网络类型）
        this.downloadTask.setWifiOnly(this.activity.launcherSetting.wifiOnlyDownload);
        // ★ 社区版新增：下载限速。用全局静态设置（原因见 DownloadTask.setSpeedLimitKbps 注释），
        //   每次开新任务都同步一次，改设置后立刻生效。
        try {
            DownloadTask.setSpeedLimitKbps(this.activity.launcherSetting.downloadSpeedLimitKbps);
        } catch (Throwable ignored) {
        }
        this.downloadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new ArrayList[]{beans});
    }

    /** ★ 1.4.3：失败清单落盘到状态行，并放出「重试」按钮。 */
    private void showFailure(ArrayList<DownloadTaskListBean> failedFile) {
        this.failedList = failedFile;
        this.paused = false;
        // ★ 社区版新增：本轮下载已结束（只是留在对话框等用户决定要不要重试），
        //   通知栏的进度条该撤掉 —— 留着会让人以为还在下。点「重试」会重新拉起。
        DownloadNotify.finish(this.getContext());
        // 全部失败时「暂停」没有意义，收起来只留「重试 / 取消」
        this.pauseButton.setVisibility(View.GONE);
        this.retryButton.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder(this.getContext().getString(R.string.dialog_download_failed_hint));
        for (DownloadTaskListBean bean : failedFile) {
            sb.append("\n  ").append(bean.name);
        }
        this.statusText.setText((CharSequence)sb.toString());
        this.statusText.setVisibility(View.VISIBLE);
        this.speedText.setText((CharSequence)"");
    }

    private void throwException(Exception e) {
        this.activity.runOnUiThread(() -> {
            this.exit();
            AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
            builder.setTitle((CharSequence)this.getContext().getString(R.string.dialog_install_fail_title));
            builder.setMessage((CharSequence)e.toString());
            builder.setPositiveButton((CharSequence)this.getContext().getString(R.string.dialog_install_fail_positive), (dialogInterface, i) -> {});
            builder.create().show();
        });
    }

    private void exit() {
        // ★ 社区版新增：无论成功 / 失败 / 取消，退出对话框都要撤掉前台服务与通知，
        //   否则通知栏会留下一条永远不动的进度条（前台服务还会一直占着进程优先级）
        DownloadNotify.finish(this.getContext());
        if (this.downloadTask != null && this.downloadTask.getStatus() != null && this.downloadTask.getStatus() == AsyncTask.Status.RUNNING) {
            // ★ 1.3.0：改成 requestCancel()，真正中断当前正在下载的文件（点取消就停，不再后台继续下）
            // ★ 1.4.3：requestCancel() 里会唤醒因暂停而 park 的线程，所以「暂停中点取消」也能立刻停住
            this.downloadTask.requestCancel();
        }
        this.netSpeedTimer.stopSpeedTimer();
        this.dismiss();
    }

    public boolean handleMessage(@NonNull Message message) {
        if (message.what == 101010) {
            // ★ 1.4.3：暂停期间不让速度计时器把「已暂停」覆盖成上一次的速度值
            if (this.paused) {
                return false;
            }
            String speed = (String)message.obj;
            this.speedText.setText((CharSequence)speed);
        }
        return false;
    }

    public void onClick(View view) {
        if (view == this.cancelButton) {
            this.exit();
            return;
        }
        if (view == this.pauseButton) {
            togglePause();
            return;
        }
        if (view == this.retryButton) {
            // ★ 1.4.3：只重下失败的这几个文件，已成功的不会重下（它们在磁盘上校验通过会被跳过）
            if (this.failedList != null && this.failedList.size() > 0) {
                this.startDownload(new ArrayList<>(this.failedList));
            }
        }
    }

    /**
     * ★ 社区版新增：暂停 / 继续的唯一实现。
     * 对话框按钮与通知栏按钮都走这里 —— 两处各写一遍必然会在某次改动后不一致。
     */
    private void togglePause() {
        if (this.downloadTask == null) {
            return;
        }
        if (!this.paused) {
            this.downloadTask.requestPause();
            this.paused = true;
            this.pauseButton.setText(R.string.dialog_download_resume);
            this.speedText.setText((CharSequence)this.getContext().getString(R.string.dialog_download_paused));
        } else {
            this.downloadTask.requestResume();
            this.paused = false;
            this.pauseButton.setText(R.string.dialog_download_pause);
            this.speedText.setText((CharSequence)"");
        }
        // 通知栏同步（byNetwork=false：这是用户主动点的，不是网络造成的）
        DownloadNotify.setPaused(this.getContext(), this.paused, false);
    }

    // ------------------------------------------------------------------ 通知栏按钮回调

    @Override
    public void onTogglePauseFromNotify() {
        // onStartCommand 本来就在主线程，这里再 post 一次是为了不依赖调用方线程
        this.handler.post(this::togglePause);
    }

    @Override
    public void onCancelFromNotify() {
        this.handler.post(this::exit);
    }

    // ------------------------------------------------------------------ 通知栏进度

    /**
     * ★ 社区版新增：把单文件进度汇成整体进度推给通知栏。
     *
     * <p>用 {@code path} 做 key 而不是「第几个文件」：{@code DownloadTask} 是并发下载的，
     * 回调顺序与入队顺序无关；而且同一个文件重试时会再次回调，用 path 覆盖天然幂等。
     *
     * <p>★ 分母固定为本次任务的文件总数，不随已完成项减少 ——
     * 否则进度条会随着文件完成而往回跳（分母缩水比分子快）。
     */
    private void refreshNotifyProgress(DownloadTaskListBean bean) {
        if (bean == null || this.notifyTotalFiles <= 0) {
            return;
        }
        this.notifyProgress.put(bean.path == null ? bean.name : bean.path, bean.progress);
        int sum = 0;
        for (Integer v : this.notifyProgress.values()) {
            sum += (v == null ? 0 : v);
        }
        int overall = sum / this.notifyTotalFiles;
        DownloadNotify.update(this.getContext(), overall, this.notifyProgress.size(),
                this.notifySpeed);
    }
}
