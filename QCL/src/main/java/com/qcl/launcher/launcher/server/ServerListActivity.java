package com.qcl.launcher.launcher.server;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.LocaleUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ServerListActivity extends AppCompatActivity {

    private LinearLayout listContainer;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppManifest.initializeManifest(getApplicationContext());
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServers();
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
        subtitle.setText("管理服务器实例，进入独立控制台");
        subtitle.setTextColor(0xFFBDBDBD);
        subtitle.setTextSize(12f);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button create = new Button(this);
        create.setText("新建服务器");
        create.setOnClickListener(v -> ServerSetupDialog.show(this,
                ServerProfile.serversRoot(), "server", (file, profile) -> openConsole(file)));
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

        row.setOnClickListener(v -> openConsole(profileFile));
        listContainer.addView(row);
    }

    private void openConsole(File profileFile) {
        Intent intent = new Intent(this, ServerConsoleActivity.class)
                .putExtra(ServerConsoleActivity.EXTRA_PROFILE, profileFile.getAbsolutePath());
        startActivity(intent);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
