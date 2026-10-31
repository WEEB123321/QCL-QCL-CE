package com.qcl.launcher.launcher.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.setting.game.GameLaunchSetting;
import com.qcl.launcher.manifest.AppManifest;

import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.ServerStdio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ForgeInstallService extends Service {

    private static final String TAG = "QCLForgeInstall";
    private static final String CHANNEL_ID = "qcl_forge_install";
    private static final int NOTIFICATION_ID = 0x5153;
    public static final String RESULT_FILE = "forge-install-result.txt";

    public static final String ACTION_START = "com.qcl.launcher.forge.START";
    public static final String ACTION_STOP = "com.qcl.launcher.forge.STOP";
    public static final String EXTRA_PROFILE = "profile";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread worker;
    private File profileFile;
    private File serverDir;
    private File resultFile;
    private PrintStream logStream;
    private PowerManager.WakeLock wakeLock;
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    });

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            writeResult("ERROR", "用户取消安装");
            finishService();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            return START_NOT_STICKY;
        }
        AppManifest.initializeManifest(getApplicationContext());
        String path = intent.getStringExtra(EXTRA_PROFILE);
        if (path == null || path.isEmpty()) {
            writeResult("ERROR", "缺少服务器配置");
            finishService();
            return START_NOT_STICKY;
        }
        if (worker != null && worker.isAlive()) {
            return START_NOT_STICKY;
        }
        profileFile = new File(path);
        serverDir = profileFile.getParentFile();
        resultFile = new File(serverDir, RESULT_FILE);
        startInForeground();
        acquireWakeLock();
        worker = new Thread(this::runInstaller, "qcl-forge-installer");
        worker.start();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        super.onDestroy();
    }

    private void runInstaller() {
        ServerProfile profile = ServerProfile.load(serverDir);
        if (profile == null) {
            writeResult("ERROR", "服务器配置损坏");
            finishService();
            return;
        }
        try {
            resultFile.delete();
            File logFile = new File(serverDir, "console.log");
            ServerStdio.redirectStdoutAppend(logFile.getAbsolutePath());
            logStream = new PrintStream(new FileOutputStream(logFile, true),
                    true, "UTF-8");
            appendLog("QCL-CE Forge 安装");
            profile.type = "forge";
            profile.requiredJava = ServerInstaller.requiredJavaForVersion(profile.version);
            appendLog("Minecraft " + profile.version + "，计划使用 Java "
                    + profile.requiredJava);
            File installer = ServerInstaller.prepareForgeInstaller(serverDir, profile,
                    new ServerInstaller.Listener() {
                        @Override
                        public void onLog(String line) {
                            appendLog(line);
                        }

                        @Override
                        public void onProgress(int percent) {
                            appendLog("下载进度 " + percent + "%");
                        }
                    });
            writeResult("RUNNING", profile.loaderVersion);
            String runtimeName = GameLaunchSetting.selectJavaRuntime(profile.requiredJava);
            File javaHome = new File(AppManifest.JAVA_DIR, runtimeName);
            File marker = "default".equals(runtimeName)
                    ? new File(javaHome, "bin/java") : new File(javaHome, "lib/modules");
            if (!marker.exists()) {
                throw new IllegalStateException("缺少 Java 运行时 " + runtimeName);
            }
            ArrayList<String> args = new ArrayList<>();
            args.add("-Djava.awt.headless=true");
            args.add("-Dfile.encoding=UTF-8");
            args.add("-Djava.io.tmpdir=" + getCacheDir().getAbsolutePath());
            args.add("-jar");
            args.add(installer.getName());
            args.add("--installServer");
            appendLog("运行 Forge 安装器 --installServer");
            int exit = JREUtils.launchJavaMain(getApplicationContext(),
                    javaHome.getAbsolutePath(), serverDir.getAbsolutePath(), args);
            appendLog("Forge 安装器退出码 " + exit);
            ServerInstaller.ensure(getApplicationContext(), serverDir, profile,
                    new ServerInstaller.Listener() {
                        @Override
                        public void onLog(String line) {
                            appendLog(line);
                        }

                        @Override
                        public void onProgress(int percent) {
                        }
                    });
            if (!ServerInstaller.isForgeInstalled(serverDir, profile)) {
                throw new IllegalStateException("Forge 安装器未生成可启动文件");
            }
            writeResult("OK", profile.loaderVersion);
            appendLog("Forge 安装完成，可以启动服务端");
        } catch (Throwable t) {
            Log.e(TAG, "Forge 安装失败", t);
            appendLog("Forge 安装失败：" + t);
            writeResult("ERROR", String.valueOf(t.getMessage()));
        } finally {
            finishService();
        }
    }

    private synchronized void appendLog(String line) {
        if (logStream != null) {
            logStream.println(line);
            logStream.flush();
        }
    }

    private void writeResult(String state, String detail) {
        if (serverDir == null) {
            return;
        }
        try {
            File target = new File(serverDir, RESULT_FILE);
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write((state + "\n" + (detail == null ? "" : detail)
                        + "\n").getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
        } catch (Throwable t) {
            Log.w(TAG, "无法写入 Forge 安装结果", t);
        }
    }

    private void startInForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, ServerConsoleActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (profileFile != null) {
            open.putExtra(ServerConsoleActivity.EXTRA_PROFILE, profileFile.getAbsolutePath());
        }
        PendingIntent content = PendingIntent.getActivity(this, 30, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ForgeInstallService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 31, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_qcl_gamecube_white)
                .setContentTitle("Forge 安装器")
                .setContentText("正在安装服务端，请保持应用运行")
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_qcl_gamecube_white),
                        "取消", stopIntent).build());
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Forge 安装器", NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);
    }

    private void acquireWakeLock() {
        try {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (power != null) {
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "QCL:ForgeInstaller");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {
        }
        wakeLock = null;
    }

    private void finishService() {
        mainHandler.post(() -> {
            if (logStream != null) {
                logStream.close();
                logStream = null;
            }
            releaseWakeLock();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            mainHandler.postDelayed(() -> Process.killProcess(Process.myPid()), 900L);
        });
    }
}
