package com.qcl.launcher.launcher.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public final class ServerSetupDialog {

    public interface Callback {
        void onCreated(File profileFile, ServerProfile profile);
    }

    private ServerSetupDialog() {
    }

    public static void show(Activity activity, File serversRoot, String defaultName,
                            Callback callback) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        body.setPadding(pad, dp(activity, 4), pad, 0);

        EditText name = field(activity, "服务器名称", defaultName, InputType.TYPE_CLASS_TEXT);
        EditText version = field(activity, "Minecraft 版本，例如 1.21.1",
                ServerProfile.currentInstanceName(activity), InputType.TYPE_CLASS_TEXT);
        Spinner type = new Spinner(activity);
        String[] typeNames = {"原版 Vanilla", "Paper（插件）", "Purpur（插件）",
                "Fabric（模组）", "Forge（模组，实验）"};
        String[] typeValues = {"vanilla", "paper", "purpur", "fabric", "forge"};
        type.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, typeNames));
        type.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
        EditText port = field(activity, "端口", "25565", InputType.TYPE_CLASS_NUMBER);
        EditText players = field(activity, "最大玩家数", "8", InputType.TYPE_CLASS_NUMBER);
        EditText memory = field(activity, "内存 MB（建议 2048）", "2048",
                InputType.TYPE_CLASS_NUMBER);
        CheckBox online = new CheckBox(activity);
        online.setText("开启正版验证（本地离线账号请关闭）");
        online.setTextColor(Color.WHITE);
        online.setChecked(false);

        TextView eula = new TextView(activity);
        eula.setText("创建并启动服务器即表示同意 Minecraft EULA：\nhttps://aka.ms/MinecraftEULA");
        eula.setTextColor(0xFFBDBDBD);
        eula.setTextSize(11f);
        eula.setPadding(0, dp(activity, 12), 0, 0);

        body.addView(name);
        body.addView(type);
        body.addView(version);
        body.addView(port);
        body.addView(players);
        body.addView(memory);
        body.addView(online);
        body.addView(eula);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("创建 Minecraft 服务器")
                .setView(scroll)
                .setPositiveButton("同意并创建", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String versionText = text(version);
                    if (versionText.isEmpty()) {
                        toast(activity, "请填写 Minecraft 版本");
                        return;
                    }
                    int portValue = parseInt(text(port), 25565);
                    if (portValue < 1024 || portValue > 65535) {
                        toast(activity, "端口需要在 1024 到 65535 之间");
                        return;
                    }
                    String serverName = ServerProfile.sanitizeName(text(name));
                    File target = new File(serversRoot, serverName);
                    if (ServerProfile.profileFile(target).exists()) {
                        toast(activity, "同名服务器已存在");
                        return;
                    }
                    ServerProfile profile = ServerProfile.create(serverName, versionText);
                    int typeIndex = type.getSelectedItemPosition();
                    profile.type = typeValues[Math.max(0,
                            Math.min(typeIndex, typeValues.length - 1))];
                    profile.port = portValue;
                    profile.maxPlayers = Math.max(1, parseInt(text(players), 8));
                    profile.memoryMb = Math.max(512, Math.min(4096,
                            parseInt(text(memory), 2048)));
                    profile.onlineMode = online.isChecked();
                    try {
                        profile.save(target);
                        dialog.dismiss();
                        if (callback != null) {
                            callback.onCreated(ServerProfile.profileFile(target), profile);
                        }
                    } catch (Throwable t) {
                        toast(activity, "保存配置失败：" + t.getMessage());
                    }
                }));
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private static EditText field(Activity activity, String hint, String value,
                                  int inputType) {
        EditText edit = new EditText(activity);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setInputType(inputType);
        edit.setSingleLine(true);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(0x80FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(activity, 6);
        edit.setLayoutParams(lp);
        return edit;
    }

    private static String text(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
