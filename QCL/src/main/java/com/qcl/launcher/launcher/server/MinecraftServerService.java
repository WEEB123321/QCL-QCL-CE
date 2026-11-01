package com.qcl.launcher.launcher.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.setting.game.GameLaunchSetting;
import com.qcl.launcher.manifest.AppManifest;

import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.ServerStdio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MinecraftServerService extends Service {

    private static final String TAG = "QCLServer";
    private static final String CHANNEL_ID = "qcl_minecraft_server";
    private static final int NOTIFICATION_ID = 0x5152;
    private static final int STOP_TIMEOUT_MS = 20000;

    public static final String ACTION_START = "com.qcl.launcher.server.START";
    public static final String ACTION_STOP = "com.qcl.launcher.server.STOP";
    public static final String EXTRA_PROFILE = "profile";

    public static final int MSG_REGISTER = 1;
    public static final int MSG_COMMAND = 2;
    public static final int MSG_STOP = 3;
    public static final int MSG_QUERY = 4;
    public static final int MSG_STATE = 10;

    public static final String STATE_IDLE = "idle";
    public static final String STATE_PREPARING = "preparing";
    public static final String STATE_DOWNLOADING = "downloading";
    public static final String STATE_STARTING = "starting";
    public static final String STATE_READY = "ready";
    public static final String STATE_STOPPING = "stopping";
    public static final String STATE_STOPPED = "stopped";
    public static final String STATE_FAILED = "failed";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Messenger messenger = new Messenger(new IncomingHandler());
    private final Object logLock = new Object();

    private volatile String state = STATE_IDLE;
    private volatile String stateDetail = "未启动";
    private volatile boolean running;
    private volatile boolean stopRequested;
    private volatile boolean jvmStarted;
    private volatile int port = 25565;

    private final java.util.List<Messenger> clients =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private File profileFile;
    private File serverDir;
    private File logFile;
    private PrintStream logStream;
    private Thread serverThread;
    private Thread readyThread;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private Runnable forceStopTask;

    private final class IncomingHandler extends Handler {
        IncomingHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER:
                case MSG_QUERY:
                    addClient(msg.replyTo);
                    sendState();
                    break;
                case MSG_COMMAND:
                    String command = msg.getData() == null ? null
                            : msg.getData().getString("command");
                    boolean ok = sendCommand(command);
                    reply(msg, ok);
                    break;
                case MSG_STOP:
                    stopServer();
                    reply(msg, true);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

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
        AppManifest.initializeManifest(getApplicationContext());
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopServer();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            String path = intent.getStringExtra(EXTRA_PROFILE);
            if (path == null || path.isEmpty()) {
                publishState(STATE_FAILED, "缺少服务器配置");
                stopSelf();
                return START_NOT_STICKY;
            }
            File requested = new File(path);
            if (running && profileFile != null
                    && !requested.getAbsolutePath().equals(profileFile.getAbsolutePath())) {
                sendState();
                return START_NOT_STICKY;
            }
            if (running) {
                sendState();
                return START_NOT_STICKY;
            }
            profileFile = requested;
            serverDir = profileFile.getParentFile();
            startInForeground("正在准备服务端");
            startServer();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    private synchronized void startServer() {
        if (serverThread != null && serverThread.isAlive()) {
            return;
        }
        if (jvmStarted) {
            mainHandler.post(() -> Process.killProcess(Process.myPid()));
            return;
        }
        if (forceStopTask != null) {
            mainHandler.removeCallbacks(forceStopTask);
            forceStopTask = null;
        }
        stopRequested = false;
        running = true;
        acquireLocks();
        serverThread = new Thread(this::runServer, "qcl-server-main");
        serverThread.setDaemon(false);
        serverThread.start();
    }

    private void acquireLocks() {
        try {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (power != null) {
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "QCL:MinecraftServer");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Throwable ignored) {
        }
        try {
            WifiManager wifi = (WifiManager) getApplicationContext()
                    .getSystemService(WIFI_SERVICE);
            if (wifi != null) {
                wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                        "QCL:MinecraftServerWifi");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {
        }
        wakeLock = null;
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
        } catch (Throwable ignored) {
        }
        wifiLock = null;
    }

    private void runServer() {
        ServerProfile profile = ServerProfile.load(serverDir);
        if (profile == null) {
            publishState(STATE_FAILED, "服务器配置损坏");
            finishService(false);
            return;
        }
        port = profile.port;
        jvmStarted = false;
        openLog(profile);
        try {
            if (stopRequested) {
                return;
            }
            publishState(STATE_PREPARING, "正在检查服务端文件");
            ServerInstaller.ensure(getApplicationContext(), serverDir, profile,
                    new ServerInstaller.Listener() {
                        @Override
                        public void onLog(String line) {
                            appendLog(line);
                        }

                        @Override
                        public void onProgress(int percent) {
                            publishState(STATE_DOWNLOADING, "正在下载 server.jar " + percent + "%");
                        }
                    });
            if (stopRequested) {
                return;
            }
            String runtimeName = GameLaunchSetting.selectJavaRuntime(profile.requiredJava);
            File javaHome = new File(AppManifest.JAVA_DIR, runtimeName);
            File marker = "default".equals(runtimeName)
                    ? new File(javaHome, "bin/java") : new File(javaHome, "lib/modules");
            if (!marker.exists()) {
                throw new IllegalStateException("缺少 Java 运行时 " + runtimeName
                        + "，请先在运行环境页安装");
            }

            if (!ServerStdio.createStdinPipe()) {
                throw new IllegalStateException("无法创建服务端命令管道");
            }
            publishState(STATE_STARTING, "正在启动服务端");
            startReadyMonitor();

            ArrayList<String> args = new ArrayList<>();
            int heap = Math.max(512, Math.min(profile.memoryMb, 4096));
            args.add("-Xms" + Math.min(512, heap) + "M");
            args.add("-Xmx" + heap + "M");
            args.add("-Djava.awt.headless=true");
            args.add("-Dfile.encoding=UTF-8");
            args.add("-Djava.io.tmpdir=" + getCacheDir().getAbsolutePath());
            args.add("-Dlog4j2.formatMsgNoLookups=true");
            args.add("-Duser.language=" + Locale.getDefault().getLanguage());
            if (("forge".equals(profile.type) || "neoforge".equals(profile.type))
                    && profile.launchArgsFile != null
                    && !profile.launchArgsFile.isEmpty()) {
                File argsFile = new File(serverDir, profile.launchArgsFile);
                if (!argsFile.isFile()) {
                    throw new IllegalStateException("Forge/NeoForge 启动参数文件缺失：" + argsFile);
                }
                args.addAll(ServerInstaller.readArgFile(argsFile));
                args.add("nogui");
            } else {
                args.add("-jar");
                args.add(profile.serverJar);
                args.add("nogui");
            }
            appendLog("启动 " + profile.typeLabel() + "，Java " + profile.requiredJava
                    + "，内存 " + heap + " MB");
            jvmStarted = true;
            int exit = JREUtils.launchJavaMain(getApplicationContext(),
                    javaHome.getAbsolutePath(), serverDir.getAbsolutePath(), args);
            appendLog("服务端进程结束，退出码 " + exit);
            publishState(stopRequested ? STATE_STOPPED : STATE_FAILED,
                    stopRequested ? "服务端已停止" : "服务端进程退出（" + exit + "）");
        } catch (Throwable t) {
            appendLog("启动失败：" + t);
            Log.e(TAG, "服务端启动失败", t);
            publishState(STATE_FAILED, "启动失败：" + t.getMessage());
        } finally {
            running = false;
            try {
                ServerStdio.closeStdinPipe();
            } catch (Throwable ignored) {
            }
            finishService(jvmStarted);
        }
    }

    private void startReadyMonitor() {
        readyThread = new Thread(() -> {
            while (running && !stopRequested) {
                if (canConnect() && logContains("Done (")) {
                    publishState(STATE_READY, "127.0.0.1:" + port);
                    return;
                }
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "qcl-server-ready");
        readyThread.setDaemon(true);
        readyThread.start();
    }

    private boolean canConnect() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 400);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean logContains(String marker) {
        File file = new File(serverDir, "console.log");
        if (!file.isFile()) {
            return false;
        }
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long start = Math.max(0, raf.length() - 128 * 1024L);
            raf.seek(start);
            byte[] data = new byte[(int) (raf.length() - start)];
            raf.readFully(data);
            return new String(data, StandardCharsets.UTF_8).contains(marker);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean sendCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        try {
            boolean ok = ServerStdio.writeStdin(
                    (command.trim() + "\n").getBytes(StandardCharsets.UTF_8));
            if (ok) {
                appendLog("> " + command.trim());
            }
            return ok;
        } catch (Throwable e) {
            appendLog("命令发送失败：" + e.getMessage());
            return false;
        }
    }

    private void stopServer() {
        if (!running && serverThread == null) {
            publishState(STATE_STOPPED, "服务端未运行");
            finishService(false);
            return;
        }
        stopRequested = true;
        publishState(STATE_STOPPING, "正在保存世界并停止");
        if (forceStopTask != null) {
            mainHandler.removeCallbacks(forceStopTask);
        }
        forceStopTask = this::forceStop;
        mainHandler.postDelayed(forceStopTask, sendCommand("stop")
                ? STOP_TIMEOUT_MS : 2500L);
    }

    private void forceStop() {
        forceStopTask = null;
        if (!running) {
            return;
        }
        appendLog("服务端未在超时内退出，结束 :server 进程");
        Process.killProcess(Process.myPid());
    }

    private void finishService(boolean killProcess) {
        mainHandler.post(() -> {
            running = false;
            if (forceStopTask != null) {
                mainHandler.removeCallbacks(forceStopTask);
                forceStopTask = null;
            }
            releaseLocks();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            sendState();
            if (killProcess) {
                mainHandler.postDelayed(() -> Process.killProcess(Process.myPid()), 200L);
            }
        });
    }

    private void openLog(ServerProfile profile) {
        try {
            logFile = new File(serverDir, "console.log");
            ServerStdio.redirectStdout(logFile.getAbsolutePath());
            synchronized (logLock) {
                if (logStream != null) {
                    logStream.close();
                }
                logStream = new PrintStream(new FileOutputStream(logFile, true),
                        true, "UTF-8");
            }
            appendLog("QCL-CE 手机开服");
            appendLog("版本 " + profile.version + "，目录 " + serverDir);
        } catch (Throwable t) {
            Log.w(TAG, "无法建立服务端日志", t);
        }
    }

    private void appendLog(String line) {
        synchronized (logLock) {
            if (logStream != null) {
                logStream.println(line);
                logStream.flush();
            }
        }
    }

    private void publishState(String newState, String detail) {
        state = newState;
        stateDetail = detail == null ? "" : detail;
        mainHandler.post(() -> {
            updateNotification();
            sendState();
        });
    }

    private void addClient(Messenger messenger) {
        if (messenger == null) {
            return;
        }
        for (Messenger existing : clients) {
            if (existing.getBinder() == messenger.getBinder()) {
                return;
            }
        }
        clients.add(messenger);
    }

    private void sendState() {
        if (clients.isEmpty()) {
            return;
        }
        for (Messenger target : clients) {
            Message message = Message.obtain(null, MSG_STATE);
            message.getData().putString("state", state);
            message.getData().putString("detail", stateDetail);
            message.getData().putInt("port", port);
            if (profileFile != null) {
                message.getData().putString("profile", profileFile.getAbsolutePath());
            }
            try {
                target.send(message);
            } catch (RemoteException e) {
                clients.remove(target);
            }
        }
    }

    private void reply(Message request, boolean ok) {
        if (request.replyTo == null) {
            return;
        }
        Message response = Message.obtain(null, request.what);
        response.arg1 = ok ? 1 : 0;
        try {
            request.replyTo.send(response);
        } catch (RemoteException ignored) {
        }
    }

    private void startInForeground(String text) {
        publishState(STATE_PREPARING, text);
        Notification notification = buildNotification(text);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        if (!running && state.equals(STATE_STOPPED)) {
            return;
        }
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(stateDetail));
            }
        } catch (Throwable ignored) {
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, ServerConsoleActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (profileFile != null) {
            open.putExtra(ServerConsoleActivity.EXTRA_PROFILE, profileFile.getAbsolutePath());
        }
        PendingIntent content = PendingIntent.getActivity(this, 20, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, MinecraftServerService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 21, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_qcl_gamecube_white)
                .setContentTitle("Minecraft 服务端")
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_qcl_gamecube_white),
                        "停止", stopIntent).build());
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
                "Minecraft 服务端", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("手机开服运行状态");
        manager.createNotificationChannel(channel);
    }
}
