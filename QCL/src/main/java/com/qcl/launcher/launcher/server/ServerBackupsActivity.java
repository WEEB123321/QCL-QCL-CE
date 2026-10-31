package com.qcl.launcher.launcher.server;

import android.app.AlertDialog;
import android.app.ProgressDialog;
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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.LocaleUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ServerBackupsActivity extends AppCompatActivity {

    public static final String EXTRA_PROFILE = "profile";

    private ServerProfile profile;
    private File serverDir;
    private LinearLayout listContainer;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppManifest.initializeManifest(getApplicationContext());
        super.onCreate(savedInstanceState);
        String path = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROFILE);
        if (path != null) {
            File profileFile = new File(path);
            serverDir = profileFile.getParentFile();
            profile = ServerProfile.load(serverDir);
        }
        if (profile == null) {
            Toast.makeText(this, "服务器配置不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(buildContent());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(0xEE111111);
        int pad = dp(18);
        root.setPadding(pad, dp(10), pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("世界备份 · " + profile.name);
        title.setTextColor(0xFFF4F0E8);
        title.setTextSize(21f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText("备份世界目录与核心配置，恢复前会自动再备份当前状态");
        subtitle.setTextColor(0xFFBDBDBD);
        subtitle.setTextSize(11f);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button create = new Button(this);
        create.setText("立即备份");
        create.setOnClickListener(v -> createBackup());
        header.addView(create);
        root.addView(header);

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

    private void refresh() {
        if (listContainer == null) {
            return;
        }
        listContainer.removeAllViews();
        List<File> backups = ServerBackupHelper.list(serverDir);
        if (backups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有备份。点右上角“立即备份”创建。");
            empty.setTextColor(0xFFBDBDBD);
            empty.setTextSize(14f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(40), dp(12), dp(40));
            listContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (File backup : backups) {
            addBackupRow(backup);
        }
    }

    private void addBackupRow(File backup) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x22FFFFFF);
        bg.setCornerRadius(dp(12));
        row.setBackground(bg);
        int pad = dp(12);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(backup.getName());
        name.setTextColor(0xFFF4F0E8);
        name.setTextSize(14f);
        name.setMaxLines(2);
        TextView detail = new TextView(this);
        detail.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date(backup.lastModified())) + "  ·  " + size(backup.length()));
        detail.setTextColor(0xFF9E9E9E);
        detail.setTextSize(11f);
        texts.addView(name);
        texts.addView(detail);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button restore = new Button(this);
        restore.setText("恢复");
        restore.setLayoutParams(new LinearLayout.LayoutParams(dp(70), dp(40)));
        restore.setOnClickListener(v -> restoreBackup(backup));
        row.addView(restore);

        Button delete = new Button(this);
        delete.setText("删除");
        delete.setLayoutParams(new LinearLayout.LayoutParams(dp(70), dp(40)));
        delete.setOnClickListener(v -> deleteBackup(backup));
        row.addView(delete);

        listContainer.addView(row);
    }

    private void createBackup() {
        final ProgressDialog dialog = progress("正在创建备份");
        new Thread(() -> {
            String error = null;
            File result = null;
            try {
                result = ServerBackupHelper.create(serverDir, "world",
                        stage -> runOnUiThread(() -> dialog.setMessage(stage)));
            } catch (Throwable t) {
                error = t.getMessage();
            }
            final String err = error;
            final File file = result;
            runOnUiThread(() -> {
                dismiss(dialog);
                refresh();
                if (err != null) {
                    toast("备份失败：" + err);
                } else {
                    toast("已创建 " + file.getName());
                }
            });
        }, "qcl-server-backup").start();
    }

    private void restoreBackup(File backup) {
        new AlertDialog.Builder(this)
                .setTitle("恢复世界备份")
                .setMessage(backup.getName()
                        + "\n\n当前世界会先自动备份，然后再被此备份覆盖。")
                .setPositiveButton("恢复", (d, w) -> doRestore(backup))
                .setNegativeButton("取消", null)
                .show();
    }

    private void doRestore(File backup) {
        final ProgressDialog dialog = progress("正在恢复备份");
        new Thread(() -> {
            String error = null;
            try {
                ServerBackupHelper.restore(serverDir, backup,
                        stage -> runOnUiThread(() -> dialog.setMessage(stage)));
                ServerBackupHelper.syncProfile(serverDir, profile);
            } catch (Throwable t) {
                error = t.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                dismiss(dialog);
                refresh();
                toast(err == null ? "恢复完成，请重新启动服务端" : "恢复失败：" + err);
            });
        }, "qcl-server-restore").start();
    }

    private void deleteBackup(File backup) {
        new AlertDialog.Builder(this)
                .setTitle("删除备份")
                .setMessage(backup.getName() + "\n\n删除后无法恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    if (ServerBackupHelper.delete(backup)) {
                        refresh();
                        toast("备份已删除");
                    } else {
                        toast("删除失败");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private ProgressDialog progress(String message) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(message);
        dialog.setCancelable(false);
        dialog.show();
        return dialog;
    }

    private void dismiss(ProgressDialog dialog) {
        try {
            if (dialog != null && dialog.isShowing() && !isFinishing()) {
                dialog.dismiss();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
