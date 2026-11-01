package com.qcl.launcher.launcher.server;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.terracotta.MultiplayerDialogHelper;
import com.qcl.launcher.launcher.terracotta.MultiplayerQrDialog;
import com.qcl.launcher.launcher.terracotta.TerracottaHelper;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.LocaleUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class ServerConsoleActivity extends AppCompatActivity {

    public static final String EXTRA_PROFILE = "profile";
    public static final String EXTRA_AUTO_START = "auto_start";

    private static final int LOG_TAIL_BYTES = 256 * 1024;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Messenger clientMessenger = new Messenger(new StateHandler());

    private File profileFile;
    private File serverDir;
    private ServerProfile profile;
    private File logFile;

    private TextView statusView;
    private TextView logView;
    private EditText commandView;
    private ScrollView logScroll;
    private Button startButton;
    private Button stopButton;
    private Button restartButton;
    private Button playButton;
    private Button moreButton;

    private Messenger serviceMessenger;
    private boolean bound;
    private boolean pendingRestart;
    private boolean waitingForgeInstall;
    private boolean forgeInstallBound;
    private boolean forgeInstallProcessGone;
    private long forgeInstallStartedAt;
    private File forgeInstallResultFile;
    private String state = MinecraftServerService.STATE_IDLE;
    private String stateDetail = "未启动";
    private long lastLogLength = -1L;
    private long lastLogModified;
    private boolean logReading;
    private int invitePollTries;

    private final Runnable invitePoller = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            String code = TerracottaHelper.getInviteCode();
            if (code != null && !code.isEmpty()) {
                MultiplayerQrDialog.show(ServerConsoleActivity.this,
                        ServerConsoleActivity.this);
                return;
            }
            if (invitePollTries++ > 120) {
                String state = TerracottaHelper.getState();
                toast("联机房间未就绪，状态：" + (state == null || state.isEmpty()
                        ? "未知" : state));
                return;
            }
            handler.postDelayed(this, 500L);
        }
    };

    private final Runnable logPoll = new Runnable() {
        @Override
        public void run() {
            refreshLog();
            handler.postDelayed(this, 600L);
        }
    };

    private final ServiceConnection forgeInstallConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            forgeInstallBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            forgeInstallGone();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            forgeInstallGone();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            forgeInstallGone();
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceMessenger = new Messenger(service);
            bound = true;
            try {
                Message message = Message.obtain(null, MinecraftServerService.MSG_REGISTER);
                message.replyTo = clientMessenger;
                serviceMessenger.send(message);
            } catch (RemoteException ignored) {
                serviceMessenger = null;
                bound = false;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceMessenger = null;
            if (bound) {
                try {
                    unbindService(connection);
                } catch (Throwable ignored) {
                }
            }
            bound = false;
            stopSharing();
            applyState(MinecraftServerService.STATE_STOPPED, "服务端进程已结束");
            if (pendingRestart) {
                pendingRestart = false;
                handler.postDelayed(ServerConsoleActivity.this::startServer, 1800L);
            }
        }
    };

    private final class StateHandler extends Handler {
        StateHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what != MinecraftServerService.MSG_STATE) {
                return;
            }
            String next = msg.getData().getString("state", MinecraftServerService.STATE_IDLE);
            String detail = msg.getData().getString("detail", "");
            int serverPort = msg.getData().getInt("port", 25565);
            String activeProfile = msg.getData().getString("profile");
            if (profileFile != null && activeProfile != null && !activeProfile.isEmpty()
                    && !profileFile.getAbsolutePath().equals(activeProfile)) {
                applyState(MinecraftServerService.STATE_FAILED,
                        "另一个服务器正在运行，请先停止它");
                return;
            }
            if (profile != null && serverPort > 0) {
                profile.port = serverPort;
            }
            applyState(next, detail);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppManifest.initializeManifest(getApplicationContext());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_console);

        statusView = findViewById(R.id.server_console_status);
        logView = findViewById(R.id.server_console_log);
        logScroll = findViewById(R.id.server_console_scroll);
        commandView = findViewById(R.id.server_console_command);
        startButton = findViewById(R.id.server_console_start);
        stopButton = findViewById(R.id.server_console_stop);
        restartButton = findViewById(R.id.server_console_restart);
        playButton = findViewById(R.id.server_console_play);
        moreButton = findViewById(R.id.server_console_more);
        Button sendButton = findViewById(R.id.server_console_send);

        String requested = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROFILE);
        profileFile = requested == null || requested.isEmpty()
                ? ServerProfile.profileFile(ServerProfile.defaultDirectory())
                : new File(requested);
        serverDir = profileFile.getParentFile();
        logFile = new File(serverDir, "console.log");
        forgeInstallResultFile = new File(serverDir, ForgeInstallService.RESULT_FILE);
        profile = ServerProfile.load(serverDir);

        startButton.setOnClickListener(v -> startServer());
        stopButton.setOnClickListener(v -> stopServer());
        restartButton.setOnClickListener(v -> restartServer());
        playButton.setOnClickListener(v -> playGame());
        moreButton.setOnClickListener(v -> showTools());
        sendButton.setOnClickListener(v -> sendCommand());
        commandView.setOnEditorActionListener((v, actionId, event) -> {
            sendCommand();
            return true;
        });

        bindServerService();
        handler.post(logPoll);
        if (profile == null) {
            applyState(MinecraftServerService.STATE_IDLE, "尚未创建服务器");
            showSetupDialog();
        } else {
            applyState(state, "等待服务端状态");
            if (getIntent() != null
                    && getIntent().getBooleanExtra(EXTRA_AUTO_START, false)) {
                handler.postDelayed(this::startServer, 250L);
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        TerracottaHelper.onActivityResult(this, requestCode);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(logPoll);
        handler.removeCallbacks(invitePoller);
        if (bound) {
            try {
                unbindService(connection);
            } catch (Throwable ignored) {
            }
            bound = false;
        }
        if (forgeInstallBound) {
            try {
                unbindService(forgeInstallConnection);
            } catch (Throwable ignored) {
            }
            forgeInstallBound = false;
        }
        super.onDestroy();
    }

    private void bindServerService() {
        try {
            Intent intent = new Intent(this, MinecraftServerService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            toast("连接服务端进程失败：" + t.getMessage());
        }
    }

    private void showSetupDialog() {
        ServerSetupDialog.show(this, ServerProfile.serversRoot(), "main",
                (file, created) -> {
                    profileFile = file;
                    serverDir = file.getParentFile();
                    logFile = new File(serverDir, "console.log");
                    forgeInstallResultFile = new File(serverDir,
                            ForgeInstallService.RESULT_FILE);
                    profile = created;
                    startServer();
                });
    }

    private void showTools() {
        if (profile == null) {
            toast("请先创建服务器");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(profile.name + " · 服务端工具")
                .setItems(new String[]{"玩家管理", "编辑 server.properties",
                        "服务器配置", "模组 / 插件管理", "世界备份", "联机与共享"}, (d, which) -> {
                    if (which == 0) {
                        showPlayerTools();
                    } else if (which == 1) {
                        editServerProperties();
                    } else if (which == 2) {
                        editServerProfile();
                    } else if (which == 3) {
                        openModsManager();
                    } else if (which == 4) {
                        openBackups();
                    } else {
                        showShareMenu();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void openModsManager() {
        if (isServerActive()) {
            toast("请先停止服务端再管理模组或插件");
            return;
        }
        startActivity(new Intent(this, ServerModsActivity.class)
                .putExtra(ServerModsActivity.EXTRA_PROFILE, profileFile.getAbsolutePath()));
    }

    private void openBackups() {
        if (isServerActive()) {
            toast("请先停止服务端再备份或恢复世界");
            return;
        }
        startActivity(new Intent(this, ServerBackupsActivity.class)
                .putExtra(ServerBackupsActivity.EXTRA_PROFILE, profileFile.getAbsolutePath()));
    }

    private void showPlayerTools() {
        if (!MinecraftServerService.STATE_READY.equals(state)) {
            toast("请等待服务端显示“运行中”");
            return;
        }
        String[] items = {
                "查看在线玩家", "设为管理员 (OP)", "取消管理员", "踢出玩家",
                "加入白名单", "移出白名单", "重新加载白名单"
        };
        new AlertDialog.Builder(this)
                .setTitle("玩家管理")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            runCommand("list");
                            break;
                        case 1:
                            askPlayerCommand("设为管理员", "op ");
                            break;
                        case 2:
                            askPlayerCommand("取消管理员", "deop ");
                            break;
                        case 3:
                            askPlayerCommand("踢出玩家", "kick ");
                            break;
                        case 4:
                            askPlayerCommand("加入白名单", "whitelist add ");
                            break;
                        case 5:
                            askPlayerCommand("移出白名单", "whitelist remove ");
                            break;
                        default:
                            runCommand("whitelist reload");
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void askPlayerCommand(String title, String prefix) {
        EditText input = new EditText(this);
        input.setHint("玩家名");
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x80FFFFFF);
        LinearLayout box = new LinearLayout(this);
        int pad = dp(18);
        box.setPadding(pad, dp(4), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("执行", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String player = input.getText() == null ? ""
                            : input.getText().toString().trim();
                    if (player.isEmpty() || player.contains(" ")) {
                        toast("请填写有效玩家名");
                        return;
                    }
                    dialog.dismiss();
                    runCommand(prefix + player);
                }));
        dialog.show();
    }

    private void runCommand(String command) {
        commandView.setText(command);
        sendCommand();
    }

    private void editServerProperties() {
        if (isServerActive()) {
            toast("请先停止服务端再修改 server.properties");
            return;
        }
        File file = new File(serverDir, "server.properties");
        String current = readFullText(file);
        EditText editor = new EditText(this);
        editor.setSingleLine(false);
        editor.setMinLines(12);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editor.setText(current == null ? "" : current);
        editor.setTextSize(12f);
        int pad = dp(14);
        editor.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(editor);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("编辑 server.properties")
                .setMessage("端口、人数、MOTD 和正版验证请使用“服务器配置”修改。")
                .setView(scroll)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        writeText(file, editor.getText() == null
                                ? "" : editor.getText().toString());
                        dialog.dismiss();
                        toast("已保存，下次启动生效");
                    } catch (Throwable t) {
                        toast("保存失败：" + t.getMessage());
                    }
                }));
        dialog.show();
    }

    private void editServerProfile() {
        if (isServerActive()) {
            toast("请先停止服务端再修改配置");
            return;
        }
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        body.setPadding(pad, dp(4), pad, 0);
        EditText port = dialogField("端口", String.valueOf(profile.port),
                InputType.TYPE_CLASS_NUMBER);
        EditText players = dialogField("最大玩家数", String.valueOf(profile.maxPlayers),
                InputType.TYPE_CLASS_NUMBER);
        EditText memory = dialogField("内存 MB", String.valueOf(profile.memoryMb),
                InputType.TYPE_CLASS_NUMBER);
        EditText motd = dialogField("服务器名（MOTD）", profile.motd,
                InputType.TYPE_CLASS_TEXT);
        CheckBox online = new CheckBox(this);
        online.setText("开启正版验证");
        online.setTextColor(Color.WHITE);
        online.setChecked(profile.onlineMode);
        body.addView(port);
        body.addView(players);
        body.addView(memory);
        body.addView(motd);
        body.addView(online);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("服务器配置 · " + profile.name)
                .setView(scroll)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int portValue;
                    try {
                        portValue = Integer.parseInt(dialogText(port));
                    } catch (Throwable t) {
                        toast("端口格式不正确");
                        return;
                    }
                    if (portValue < 1024 || portValue > 65535) {
                        toast("端口需要在 1024 到 65535 之间");
                        return;
                    }
                    try {
                        profile.port = portValue;
                        profile.maxPlayers = Math.max(1,
                                Integer.parseInt(dialogText(players)));
                        profile.memoryMb = Math.max(512, Math.min(4096,
                                Integer.parseInt(dialogText(memory))));
                        profile.motd = dialogText(motd);
                        profile.onlineMode = online.isChecked();
                        profile.save(serverDir);
                        dialog.dismiss();
                        toast("已保存，下次启动生效");
                    } catch (Throwable t) {
                        toast("保存失败：" + t.getMessage());
                    }
                }));
        dialog.show();
    }

    private EditText dialogField(String hint, String value, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setInputType(inputType);
        edit.setSingleLine(true);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(0x80FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        edit.setLayoutParams(lp);
        return edit;
    }

    private static String dialogText(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private boolean isServerActive() {
        return MinecraftServerService.STATE_PREPARING.equals(state)
                || MinecraftServerService.STATE_DOWNLOADING.equals(state)
                || MinecraftServerService.STATE_STARTING.equals(state)
                || MinecraftServerService.STATE_READY.equals(state)
                || MinecraftServerService.STATE_STOPPING.equals(state);
    }

    private static String readFullText(File file) {
        if (file == null || !file.isFile() || file.length() > 2 * 1024 * 1024) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int off = 0;
            while (off < data.length) {
                int read = in.read(data, off, data.length - off);
                if (read < 0) {
                    break;
                }
                off += read;
            }
            return new String(data, 0, off, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeText(File file, String text) throws Exception {
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    private void showShareMenu() {
        if (!MinecraftServerService.STATE_READY.equals(state)) {
            toast("请等待服务端显示“运行中”");
            return;
        }
        String address = ServerLanAdvertiser.localAddress() + ":" + profile.port;
        String code = TerracottaHelper.getInviteCode();
        String tcState = TerracottaHelper.getState();
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        body.setPadding(pad, dp(4), pad, 0);
        TextView info = new TextView(this);
        info.setText("局域网地址（同一 Wi-Fi）：\n" + address
                + "\n\nTerracotta 邀请码：\n"
                + (code == null || code.isEmpty() ? "未创建" : code)
                + "\n\n联机状态：" + (tcState == null || tcState.isEmpty()
                ? "未初始化" : tcState));
        info.setTextColor(Color.WHITE);
        info.setTextSize(13f);
        info.setTextIsSelectable(true);
        body.addView(info);
        Button copyLan = new Button(this);
        copyLan.setText("复制局域网地址");
        copyLan.setOnClickListener(v -> {
            copyText(address);
            toast("已复制 " + address);
        });
        body.addView(copyLan);
        Button host = new Button(this);
        host.setText(code == null || code.isEmpty()
                ? "创建 Terracotta 房间" : "显示邀请二维码");
        host.setOnClickListener(v -> {
            if (code != null && !code.isEmpty()) {
                MultiplayerQrDialog.show(this, this);
            } else {
                MultiplayerDialogHelper.ensureEnabled(this, this, this::startTerracottaHost);
            }
        });
        body.addView(host);
        if (ServerLanAdvertiser.isRunning() || (code != null && !code.isEmpty())) {
            Button stop = new Button(this);
            stop.setText("停止联机共享");
            stop.setOnClickListener(v -> {
                stopSharing();
                toast("已停止联机共享");
            });
            body.addView(stop);
        }
        new AlertDialog.Builder(this)
                .setTitle("联机与共享")
                .setView(body)
                .setNegativeButton("关闭", null)
                .show();
    }

    private void startTerracottaHost() {
        if (!MinecraftServerService.STATE_READY.equals(state)) {
            toast("服务端尚未就绪");
            return;
        }
        ServerLanAdvertiser.start(getApplicationContext(), profile.port);
        TerracottaHelper.host(this);
        invitePollTries = 0;
        handler.removeCallbacks(invitePoller);
        handler.postDelayed(invitePoller, 500L);
        toast("正在创建房间，请按系统提示授权 VPN");
    }

    private void stopSharing() {
        handler.removeCallbacks(invitePoller);
        ServerLanAdvertiser.stop();
        try {
            TerracottaHelper.reset(this);
        } catch (Throwable ignored) {
        }
    }

    private void copyText(String value) {
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("QCL server", value));
            }
        } catch (Throwable ignored) {
        }
    }

    private void startServer() {
        if (profile == null) {
            showSetupDialog();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    1002);
        }
        if (("forge".equals(profile.type) || "neoforge".equals(profile.type))
                && !ServerInstaller.isForgeInstalled(serverDir, profile)) {
            startForgeInstall();
            return;
        }
        try {
            Intent intent = new Intent(this, MinecraftServerService.class)
                    .setAction(MinecraftServerService.ACTION_START)
                    .putExtra(MinecraftServerService.EXTRA_PROFILE, profileFile.getAbsolutePath());
            startForegroundService(intent);
            applyState(MinecraftServerService.STATE_PREPARING, "正在准备服务端");
            if (!bound) {
                bindServerService();
            }
        } catch (Throwable t) {
            toast("启动失败：" + t.getMessage());
        }
    }

    private void forgeInstallGone() {
        if (forgeInstallBound) {
            try {
                unbindService(forgeInstallConnection);
            } catch (Throwable ignored) {
            }
        }
        forgeInstallBound = false;
        forgeInstallProcessGone = true;
        handler.postDelayed(this::checkForgeInstall, 600L);
    }

    private void startForgeInstall() {
        try {
            waitingForgeInstall = true;
            forgeInstallProcessGone = false;
            forgeInstallStartedAt = System.currentTimeMillis();
            if (forgeInstallResultFile != null) {
                forgeInstallResultFile.delete();
            }
            applyState(MinecraftServerService.STATE_PREPARING,
                    "正在安装 " + profile.typeLabel() + "，首次需要下载");
            Intent intent = new Intent(this, ForgeInstallService.class)
                    .setAction(ForgeInstallService.ACTION_START)
                    .putExtra(ForgeInstallService.EXTRA_PROFILE,
                            profileFile.getAbsolutePath());
            startForegroundService(intent);
            bindService(new Intent(this, ForgeInstallService.class),
                    forgeInstallConnection, Context.BIND_AUTO_CREATE);
            toast("正在安装 " + profile.typeLabel() + "，日志显示在控制台");
        } catch (Throwable t) {
            waitingForgeInstall = false;
            applyState(MinecraftServerService.STATE_FAILED,
                    "无法启动 Forge 安装器：" + t.getMessage());
        }
    }

    private void checkForgeInstall() {
        if (!waitingForgeInstall || forgeInstallResultFile == null) {
            return;
        }
        if (System.currentTimeMillis() - forgeInstallStartedAt > 20 * 60 * 1000L) {
            waitingForgeInstall = false;
            applyState(MinecraftServerService.STATE_FAILED, "Forge/NeoForge 安装超时");
            return;
        }
        String result = readFullText(forgeInstallResultFile);
        if (result != null && result.startsWith("OK")) {
            completeForgeInstall();
            return;
        }
        if (result != null && result.startsWith("ERROR")) {
            waitingForgeInstall = false;
            String[] lines = result.split("\\n", 3);
            String detail = lines.length > 1 ? lines[1] : "未知错误";
            applyState(MinecraftServerService.STATE_FAILED, "Forge/NeoForge 安装失败：" + detail);
            return;
        }
        if (forgeInstallProcessGone) {
            if (ServerInstaller.isForgeInstalled(serverDir, profile)) {
                completeForgeInstall();
            } else {
                waitingForgeInstall = false;
                applyState(MinecraftServerService.STATE_FAILED,
                        "Forge/NeoForge 安装进程已结束，但未生成启动文件");
            }
        }
    }

    private void completeForgeInstall() {
        if (!waitingForgeInstall) {
            return;
        }
        waitingForgeInstall = false;
        if (forgeInstallBound) {
            try {
                unbindService(forgeInstallConnection);
            } catch (Throwable ignored) {
            }
            forgeInstallBound = false;
        }
        profile = ServerProfile.load(serverDir);
        if (profile == null || !ServerInstaller.isForgeInstalled(serverDir, profile)) {
            applyState(MinecraftServerService.STATE_FAILED,
                    "Forge/NeoForge 安装结果无法识别，请查看控制台日志");
            return;
        }
        toast(profile.typeLabel() + " 安装完成");
        startServer();
    }

    private void stopServer() {
        if (waitingForgeInstall) {
            waitingForgeInstall = false;
            try {
                startService(new Intent(this, ForgeInstallService.class)
                        .setAction(ForgeInstallService.ACTION_STOP));
            } catch (Throwable ignored) {
            }
            applyState(MinecraftServerService.STATE_STOPPED, "Forge 安装已取消");
            return;
        }
        stopSharing();
        if (!bound || serviceMessenger == null) {
            try {
                startService(new Intent(this, MinecraftServerService.class)
                        .setAction(MinecraftServerService.ACTION_STOP));
            } catch (Throwable t) {
                toast("停止失败：" + t.getMessage());
            }
            return;
        }
        try {
            serviceMessenger.send(Message.obtain(null, MinecraftServerService.MSG_STOP));
        } catch (RemoteException e) {
            toast("停止失败：" + e.getMessage());
        }
    }

    private void restartServer() {
        if (!MinecraftServerService.STATE_READY.equals(state)) {
            toast("服务端尚未就绪");
            return;
        }
        pendingRestart = true;
        applyState(MinecraftServerService.STATE_STOPPING, "正在保存并等待重启");
        stopServer();
        handler.postDelayed(() -> {
            if (pendingRestart) {
                pendingRestart = false;
                toast("服务端还未停止，已取消自动重启");
            }
        }, 30000L);
    }

    private void playGame() {
        if (!MinecraftServerService.STATE_READY.equals(state)) {
            toast("请等待服务端显示“运行中”");
            return;
        }
        String instance = ServerProfile.currentInstanceName(this);
        if (!instance.isEmpty() && !profile.version.isEmpty()
                && !instance.toLowerCase().contains(profile.version.toLowerCase())) {
            new AlertDialog.Builder(this)
                    .setTitle("实例版本可能不匹配")
                    .setMessage("当前实例：" + instance + "\n服务端版本：" + profile.version
                            + "\n\n仍要启动客户端并连接本地服务器吗？")
                    .setPositiveButton("继续启动", (d, w) -> launchGame())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        launchGame();
    }

    private void launchGame() {
        try {
            Intent intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("qcl_auto_launch", true)
                    .putExtra("qcl_server", "127.0.0.1:" + profile.port);
            startActivity(intent);
        } catch (Throwable t) {
            toast("启动游戏失败：" + t.getMessage());
        }
    }

    private void sendCommand() {
        String command = commandView.getText() == null
                ? "" : commandView.getText().toString().trim();
        if (command.isEmpty()) {
            return;
        }
        if (!bound || serviceMessenger == null) {
            toast("服务端进程尚未连接");
            return;
        }
        Message message = Message.obtain(null, MinecraftServerService.MSG_COMMAND);
        message.getData().putString("command", command);
        message.replyTo = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.arg1 == 0) {
                    toast("命令未发送");
                }
            }
        });
        try {
            serviceMessenger.send(message);
            commandView.setText("");
        } catch (RemoteException e) {
            toast("命令发送失败：" + e.getMessage());
        }
    }

    private void applyState(String nextState, String detail) {
        state = nextState == null ? MinecraftServerService.STATE_IDLE : nextState;
        stateDetail = detail == null ? "" : detail;
        statusView.setText(stateLabel(state) + (stateDetail.isEmpty() ? "" : " · " + stateDetail));

        boolean active = MinecraftServerService.STATE_PREPARING.equals(state)
                || MinecraftServerService.STATE_DOWNLOADING.equals(state)
                || MinecraftServerService.STATE_STARTING.equals(state)
                || MinecraftServerService.STATE_READY.equals(state)
                || MinecraftServerService.STATE_STOPPING.equals(state);
        boolean ready = MinecraftServerService.STATE_READY.equals(state);
        startButton.setEnabled(!active);
        stopButton.setEnabled(active && !MinecraftServerService.STATE_STOPPING.equals(state));
        restartButton.setEnabled(ready);
        playButton.setEnabled(ready);

        if (pendingRestart && (MinecraftServerService.STATE_STOPPED.equals(state)
                || MinecraftServerService.STATE_FAILED.equals(state)
                || MinecraftServerService.STATE_IDLE.equals(state))) {
            pendingRestart = false;
            handler.postDelayed(this::startServer, 1800L);
        }
    }

    private String stateLabel(String value) {
        switch (value) {
            case MinecraftServerService.STATE_PREPARING:
                return "准备中";
            case MinecraftServerService.STATE_DOWNLOADING:
                return "下载中";
            case MinecraftServerService.STATE_STARTING:
                return "启动中";
            case MinecraftServerService.STATE_READY:
                return "运行中";
            case MinecraftServerService.STATE_STOPPING:
                return "停止中";
            case MinecraftServerService.STATE_STOPPED:
                return "已停止";
            case MinecraftServerService.STATE_FAILED:
                return "启动失败";
            default:
                return "未启动";
        }
    }

    private void refreshLog() {
        checkForgeInstall();
        if (logReading || logFile == null || !logFile.isFile()) {
            return;
        }
        long length = logFile.length();
        long modified = logFile.lastModified();
        if (length == lastLogLength && modified == lastLogModified) {
            return;
        }
        lastLogLength = length;
        lastLogModified = modified;
        logReading = true;
        boolean atBottom = logScroll.getChildCount() == 0
                || logScroll.getScrollY() + logScroll.getHeight()
                >= logScroll.getChildAt(0).getHeight() - dp(24);
        new Thread(() -> {
            String text = readTail(logFile, LOG_TAIL_BYTES);
            handler.post(() -> {
                logReading = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (text != null && !text.isEmpty()) {
                    logView.setText(text);
                }
                if (atBottom) {
                    logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
                }
            });
        }, "qcl-server-log-tail").start();
    }

    private static String readTail(File file, int maxBytes) {
        try (FileInputStream in = new FileInputStream(file)) {
            long length = file.length();
            long skip = Math.max(0, length - maxBytes);
            long skipped = 0;
            while (skipped < skip) {
                long n = in.skip(skip - skipped);
                if (n <= 0) {
                    break;
                }
                skipped += n;
            }
            byte[] data = new byte[(int) Math.max(0, length - skipped)];
            int off = 0;
            while (off < data.length) {
                int read = in.read(data, off, data.length - off);
                if (read < 0) {
                    break;
                }
                off += read;
            }
            return new String(data, 0, off, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
