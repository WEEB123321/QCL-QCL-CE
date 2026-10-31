package com.qcl.launcher.launcher.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.LocaleUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ServerListActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private boolean bound;
    private Messenger serviceMessenger;
    private String activeProfile = "";
    private String activeState = MinecraftServerService.STATE_IDLE;
    private String activeDetail = "";

    private final Messenger clientMessenger = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what != MinecraftServerService.MSG_STATE) {
                return;
            }
            String state = msg.getData().getString("state",
                    MinecraftServerService.STATE_IDLE);
            String detail = msg.getData().getString("detail", "");
            String profile = msg.getData().getString("profile", "");
            String nextProfile = profile == null ? "" : profile;
            String nextState = state == null ? MinecraftServerService.STATE_IDLE : state;
            boolean changed = !nextProfile.equals(activeProfile)
                    || !nextState.equals(activeState);
            activeProfile = nextProfile;
            activeState = nextState;
            activeDetail = detail == null ? "" : detail;
            if (changed) {
                refreshServers();
            }
        }
    });

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
            if (bound) {
                try {
                    unbindService(connection);
                } catch (Throwable ignored) {
                }
            }
            serviceMessenger = null;
            bound = false;
            activeProfile = "";
            activeState = MinecraftServerService.STATE_IDLE;
            activeDetail = "";
            refreshServers();
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppManifest.initializeManifest(getApplicationContext());
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        bindServerService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServers();
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            try {
                unbindService(connection);
            } catch (Throwable ignored) {
            }
            bound = false;
        }
        super.onDestroy();
    }

    private void bindServerService() {
        try {
            bindService(new Intent(this, MinecraftServerService.class),
                    connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable ignored) {
        }
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(0xEE111111);
        int pad = dp(18);
        root.setPadding(pad, dp(10), pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("手机开服");
        title.setTextColor(0xFFF4F0E8);
        title.setTextSize(22f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText("点服务器进控制台，也可以直接从列表启动");
        subtitle.setTextColor(0xFFBDBDBD);
        subtitle.setTextSize(12f);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button create = new Button(this);
        create.setText("新建服务器");
        create.setOnClickListener(v -> ServerSetupDialog.show(this,
                ServerProfile.serversRoot(), "server",
                (file, profile) -> openConsole(file, false)));
        header.addView(create);
        root.addView(header);

        TextView hint = new TextView(this);
        hint.setText("服务端保存在 QCL/servers/<名称>，每个服务器独立运行和保存世界。");
        hint.setTextColor(0xFF9E9E9E);
        hint.setTextSize(11f);
        hint.setPadding(0, dp(8), 0, dp(8));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void refreshServers() {
        if (listContainer == null) {
            return;
        }
        listContainer.removeAllViews();
        File root = ServerProfile.serversRoot();
        if (!root.isDirectory() && !root.mkdirs()) {
            addEmpty("无法创建服务器目录：" + root);
            return;
        }
        File[] dirs = root.listFiles(File::isDirectory);
        List<ServerProfile> profiles = new ArrayList<>();
        List<File> files = new ArrayList<>();
        if (dirs != null) {
            for (File dir : dirs) {
                ServerProfile profile = ServerProfile.load(dir);
                if (profile != null) {
                    profiles.add(profile);
                    files.add(ServerProfile.profileFile(dir));
                }
            }
        }
        if (profiles.isEmpty()) {
            addEmpty("还没有服务器。点右上角“新建服务器”开始。");
            return;
        }
        Integer[] order = new Integer[profiles.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, Comparator.comparing(
                (Integer i) -> profiles.get(i).name, String.CASE_INSENSITIVE_ORDER));
        for (int index : order) {
            addServerRow(profiles.get(index), files.get(index));
        }
    }

    private void addEmpty(String text) {
        TextView empty = new TextView(this);
        empty.setText(text);
        empty.setTextColor(0xFFBDBDBD);
        empty.setTextSize(14f);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(40), dp(12), dp(40));
        listContainer.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addServerRow(ServerProfile profile, File profileFile) {
        boolean isActive = profileFile.getAbsolutePath().equals(activeProfile);
        boolean serverActive = isActive && isActiveState(activeState);
        boolean otherActive = !isActive && !activeProfile.isEmpty()
                && isActiveState(activeState);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x22FFFFFF);
        bg.setCornerRadius(dp(12));
        row.setBackground(bg);
        int pad = dp(14);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        TextView name = new TextView(this);
        name.setText(profile.name + "  ·  " + profile.version);
        name.setTextColor(0xFFF4F0E8);
        name.setTextSize(16f);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(name);

        TextView detail = new TextView(this);
        detail.setText(profile.typeLabel() + "  ·  127.0.0.1:" + profile.port
                + "  ·  " + profile.maxPlayers + " 人  ·  Java "
                + profile.requiredJava);
        detail.setTextColor(0xFFBDBDBD);
        detail.setTextSize(12f);
        detail.setPadding(0, dp(4), 0, 0);
        row.addView(detail);

        if (isActive) {
            TextView status = new TextView(this);
            status.setText(stateLabel(activeState)
                    + (activeDetail.isEmpty() ? "" : " · " + activeDetail));
            status.setTextColor(stateColor(activeState));
            status.setTextSize(12f);
            status.setPadding(0, dp(4), 0, 0);
            row.addView(status);
        } else if (otherActive) {
            TextView status = new TextView(this);
            status.setText("另一个服务器正在运行：" + stateLabel(activeState));
            status.setTextColor(0xFFFFB74D);
            status.setTextSize(12f);
            status.setPadding(0, dp(4), 0, 0);
            row.addView(status);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(8), 0, 0);

        if (supportsContent(profile)) {
            Button content = new Button(this);
            content.setText(isPluginServer(profile) ? "下载插件" : "下载模组");
            content.setOnClickListener(v -> openContentDownload(profileFile));
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            contentParams.rightMargin = dp(6);
            actions.addView(content, contentParams);
        }

        Button start = new Button(this);
        start.setText(serverActive ? "查看控制台" : "启动");
        start.setOnClickListener(v -> {
            if (otherActive) {
                toast("另一个服务器正在运行，请先停止它");
                return;
            }
            openConsole(profileFile, !serverActive);
        });
        actions.addView(start);
        row.addView(actions);

        row.setOnClickListener(v -> openConsole(profileFile, false));
        listContainer.addView(row);
    }

    private void openConsole(File profileFile, boolean autoStart) {
        Intent intent = new Intent(this, ServerConsoleActivity.class)
                .putExtra(ServerConsoleActivity.EXTRA_PROFILE, profileFile.getAbsolutePath())
                .putExtra(ServerConsoleActivity.EXTRA_AUTO_START, autoStart);
        startActivity(intent);
    }

    private void openContentDownload(File profileFile) {
        try {
            startActivity(new Intent(this, ServerContentDownloadActivity.class)
                    .putExtra(ServerContentDownloadActivity.EXTRA_PROFILE,
                            profileFile.getAbsolutePath()));
        } catch (Throwable t) {
            toast("打开下载页失败：" + t.getMessage());
        }
    }

    private static boolean supportsContent(ServerProfile profile) {
        return isPluginServer(profile) || "fabric".equals(profile.type)
                || "forge".equals(profile.type);
    }

    private static boolean isPluginServer(ServerProfile profile) {
        return "paper".equals(profile.type) || "purpur".equals(profile.type);
    }

    private static boolean isActiveState(String state) {
        return MinecraftServerService.STATE_PREPARING.equals(state)
                || MinecraftServerService.STATE_DOWNLOADING.equals(state)
                || MinecraftServerService.STATE_STARTING.equals(state)
                || MinecraftServerService.STATE_READY.equals(state)
                || MinecraftServerService.STATE_STOPPING.equals(state);
    }

    private static String stateLabel(String state) {
        switch (state) {
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

    private static int stateColor(String state) {
        if (MinecraftServerService.STATE_READY.equals(state)) {
            return 0xFF81C784;
        }
        if (MinecraftServerService.STATE_FAILED.equals(state)) {
            return 0xFFE57373;
        }
        if (MinecraftServerService.STATE_STOPPED.equals(state)) {
            return 0xFFBDBDBD;
        }
        return 0xFFFFD54F;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
